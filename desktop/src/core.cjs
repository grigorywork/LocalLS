'use strict';
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { EventEmitter } = require('node:events');
const { pipeline } = require('node:stream/promises');
const { Transform } = require('node:stream');

function profile(input) {
  if (!input || typeof input !== 'object') throw new Error('Некорректный профиль');
  const host = String(input.host || '').trim();
  const user = String(input.user || '').trim();
  const port = Number(input.port);
  const folder = String(input.folder || '.');
  if (!/^[a-zA-Z0-9][a-zA-Z0-9.:-]{0,252}$/.test(host) || !Number.isInteger(port) || port < 1 || port > 65535 || !/^[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,63}$/.test(user) || folder.includes('\0') || folder.length > 4096) throw new Error('Проверьте адрес, порт и пользователя');
  const fingerprint = String(input.fingerprint || '');
  if (fingerprint && !/^SHA256:[A-Za-z0-9+/]{43}$/.test(fingerprint)) throw new Error('Некорректный отпечаток SSH');
  return { format: 'localls-server-v1', host, port, user, folder, fingerprint };
}
function endpoint(p) { return `${p.host.toLowerCase()}:${p.port}`; }
function fingerprint(key) { return 'SHA256:' + crypto.createHash('sha256').update(key).digest('base64').replace(/=+$/, ''); }
function name(value) {
  if (typeof value !== 'string' || !value || value === '.' || value === '..' || /[\\/\x00-\x1f]/.test(value) || value.length > 255) throw new Error('Недопустимое имя файла');
  return value;
}
function remotePath(value) {
  if (typeof value !== 'string' || value.includes('\0') || value.length > 4096) throw new Error('Недопустимый путь');
  return value;
}
function safeDownloadName(value) {
  name(value);
  if (/[<>:"|?*]/.test(value) || /[. ]$/.test(value) || /^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\.|$)/i.test(value)) throw new Error('Имя файла несовместимо с Windows');
  return value;
}
function call(sftp, method, ...args) { return new Promise((resolve, reject) => sftp[method](...args, (err, result) => err ? reject(err) : resolve(result))); }
function publicError(err) {
  const known={KEY_INVALID:'Ключ не распознан или неверна парольная фраза.',KEY_WEAK:'Используйте RSA не менее 3072 бит или Ed25519/ECDSA.',KEY_UNSUPPORTED:'Тип SSH-ключа не поддерживается.',KEY_GENERATION_FAILED:'Не удалось создать SSH-ключ.',KEY_REMOTE_UNSAFE:'Папка .ssh или authorized_keys имеет небезопасный тип или размер.',KEY_MISSING:'Создайте или импортируйте SSH-ключ в разделе безопасности.',KEY_BOOTSTRAP_REQUIRED:'Для установки публичного ключа введите действующий пароль сервера.',AGENT_TRUST_REQUIRED:'Сначала проверьте и подтвердите HTTPS-сертификат агента.',AGENT_TLS_FAILED:'Защищённое соединение агента не установлено: проверьте HTTPS, сертификат и порт.',AGENT_FAILED:'Агент не выполнил запрос.',NO_TOKEN:'Сохраните токен существующего агента в настройках сервера.',NOT_INSTALLED:'Установите официальный клиент Tailscale.',NOT_CONNECTED:'Сначала подключитесь к SSH-серверу.',FILES_ONLY:'Для передачи выберите файлы внутри папки.',DEST_EXISTS:'Файл с таким именем уже существует.',EXISTS:'Это имя уже занято.',NO_KEY:'Не удалось получить ключ SSH-сервера.'};
  if(known[err?.message])return known[err.message];
  if (err?.code === 'HOST_KEY_CHANGED') return 'Ключ сервера изменился. Подключение заблокировано.';
  if (err?.code === 'ABORT_ERR' || err?.name === 'AbortError') return 'Передача отменена';
  if (err?.level === 'client-authentication') return 'Сервер отклонил вход. Проверьте имя пользователя и пароль.';
  if (err?.code === 'ENOTFOUND') return 'Адрес сервера не найден';
  if (err?.code === 'ECONNREFUSED') return 'SSH-порт недоступен';
  if (err?.code === 'ETIMEDOUT') return 'Сервер не ответил вовремя';
  if (err?.code === 'ENOENT' || err?.code === 2) return 'Файл или папка не найдены';
  if (err?.code === 'EACCES' || err?.code === 3) return 'Нет доступа к файлу или папке';
  return 'Операция не выполнена. Проверьте соединение и права доступа.';
}
class Server extends EventEmitter {
  constructor(Client) { super(); this.Client = Client || require('ssh2').Client; this.client = null; this.sftp = null; this.location = '.'; }
  probe(p) {
    p = profile(p);
    return new Promise((resolve, reject) => {
      const c = new this.Client(); let found = false;
      c.on('error', err => { if (!found) reject(err); });
      c.on('close', () => { if (!found) reject(new Error('NO_KEY')); });
      c.connect({ host: p.host, port: p.port, username: p.user, readyTimeout: 12000,
        authHandler: ['none'], hostVerifier: key => { found = true; resolve(fingerprint(key)); return false; } });
    });
  }
  async connect(p, credential, trusted) {
    p = profile(p); this.disconnect();
    const auth=typeof credential==='string'?{method:'password',password:credential}:credential;
    if(!auth||!['password','key'].includes(auth.method))throw new Error('AUTH_INVALID');
    if(auth.method==='key')require('./security.cjs').inspectIdentity(auth.privateKey,auth.passphrase||'');
    if (!trusted || !/^SHA256:[A-Za-z0-9+/]{43}$/.test(trusted)) throw Object.assign(new Error('UNTRUSTED'), { code: 'HOST_KEY_CHANGED' });
    const c = new this.Client(); this.client = c;
    await new Promise((resolve, reject) => {
      let mismatch = false;
      c.on('error', err => reject(mismatch ? Object.assign(new Error('CHANGED'), { code: 'HOST_KEY_CHANGED' }) : err));
      c.on('close', () => { if (this.client === c) { this.sftp = null; this.client = null; this.emit('closed'); } reject(new Error('CLOSED')); });
      c.once('ready', resolve);
      c.connect({ host: p.host, port: p.port, username: p.user, ...(auth.method==='key'?{privateKey:auth.privateKey,passphrase:auth.passphrase||'',authHandler:['publickey']}:{password:auth.password||'',authHandler:['password']}),
        readyTimeout: 15000, keepaliveInterval: 15000, keepaliveCountMax: 3,
        hostVerifier: key => { mismatch = fingerprint(key) !== trusted; return !mismatch; } });
    });
    this.sftp = await new Promise((resolve, reject) => c.sftp((err, s) => err ? reject(err) : resolve(s)));
    this.location = await call(this.sftp, 'realpath', p.folder);
    return this.list(this.location);
  }
  disconnect() { if (this.client) this.client.end(); this.client = null; this.sftp = null; }
  requireSftp() { if (!this.sftp) throw new Error('NOT_CONNECTED'); return this.sftp; }
  async list(folder) {
    const s = this.requireSftp(); const resolved = await call(s, 'realpath', remotePath(folder));
    const entries = await call(s, 'readdir', resolved);
    return { path: resolved, entries: entries.filter(e => !['.', '..'].includes(e.filename)).map(e => ({ name: e.filename, path: path.posix.join(resolved, e.filename), directory: e.attrs.isDirectory(), link: e.attrs.isSymbolicLink(), size: e.attrs.size, modified: e.attrs.mtime * 1000 })).sort((a,b) => Number(b.directory)-Number(a.directory) || a.name.localeCompare(b.name)) };
  }
  async stats() {
    if (!this.client) throw new Error('NOT_CONNECTED');
    return new Promise((resolve, reject) => this.client.exec('LC_ALL=C uptime; LC_ALL=C df -Pk .', { pty: false }, (err, stream) => {
      if (err) return reject(err); let data = ''; const timer = setTimeout(() => { stream.close(); reject(new Error('TIMEOUT')); }, 10000);
      stream.on('data', chunk => { if (data.length < 8000) data += chunk.toString().slice(0, 8000-data.length); });
      stream.on('close', () => { clearTimeout(timer); const lines = data.trim().split('\n'); const disk = lines.find(l => /\d+%/.test(l)); resolve({ uptime: lines[0]?.slice(0,160) || '—', disk: disk?.trim().split(/\s+/).slice(1,5).join(' · ') || '—' }); });
      stream.on('error', e => { clearTimeout(timer); reject(e); });
    }));
  }
}
class Transfers extends EventEmitter {
  constructor(server) { super(); this.server = server; this.items = []; this.running = false; this.current = null; }
  add(specs) {
    if (!Array.isArray(specs) || specs.length > 1000) throw new Error('BAD_QUEUE');
    const jobs = specs.map(spec => ({ ...spec, id: crypto.randomUUID(), state: 'queued', bytes: 0, speed: 0, created: Date.now() }));
    this.items.push(...jobs); this.publish(); this.drain(); return jobs.map(j => j.id);
  }
  snapshot() { return this.items.slice(-200).map(({ controller, ...j }) => ({ ...j })); }
  publish() { this.emit('update', this.snapshot()); }
  cancel(id) { const j = this.items.find(j => j.id === id); if (!j) return; if (j.state === 'queued') j.state = 'cancelled'; else j.controller?.abort(); this.publish(); }
  async drain() {
    if (this.running) return; this.running = true; this.emit('active', true);
    try { for (;;) { const job = this.items.find(j => j.state === 'queued'); if (!job) break; await this.run(job); } }
    finally { this.running = false; this.current = null; this.emit('active', false); this.publish(); }
  }
  async run(j) {
    j.controller = new AbortController(); this.current = j; j.state = 'running'; j.started = Date.now(); this.publish(); let temp, localHandle, s;
    let lastBytes = 0, lastAt = Date.now();
    const tick = setInterval(() => { const now = Date.now(); j.speed = (j.bytes-lastBytes)*1000/Math.max(1,now-lastAt); lastBytes=j.bytes; lastAt=now; this.publish(); }, 500);
    try {
      s = this.server.requireSftp();
      const meter = new Transform({ transform(chunk, enc, done) { j.bytes += chunk.length; done(null,chunk); } });
      if (j.direction === 'download') {
        safeDownloadName(path.posix.basename(j.remote));
        const stat = await call(s, 'stat', remotePath(j.remote)); if (!stat.isFile()) throw new Error('FILES_ONLY'); j.total = stat.size;
        temp = path.join(path.dirname(j.local), '.localls-'+crypto.randomUUID()+'.part');
        localHandle = await fs.promises.open(temp, 'wx', 0o600);
        await pipeline(s.createReadStream(j.remote), meter, localHandle.createWriteStream(), { signal: j.controller.signal });
        localHandle = null;
        // Hard-link gives an atomic commit which never overwrites an existing destination.
        if (j.controller.signal.aborted) throw Object.assign(new Error('CANCELLED'),{name:'AbortError'});
        try { await fs.promises.link(temp, j.local); } catch(e) { if(!['EPERM','ENOTSUP','EOPNOTSUPP'].includes(e.code))throw e; await fs.promises.copyFile(temp,j.local,fs.constants.COPYFILE_EXCL); }
        await fs.promises.unlink(temp); temp=null;
      } else if (j.direction === 'upload') {
        const stat = await fs.promises.stat(j.local); if (!stat.isFile()) throw new Error('FILES_ONLY'); j.total = stat.size;
        temp = path.posix.join(path.posix.dirname(remotePath(j.remote)), '.localls-'+crypto.randomUUID()+'.part');
        await pipeline(fs.createReadStream(j.local), meter, s.createWriteStream(temp, { flags:'wx', mode:0o600 }), { signal:j.controller.signal });
        // The final path is selected and confirmed in main before enqueueing.
        if (j.overwrite) await call(s, 'ext_openssh_rename', temp, j.remote);
        else { try { await call(s,'lstat',j.remote); throw new Error('DEST_EXISTS'); } catch(e) { if(e.code !== 2) throw e; } await call(s,'rename',temp,j.remote); }
        temp=null;
      } else throw new Error('BAD_DIRECTION');
      j.state='completed'; j.finished=Date.now();
    } catch(err) { j.state=j.controller.signal.aborted?'cancelled':'failed'; j.error=publicError(err); j.finished=Date.now(); }
    finally { clearInterval(tick); j.speed=0; await localHandle?.close().catch(()=>{}); if(temp) { if(j.direction==='download') await fs.promises.unlink(temp).catch(()=>{}); else if(s) await call(s,'unlink',temp).catch(()=>{}); } delete j.controller; this.publish(); }
  }
}
module.exports={profile,endpoint,fingerprint,name,remotePath,safeDownloadName,call,publicError,Server,Transfers};
