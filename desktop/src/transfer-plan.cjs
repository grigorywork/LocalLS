'use strict';
const fs = require('node:fs'), path = require('node:path');
const {call, name, safeDownloadName, remotePath} = require('./core.cjs');
const LIMIT = 1000;
function identity(file) {
  const st = fs.lstatSync(file, {bigint: true}); if (st.isSymbolicLink()) throw new Error('COPY_LINK');
  return {path: fs.realpathSync(file), dev: String(st.dev), ino: String(st.ino)};
}
function checkIdentity(record, directory = false) {
  const st = fs.lstatSync(record.path, {bigint: true});
  if (st.isSymbolicLink() || directory && !st.isDirectory() || String(st.dev) !== record.dev || String(st.ino) !== record.ino) throw new Error('COPY_FOLDER_CHANGED');
  return st;
}
function sourceSnapshot(file) {
  const st = fs.lstatSync(file, {bigint: true}); if (st.isSymbolicLink() || !st.isFile()) throw new Error('COPY_LINK');
  if (st.size > BigInt(Number.MAX_SAFE_INTEGER)) throw new Error('COPY_LIMIT');
  return {...identity(file), size: Number(st.size), mtime: String(st.mtimeNs)};
}
function verifySource(job) {
  if (!job.sourceSnapshot) return;
  const st = checkIdentity(job.sourceSnapshot);
  if (!st.isFile() || Number(st.size) !== job.sourceSnapshot.size || String(st.mtimeNs) !== job.sourceSnapshot.mtime) throw new Error('SOURCE_CHANGED');
  if (job.sourceRoot) {
    checkIdentity(job.sourceRoot, true);
    const relative = path.relative(job.sourceRoot.path, fs.realpathSync(job.local));
    if (relative === '..' || relative.startsWith('..' + path.sep) || path.isAbsolute(relative)) throw new Error('COPY_FOLDER_CHANGED');
  }
}
function verifyDestination(job) {
  if (!job.destinationParent) return;
  checkIdentity(job.destinationParent, true); checkIdentity(job.destinationRoot, true);
  if (job.directory) { if (job.local !== job.destinationRoot.path || path.dirname(job.local) !== job.destinationParent.path) throw new Error('COPY_FOLDER_CHANGED'); return; }
  const relative = path.relative(job.destinationRoot.path, fs.realpathSync(job.destinationParent.path));
  if (relative === '..' || relative.startsWith('..' + path.sep) || path.isAbsolute(relative) || path.dirname(job.local) !== job.destinationParent.path) throw new Error('COPY_FOLDER_CHANGED');
}
async function timed(sftp, method, ...args) {
  let timer;
  try { return await Promise.race([call(sftp, method, ...args), new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('SCAN_TIMEOUT')), 10000); })]); }
  finally { clearTimeout(timer); }
}
function budget() {
  let count = 0; const until = performance.now() + 30000;
  return depth => { if (depth > 48 || ++count > LIMIT || performance.now() > until) throw new Error('COPY_LIMIT'); };
}
function duplicate(base, n) { const ext = path.extname(base); return base.slice(0, base.length - ext.length) + ' (' + n + ')' + ext; }
async function remoteUnique(sftp, folder, base, reserved) {
  for (let n = 0; n < 10000; n++) {
    const value = path.posix.join(folder, n ? duplicate(base, n) : base); if (reserved.has(value)) continue;
    try { await timed(sftp, 'lstat', value); } catch (error) { if (error.code === 2) { reserved.add(value); return value; } throw error; }
  }
  throw new Error('COPY_LIMIT');
}
function localUnique(folder, base, reserved) {
  for (let n = 0; n < 10000; n++) { const value = path.join(folder, n ? duplicate(base, n) : base); if (!fs.existsSync(value) && !reserved.has(value)) { reserved.add(value); return value; } }
  throw new Error('COPY_LIMIT');
}
async function planUpload({files, folder, sftp, binding, allowed, confirmOverwrite}) {
  const step = budget(), trees = [], selected = [...new Map([...new Set(files)].map(p => { if (typeof p !== 'string' || p.length > 4096 || p.includes('\0')) throw new Error('BAD_FILES'); const st = fs.lstatSync(p); if (st.isSymbolicLink()) throw new Error('COPY_LINK'); const item = {path: allowed(p), directory: st.isDirectory()}; return [item.path, item]; })).values()];
  const roots = selected.filter(a => !selected.some(b => b !== a && b.directory && path.relative(b.path, a.path) !== '..' && !path.relative(b.path, a.path).startsWith('..' + path.sep) && !path.isAbsolute(path.relative(b.path, a.path))));
  async function scan(file, relative, depth, tree) {
    step(depth); const st = await fs.promises.lstat(file); if (st.isSymbolicLink()) throw new Error('COPY_LINK');
    if (st.isDirectory()) {
      tree.dirs.push({file, relative}); const children = await fs.promises.readdir(file, {withFileTypes: true});
      for (const child of children) { name(child.name); await scan(path.join(file, child.name), path.posix.join(relative, child.name), depth + 1, tree); }
    } else if (st.isFile()) tree.files.push({file, relative, snapshot: sourceSnapshot(file)});
    else throw new Error('COPY_LINK');
  }
  for (const source of roots) { const tree = {source, dirs: [], files: []}; await scan(source.path, '', 0, tree); trees.push(tree); }
  const canonical = await timed(sftp, 'realpath', remotePath(folder)), reserved = new Set(), jobs = [], created = [];
  const parent = await timed(sftp, 'lstat', canonical); if (!parent.isDirectory() || parent.isSymbolicLink()) throw new Error('COPY_FOLDER_CHANGED');
  try {
    for (const tree of trees) {
      const base = name(path.basename(tree.source.path));
      if (!tree.source.directory) {
        const remote = path.posix.join(canonical, base); let overwrite = false;
        try { const existing = await timed(sftp, 'lstat', remote); if (!existing.isFile() || existing.isSymbolicLink()) throw new Error('DEST_EXISTS'); overwrite = true; } catch (error) { if (error.code !== 2) throw error; }
        if (overwrite && !await confirmOverwrite(base)) continue;
        jobs.push({direction:'upload', local:tree.source.path, remote, label:base, overwrite, binding, sourceSnapshot:tree.files[0].snapshot, sourceRoot:identity(path.dirname(tree.source.path))});
        continue;
      }
      const destination = await remoteUnique(sftp, canonical, base, reserved), sourceRoot = identity(tree.source.path);
      for (const directory of tree.dirs) { const remote = path.posix.join(destination, directory.relative); await timed(sftp, 'mkdir', remote); created.push(remote); }
      for (const entry of tree.files) jobs.push({direction:'upload', local:entry.file, remote:path.posix.join(destination, entry.relative), label:path.posix.join(path.posix.basename(destination),entry.relative), overwrite:false, binding, sourceSnapshot:entry.snapshot, sourceRoot});
      if (!tree.files.length) jobs.push({direction:'upload', remote:destination, label:path.posix.basename(destination), directory:true, binding});
    }
    return jobs;
  } catch (error) { for (const dir of created.reverse()) try { await timed(sftp, 'rmdir', dir); } catch {} throw error; }
}
async function planDownload({files, folder, sftp, binding}) {
  const step = budget(), selected = [...new Set(files)].map(p => path.posix.resolve('/', remotePath(p))), roots = selected.filter(a => !selected.some(b => a !== b && a.startsWith(b.replace(/\/$/,'') + '/'))), trees = [];
  async function scan(remote, relative, depth, tree) {
    step(depth); const st = await timed(sftp, 'lstat', remote); if (st.isSymbolicLink()) throw new Error('COPY_LINK');
    if (st.isDirectory()) {
      tree.dirs.push(relative); const entries = await timed(sftp, 'readdir', remote);
      if (entries.length > LIMIT) throw new Error('COPY_LIMIT');
      for (const entry of entries) { if (entry.filename === '.' || entry.filename === '..') continue; safeDownloadName(entry.filename); await scan(path.posix.join(remote, entry.filename), path.posix.join(relative, entry.filename), depth + 1, tree); }
    } else if (st.isFile()) {
      if (!Number.isSafeInteger(st.size) || st.size < 0) throw new Error('COPY_LIMIT');
      tree.files.push({remote, relative, snapshot:{size:st.size, mtime:st.mtime}});
    } else throw new Error('COPY_LINK');
  }
  for (const remote of roots) { const st = await timed(sftp, 'lstat', remote), tree = {remote, directory:st.isDirectory(), dirs:[], files:[]}; safeDownloadName(path.posix.basename(remote)); await scan(remote, '', 0, tree); trees.push(tree); }
  const jobs = [], created = [], reserved = new Set(), baseRoot = identity(folder);
  try {
    for (const tree of trees) {
      const base = safeDownloadName(path.posix.basename(tree.remote)), destination = localUnique(baseRoot.path, base, reserved);
      if (!tree.directory) { jobs.push({direction:'download',remote:tree.remote,local:destination,label:path.basename(destination),binding,remoteSnapshot:tree.files[0].snapshot,destinationRoot:baseRoot,destinationParent:baseRoot}); continue; }
      for (const relative of tree.dirs) { const target = path.join(destination, ...relative.split('/').filter(Boolean)); await fs.promises.mkdir(target); created.push(target); }
      const destinationRoot = identity(destination);
      for (const entry of tree.files) {
        const local = path.join(destination, ...entry.relative.split('/')); jobs.push({direction:'download',remote:entry.remote,local,label:path.join(path.basename(destination),entry.relative),binding,remoteSnapshot:entry.snapshot,destinationRoot,destinationParent:identity(path.dirname(local))});
      }
      if (!tree.files.length) jobs.push({direction:'download',local:destination,label:path.basename(destination),directory:true,binding,destinationRoot,destinationParent:identity(path.dirname(destination))});
    }
    return jobs;
  } catch (error) { for (const dir of created.reverse()) try { await fs.promises.rmdir(dir); } catch {} throw error; }
}
module.exports = {planUpload, planDownload, identity, checkIdentity, sourceSnapshot, verifySource, verifyDestination};
