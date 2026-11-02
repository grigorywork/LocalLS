'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), os = require('node:os'), path = require('node:path');
const {utils} = require('ssh2');
const {SharedFiles} = require('../src/server-files.cjs');
const {EmbeddedServer, publicIdentity} = require('../src/embedded-server.cjs');
const {Server, Transfers, call} = require('../src/core.cjs');
const {planDownload} = require('../src/transfer-plan.cjs');
const {identity} = require('./ed25519-fixture.cjs');

test('Portable share classifies incompatible POSIX names as denied without weakening path policy', () => {
  const folder = fs.mkdtempSync(path.join(os.tmpdir(), 'localls-portable-path-'));
  try {
    const share = new SharedFiles(folder);
    for (const name of ['CON', 'a:b.txt', 'trailing.', 'trailing ', 'a?b.txt']) {
      fs.writeFileSync(path.join(folder, name), 'private original');
      assert.throws(() => share.resolve('/' + name), {message: 'SHARE_DENIED'}, name);
      assert.equal(fs.readFileSync(path.join(folder, name), 'utf8'), 'private original');
    }
    fs.writeFileSync(path.join(folder, 'обычный.txt'), 'visible');
    assert.equal(share.resolve('/обычный.txt'), path.join(folder, 'обычный.txt'));
  } finally { fs.rmSync(folder, {recursive: true, force: true}); }
});

test('Real Fedora SFTP lists and downloads valid siblings beside incompatible POSIX filenames', async () => {
  const folder = fs.mkdtempSync(path.join(os.tmpdir(), 'localls-portable-listing-'));
  const root = path.join(folder, 'shared'), download = path.join(folder, 'download');
  fs.mkdirSync(root); fs.mkdirSync(download); fs.mkdirSync(path.join(root, 'nested'));
  const content = Buffer.from('Уникальные данные без потери содержимого\n');
  fs.writeFileSync(path.join(root, 'visible.txt'), content);
  fs.writeFileSync(path.join(root, 'CON'), 'reserved');
  fs.writeFileSync(path.join(root, 'nested', 'a:b.txt'), 'POSIX original');
  fs.writeFileSync(path.join(root, 'nested', 'keep.txt'), content);
  const key = identity(), service = new EmbeddedServer(), client = new Server();
  try {
    const privateKey = utils.generateKeyPairSync('rsa', {bits: 3072}).private;
    await service.start({host: '127.0.0.1', port: 0, user: 'localls', root, privateKey,
      devices: [{...publicIdentity(key.publicKey), key: undefined}]});
    const code = service.invite('127.0.0.1');
    const listing = await client.connect({...code, folder: '/'}, {method: 'key', privateKey: key.privateKey}, code.pin);
    assert.deepEqual(listing.entries.map(e => e.name), ['nested', 'visible.txt']);
    assert.deepEqual((await client.list('/nested')).entries.map(e => e.name), ['keep.txt']);
    for (const remote of ['/CON', '/nested/a:b.txt'])
      await assert.rejects(call(client.sftp, 'readFile', remote), error => error.code === 3);
    const jobs = await planDownload({files: ['/nested', '/visible.txt'], folder: download,
      sftp: client.sftp, binding: 'synthetic-loopback'});
    const queue = new Transfers(client);
    const finished = new Promise(resolve => queue.on('active', active => { if (!active) resolve(); }));
    queue.add(jobs);
    await finished;
    assert.ok(queue.items.length >= 2);
    assert.ok(queue.items.every(job => job.state === 'completed'));
    assert.deepEqual(fs.readFileSync(path.join(download, 'visible.txt')), content);
    assert.deepEqual(fs.readFileSync(path.join(download, 'nested', 'keep.txt')), content);
    assert.equal(fs.readFileSync(path.join(root, 'CON'), 'utf8'), 'reserved');
    assert.equal(fs.readFileSync(path.join(root, 'nested', 'a:b.txt'), 'utf8'), 'POSIX original');
  } finally { client.disconnect(); await service.stop(); fs.rmSync(folder, {recursive: true, force: true}); }
});
