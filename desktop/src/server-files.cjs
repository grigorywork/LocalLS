'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {utils} = require('ssh2');
const {safeDownloadName} = require('./core.cjs');

// SFTP exposes only the explicitly chosen directory, never Windows drive paths,
// junctions, symbolic links, alternate data streams or hard-linked outside files.
class SharedFiles {
  constructor(root) {
    const original = fs.lstatSync(root);
    if (!original.isDirectory() || original.isSymbolicLink()) throw new Error('SHARE_UNSAFE');
    this.root = fs.realpathSync(root);
    this.identity = fs.statSync(this.root);
  }
  resolve(remote, missing = false) {
    if (typeof remote !== 'string' || remote.length > 4096 || /[\\\x00-\x1f]/.test(remote)) throw new Error('SHARE_DENIED');
    const current = fs.lstatSync(this.root);
    if (current.isSymbolicLink() || !current.isDirectory() || current.dev !== this.identity.dev || current.ino !== this.identity.ino) throw new Error('SHARE_UNSAFE');
    let depth = 0;
    for (const part of remote.split('/')) {
      if (!part || part === '.') continue;
      if (part === '..') { if (--depth < 0) throw new Error('SHARE_DENIED'); }
      else { safeDownloadName(part); depth++; }
    }
    const canonical = path.posix.resolve('/', remote || '.');
    const parts = canonical.split('/').filter(Boolean);
    let full = this.root;
    for (let i = 0; i < parts.length; i++) {
      full = path.join(full, parts[i]);
      let st;
      try { st = fs.lstatSync(full); }
      catch (error) { if (error.code === 'ENOENT' && missing && i === parts.length - 1) return full; throw error; }
      if (st.isSymbolicLink() || (st.isFile() && st.nlink > 1)) throw new Error('SHARE_DENIED');
      if (i < parts.length - 1 && !st.isDirectory()) throw new Error('SHARE_DENIED');
      const relative = path.relative(this.root, fs.realpathSync(full));
      if (relative === '..' || relative.startsWith('..' + path.sep) || path.isAbsolute(relative)) throw new Error('SHARE_DENIED');
    }
    return full;
  }
  mutable(remote, missing = false) {
    const full = this.resolve(remote, missing);
    if (full === this.root) throw new Error('SHARE_DENIED');
    return full;
  }
}

function attributes(st) {
  if (!Number.isSafeInteger(st.size) || st.size < 0) throw new Error('BAD_SIZE');
  const seconds = value => Math.max(0, Math.min(0xffffffff, Math.floor(value / 1000)));
  return {mode: st.mode, uid: st.uid || 0, gid: st.gid || 0, size: st.size, atime: seconds(st.atimeMs), mtime: seconds(st.mtimeMs)};
}
function position(n) { if (!Number.isSafeInteger(n) || n < 0) throw new Error('BAD_OFFSET'); return n; }

function serveFiles(s, share, traffic = () => {}, completed = () => {}) {
  const handles = new Map();
  let closed = false, requests = 0;
  const send = fn => { if (!closed) try { fn(); } catch {} };
  const observe = (fn, ...args) => { try { fn(...args); } catch { /* Telemetry never changes a completed filesystem operation. */ } };
  const status = (id, error) => send(() => s.status(id, !error ? 0 : error.code === 'ENOENT' ? 2 : ['EACCES','EPERM'].includes(error.code) || /^SHARE_/.test(error.message) ? 3 : 4));
  const bind = (name, fn) => s.on(name, (id, ...args) => {
    if (++requests > 128) { requests--; return status(id, new Error('LIMIT')); }
    Promise.resolve().then(() => { if (closed) throw new Error('CLOSED'); return fn(id, ...args); })
      .catch(error => status(id, error)).finally(() => requests--);
  });
  const create = data => {
    if (closed || handles.size >= 128) throw new Error('HANDLE_LIMIT');
    const token = crypto.randomBytes(16); handles.set(token.toString('hex'), {...data, queue: Promise.resolve(), closing: false}); return token;
  };
  const get = token => {
    if (!Buffer.isBuffer(token) || token.length !== 16) throw new Error('BAD_HANDLE');
    const h = handles.get(token.toString('hex')); if (!h || h.closing) throw new Error('BAD_HANDLE'); return h;
  };
  const serial = (h, operation) => {
    const next = h.queue.then(() => { if (closed) throw new Error('CLOSED'); if (h.path) share.resolve(h.remote); return operation(); });
    h.queue = next.catch(() => {}); return next;
  };
  const stat = (id, remote) => { const attrs = attributes(fs.statSync(share.resolve(remote))); send(() => s.attrs(id, attrs)); };
  bind('STAT', stat); bind('LSTAT', stat);
  bind('REALPATH', (id, remote) => { const full = share.resolve(remote); const value = '/' + path.relative(share.root, full).split(path.sep).join('/'); const attrs = attributes(fs.statSync(full)); send(() => s.name(id, [{filename: value, longname: value, attrs}])); });
  bind('OPENDIR', (id, remote) => {
    const folder = share.resolve(remote);
    const items = fs.readdirSync(folder, {withFileTypes: true}); if (items.length > 10000) throw new Error('DIRECTORY_LIMIT');
    const entries = [];
    for (const entry of items) {
      try { const item = share.resolve(path.posix.join(remote, entry.name)); entries.push({filename: entry.name, longname: entry.name, attrs: attributes(fs.statSync(item))}); }
      catch (error) { if (error.code !== 'ENOENT' && !/^SHARE_/.test(error.message)) throw error; }
    }
    const token = create({entries, index: 0}); send(() => s.handle(id, token));
  });
  bind('READDIR', (id, token) => {
    const h = get(token); if (!h.entries) throw new Error('BAD_HANDLE');
    if (h.index >= h.entries.length) return send(() => s.status(id, 1));
    const batch = h.entries.slice(h.index, h.index + 100); h.index += batch.length; send(() => s.name(id, batch));
  });
  bind('OPEN', (id, remote, flags, attrs) => {
    const mode = utils.sftp.flagsToString(flags); if (!mode) throw new Error('BAD_FLAGS');
    const full = share.mutable(remote, /[wax]/.test(mode));
    if (handles.size >= 128) throw new Error('HANDLE_LIMIT');
    const fd = fs.openSync(full, mode, (attrs.mode === undefined ? 0o600 : attrs.mode) & 0o777);
    try { const token = create({fd, path: full, remote, read: 0, written: 0}); send(() => s.handle(id, token)); }
    catch (error) { fs.closeSync(fd); throw error; }
  });
  bind('FSTAT', (id, token) => { const h = get(token); return serial(h, () => { const attrs = attributes(fs.fstatSync(h.fd)); send(() => s.attrs(id, attrs)); }); });
  bind('READ', (id, token, offset, length) => {
    const h = get(token); position(offset); if (!Number.isInteger(length) || length < 1 || length > 262144) throw new Error('BAD_LENGTH');
    return serial(h, () => new Promise((resolve, reject) => {
      const buffer = Buffer.alloc(Math.min(length, 65536));
      fs.read(h.fd, buffer, 0, buffer.length, offset, (error, count) => {
        if (error) return reject(error); h.read += count; if (count) observe(traffic, 'sent', count);
        send(() => count ? s.data(id, buffer.subarray(0, count)) : s.status(id, 1)); resolve();
      });
    }));
  });
  bind('WRITE', (id, token, offset, data) => {
    const h = get(token); position(offset); if (!Buffer.isBuffer(data) || data.length > 262144 || !Number.isSafeInteger(offset + data.length)) throw new Error('BAD_LENGTH');
    return serial(h, async () => {
      let written = 0;
      while (written < data.length) {
        const n = await new Promise((resolve, reject) => fs.write(h.fd, data, written, data.length - written, offset + written, (e, n) => e ? reject(e) : resolve(n)));
        if (!n) throw new Error('SHORT_WRITE'); written += n; h.written += n; observe(traffic, 'received', n);
      }
      status(id);
    });
  });
  const change = (full, attrs, fd) => {
    if (attrs.size !== undefined) { position(attrs.size); fd === undefined ? fs.truncateSync(full, attrs.size) : fs.ftruncateSync(fd, attrs.size); }
    if (attrs.mode !== undefined) { if (!Number.isInteger(attrs.mode)) throw new Error('BAD_MODE'); fd === undefined ? fs.chmodSync(full, attrs.mode & 0o777) : fs.fchmodSync(fd, attrs.mode & 0o777); }
    if (attrs.atime !== undefined || attrs.mtime !== undefined) {
      for (const n of [attrs.atime, attrs.mtime]) if (!Number.isInteger(n) || n < 0 || n > 0xffffffff) throw new Error('BAD_TIME');
      fd === undefined ? fs.utimesSync(full, attrs.atime, attrs.mtime) : fs.futimesSync(fd, attrs.atime, attrs.mtime);
    }
  };
  bind('SETSTAT', (id, remote, attrs) => { change(share.mutable(remote), attrs); status(id); });
  bind('FSETSTAT', (id, token, attrs) => { const h = get(token); return serial(h, () => { change(h.path, attrs, h.fd); status(id); }); });
  bind('CLOSE', async (id, token) => {
    const h = get(token); h.closing = true;
    try { await h.queue; if (h.fd !== undefined) { const fd = h.fd; delete h.fd; fs.closeSync(fd); } }
    finally { handles.delete(token.toString('hex')); }
    if (h.written || h.read) observe(completed, {name: path.posix.basename(h.remote), remote: path.posix.resolve('/', h.remote), received: h.written, sent: h.read}); status(id);
  });
  bind('MKDIR', (id, remote, attrs) => { fs.mkdirSync(share.mutable(remote, true), {mode: (attrs.mode || 0o700) & 0o777}); status(id); });
  bind('REMOVE', (id, remote) => { fs.unlinkSync(share.mutable(remote)); status(id); });
  bind('RMDIR', (id, remote) => { fs.rmdirSync(share.mutable(remote)); status(id); });
  const rename = (id, source, target, overwrite) => {
    const from = share.mutable(source), to = share.mutable(target, true);
    if (fs.existsSync(to)) { if (!overwrite || !fs.statSync(from).isFile() || !fs.statSync(to).isFile()) throw new Error('DEST_EXISTS'); }
    fs.renameSync(from, to); observe(completed, {renamedFrom: path.posix.resolve('/', source), remote: path.posix.resolve('/', target), name: path.posix.basename(target)}); status(id);
  };
  bind('RENAME', (id, from, to) => rename(id, from, to, false));
  bind('EXTENDED', (id, extension, payload) => {
    let offset = 0;
    const field = () => {
      if (!Buffer.isBuffer(payload) || offset + 4 > payload.length) throw new Error('BAD_EXTENSION');
      const n = payload.readUInt32BE(offset); offset += 4;
      if (n > 16384 || offset + n > payload.length) throw new Error('BAD_EXTENSION');
      const value = payload.subarray(offset, offset + n); offset += n; return value;
    };
    if (extension === 'posix-rename@openssh.com') { const from = field().toString('utf8'), to = field().toString('utf8'); if (offset !== payload.length) throw new Error('BAD_EXTENSION'); return rename(id, from, to, true); }
    if (extension === 'fsync@openssh.com') { const h = get(field()); if (offset !== payload.length) throw new Error('BAD_EXTENSION'); return serial(h, () => { fs.fsyncSync(h.fd); status(id); }); }
    send(() => s.status(id, 8));
  });
  for (const action of ['SYMLINK','READLINK']) bind(action, id => send(() => s.status(id, 8)));
  const cleanup = () => {
    if (closed) return; closed = true;
    for (const h of handles.values()) h.queue.finally(() => { if (h.fd !== undefined) { const fd = h.fd; delete h.fd; try { fs.closeSync(fd); } catch {} } }).catch(() => {});
    handles.clear();
  };
  s.on('error', cleanup); s.on('close', cleanup); s.on('end', cleanup);
  return cleanup;
}
module.exports = {SharedFiles, serveFiles};
