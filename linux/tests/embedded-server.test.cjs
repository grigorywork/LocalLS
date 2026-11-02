'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), os = require('node:os'), path = require('node:path'), crypto = require('node:crypto');
const {utils} = require('ssh2');
const {EmbeddedServer, publicIdentity} = require('../src/embedded-server.cjs');
const {Server, Transfers, call} = require('../src/core.cjs');
const pairing = require('../src/pairing.cjs');
const {identity: makeIdentity} = require('./ed25519-fixture.cjs');
const identity = () => makeIdentity();
test('Synthetic Ed25519 identities preserve a leading zero public byte and signed proof', () => {
  const seed = crypto.createHash('sha256').update('LocalLS synthetic Ed25519 boundary 387').digest();
  const key = makeIdentity(seed), privateKey = utils.parseKey(key.privateKey), publicKey = publicIdentity(key.publicKey).key;
  assert.equal(publicKey.getPublicSSH().at(-32), 0);
  const challenge = Buffer.from('LocalLS synthetic identity proof');
  assert.equal(publicKey.verify(challenge, privateKey.sign(challenge)), true);
});
async function setup(devices = []) {
  const folder = fs.mkdtempSync(path.join(os.tmpdir(), 'localls-win-server-')), root = path.join(folder, 'shared'); fs.mkdirSync(root);
  const host = utils.generateKeyPairSync('rsa', {bits: 3072}).private;
  let saved = devices;
  const server = new EmbeddedServer({saveDevices: value => { saved = JSON.parse(JSON.stringify(value)); }});
  const start = port => server.start({host: '127.0.0.1', port, user: 'localls', root, privateKey: host, devices: saved});
  await start(0);
  return {folder, root, server, start, saved: () => saved, offer: () => server.invite('127.0.0.1'), close: async () => { await server.stop(); fs.rmSync(folder, {recursive: true, force: true}); }};
}
const wait = async fn => { const end = Date.now() + 3000; while (!fn() && Date.now() < end) await new Promise(r => setTimeout(r, 10)); assert.ok(fn()); };
test('Windows server QR requires signed key proof and local approval; restart keeps the same host and device keys', async () => {
  const f = await setup(), key = identity();
  try {
    const code = f.offer(); assert.deepEqual(pairing.decode(JSON.stringify(code)), code);
    assert.equal(await pairing.loginAttempt(code, key), false); assert.equal(f.server.snapshot().pending, null);
    assert.equal(await pairing.loginAttempt(code, key, true), false);
    const pending = f.server.snapshot().pending; assert.ok(pending); assert.equal(pending.code, pairing.syncCode(code, key.publicKey));
    assert.equal(await pairing.loginAttempt(code, key), false);
    assert.throws(() => f.server.approve(crypto.randomUUID(), pending.publicKey), /QR_EXPIRED/);
    f.server.approve(code.nonce, pending.publicKey, 'Телефон'); assert.equal(f.saved().length, 1);
    assert.equal(await pairing.loginAttempt(code, key), true); assert.equal(await pairing.loginAttempt(code, identity(), true), false);
    const pin = code.pin, port = code.port; await f.server.stop(); await f.start(port);
    assert.equal(f.server.snapshot().fingerprint, pin); assert.equal(await pairing.loginAttempt(code, key), true);
    assert.equal(f.server.snapshot().devices[0].label, 'Телефон');
  } finally { await f.close(); }
});
test('Windows server transfers actual SFTP bytes, folders and telemetry without any command shell', async () => {
  const key = identity(), f = await setup([{...publicIdentity(key.publicKey), key: undefined, label: 'Телефон'}]), client = new Server();
  try {
    const code = f.offer(); await client.connect({...code, folder: '/'}, {method: 'key', privateKey: key.privateKey}, code.pin);
    const s = client.sftp, content = crypto.randomBytes(2 * 1024 * 1024);
    await call(s, 'mkdir', '/Новая папка'); await call(s, 'writeFile', '/Новая папка/данные.bin', content);
    assert.deepEqual(await call(s, 'readFile', '/Новая папка/данные.bin'), content);
    assert.equal(fs.readFileSync(path.join(f.root, 'Новая папка', 'данные.bin')).length, content.length);
    await call(s, 'rename', '/Новая папка/данные.bin', '/Новая папка/копия.bin');
    assert.ok((await client.list('/Новая папка')).entries.some(e => e.name === 'копия.bin'));
    const metrics = await client.stats(); assert.ok(metrics.freeBytes >= 0); assert.notEqual(metrics.disk, '—');
    await assert.rejects(new Promise((resolve, reject) => client.client.exec('echo forbidden', (e, stream) => e ? reject(e) : resolve(stream))));
    await assert.rejects(call(s, 'rmdir', '/Новая папка')); // non-empty deletion is never recursive
    await call(s, 'unlink', '/Новая папка/копия.bin'); await call(s, 'rmdir', '/Новая папка');
    assert.equal(f.server.snapshot().received, content.length); assert.equal(f.server.snapshot().sent, content.length);
  } finally { client.disconnect(); await f.close(); }
});
test('Windows SFTP rejects traversal, drive paths, ADS, symlinks, hard links and root deletion', async () => {
  const key = identity(), f = await setup([{...publicIdentity(key.publicKey), key: undefined}]), client = new Server();
  try {
    const outside = path.join(f.folder, 'outside.txt'); fs.writeFileSync(outside, 'unchanged');
    fs.symlinkSync(f.folder, path.join(f.root, 'junction')); fs.linkSync(outside, path.join(f.root, 'hardlink.txt'));
    const code = f.offer(); await client.connect({...code, folder: '/'}, {method: 'key', privateKey: key.privateKey}, code.pin);
    for (const name of ['/../outside.txt','/junction/outside.txt','/hardlink.txt','C:/outside.txt','/file:secret','\\\\server\\share']) await assert.rejects(call(client.sftp, 'readFile', name), name);
    for (const name of ['/../escape.bin','/junction/escape.bin','/file:secret','/CON']) await assert.rejects(call(client.sftp, 'writeFile', name, Buffer.from('bad')), name);
    await assert.rejects(call(client.sftp, 'rmdir', '/')); await assert.rejects(call(client.sftp, 'rename', '/', '/old'));
    await assert.rejects(call(client.sftp, 'symlink', outside, '/new-link'));
    assert.equal(fs.readFileSync(outside, 'utf8'), 'unchanged'); assert.equal(fs.existsSync(path.join(f.folder, 'escape.bin')), false);
  } finally { client.disconnect(); await f.close(); }
});
test('Revoking a Windows server device closes its sessions and old QR cannot restore access', async () => {
  const key = identity(), f = await setup(), client = new Server();
  try {
    const code = f.offer(); await pairing.loginAttempt(code, key, true); f.server.approve(code.nonce, key.publicKey);
    await client.connect({...code, folder: '/'}, {method: 'key', privateKey: key.privateKey}, code.pin);
    assert.equal(f.server.snapshot().devices[0].online, true); f.server.revoke(publicIdentity(key.publicKey).id);
    await wait(() => !client.sftp); assert.equal(f.saved().length, 0);
    assert.equal(await pairing.loginAttempt(code, key), false); assert.equal(await pairing.loginAttempt(code, key, true), false);
    const port = code.port; await f.server.stop(); await f.start(port); assert.equal(await pairing.loginAttempt(code, key), false);
  } finally { client.disconnect(); await f.close(); }
});
test('Windows server enforces seven devices and expired requests never grant access', async () => {
  const keys = Array.from({length: 7}, identity), f = await setup(keys.map(k => ({...publicIdentity(k.publicKey), key: undefined}))), eighth = identity();
  try {
    const code = f.offer(); await pairing.loginAttempt(code, eighth, true);
    assert.throws(() => f.server.approve(code.nonce, eighth.publicKey), /DEVICE_LIMIT/);
    assert.equal(f.server.snapshot().devices.length, 7); assert.equal(await pairing.loginAttempt(code, keys[0]), true);
    assert.equal(await pairing.loginAttempt(code, eighth), false);
    f.server.invitation.expires = Date.now() - 1;
    assert.throws(() => f.server.approve(code.nonce, eighth.publicKey), /QR_EXPIRED/);
    assert.equal(f.server.snapshot().pending, null);
  } finally { await f.close(); }
});
test('Windows server never grants access when persisting owner approval fails', async () => {
  const f = await setup(), key = identity();
  try {
    const code = f.offer(); await pairing.loginAttempt(code, key, true);
    f.server.saveDevices = () => { throw new Error('DISK_ERROR'); };
    assert.throws(() => f.server.approve(code.nonce, key.publicKey), /DISK_ERROR/);
    assert.equal(f.server.snapshot().devices.length, 0); assert.equal(await pairing.loginAttempt(code, key), false);
  } finally { await f.close(); }
});
test('Missing-file STAT returns a bounded SFTP error and the client replaces files safely on generic SFTP', async () => {
  const key = identity(), f = await setup([{...publicIdentity(key.publicKey), key: undefined}]), client = new Server();
  try {
    const code = f.offer(); await client.connect({...code, folder: '/'}, {method: 'key', privateKey: key.privateKey}, code.pin);
    let timer;
    try { await assert.rejects(Promise.race([call(client.sftp, 'stat', '/missing.bin'), new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('STAT_TIMEOUT')), 1000); })]), error => error.code === 2); }
    finally { clearTimeout(timer); }
    await call(client.sftp, 'writeFile', '/target.bin', Buffer.from('old'));
    const source = path.join(f.folder, 'source.bin'); fs.writeFileSync(source, 'new');
    const transfers = new Transfers(client), ids = transfers.add([{direction: 'upload', local: source, remote: '/target.bin', label: 'target.bin', overwrite: true}]);
    await wait(() => transfers.items.find(j => j.id === ids[0]).state !== 'queued' && transfers.items.find(j => j.id === ids[0]).state !== 'running');
    assert.equal(transfers.items.find(j => j.id === ids[0]).state, 'completed');
    assert.equal(fs.readFileSync(path.join(f.root, 'target.bin'), 'utf8'), 'new');
    assert.deepEqual(fs.readdirSync(f.root), ['target.bin']); assert.equal(fs.readFileSync(source, 'utf8'), 'new');
  } finally { client.disconnect(); await f.close(); }
});

test('Authenticated Windows metadata explicitly identifies the peer as a PC', async () => {
  const key = identity(), f = await setup([{...publicIdentity(key.publicKey), key: undefined}]), client = new Server();
  try {
    const offer = f.offer();
    await client.connect({...offer, folder: '/'}, {method: 'key', privateKey: key.privateKey}, offer.pin);
    const wire = await new Promise((resolve, reject) => client.client.subsys('localls-status-v3', (error, channel) => {
      if (error) return reject(error);
      let text = ''; channel.setEncoding('utf8'); channel.on('data', part => { text += part; });
      channel.on('error', reject); channel.on('close', () => resolve(text));
    }));
    const fields = wire.split('\n');
    assert.equal(fields[0], 'localls-status-v3'); assert.equal(fields.length, 8);
    assert.equal(fields[6], 'pc'); assert.equal(fields[7], '');
  } finally { client.disconnect(); await f.close(); }
});
