'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), os = require('node:os'), path = require('node:path'), crypto = require('node:crypto');
const {utils} = require('ssh2');
const batch = require('../src/file-batch.cjs');
const {EmbeddedServer, publicIdentity} = require('../src/embedded-server.cjs');
const {Server, call} = require('../src/core.cjs');
const {identity: makeIdentity} = require('./ed25519-fixture.cjs');

function workspace() {
  const base = fs.mkdtempSync(path.join(os.tmpdir(), 'localls-batch-'));
  const source = path.join(base, 'source');
  fs.mkdirSync(source);
  return {base, source, visible: () => fs.readdirSync(base).filter(name => name !== 'source'), close: () => fs.rmSync(base, {recursive: true, force: true})};
}

test('a batch archive round-trips every file and is deleted after extraction', async () => {
  const box = workspace();
  try {
    const photo = crypto.randomBytes(300 * 1024);
    fs.writeFileSync(path.join(box.source, 'photo.jpg'), photo);
    fs.writeFileSync(path.join(box.source, 'Заметка.txt'), 'привет LocalLS');
    const archive = await batch.createArchive([
      {path: path.join(box.source, 'photo.jpg'), name: 'photo.jpg'},
      {path: path.join(box.source, 'Заметка.txt'), name: 'Заметка.txt'},
    ], {directory: box.base});
    assert.ok(batch.isBatchName(batch.archiveName()));
    assert.equal(archive.count, 2);
    assert.ok(archive.bytes < photo.length + 4096, 'stored photos must not be recompressed');
    const result = await batch.extractArchive(archive.file, box.base);
    assert.equal(result.count, 2);
    assert.deepEqual(fs.readFileSync(path.join(box.base, 'photo.jpg')), photo);
    assert.equal(fs.readFileSync(path.join(box.base, 'Заметка.txt'), 'utf8'), 'привет LocalLS');
    assert.equal(fs.existsSync(archive.file), false);
    assert.deepEqual(box.visible().sort(), ['photo.jpg', 'Заметка.txt'].sort());
  } finally { box.close(); }
});

test('extraction keeps an existing different file and drops an identical duplicate', async () => {
  const box = workspace();
  try {
    fs.writeFileSync(path.join(box.source, 'same.txt'), 'same');
    fs.writeFileSync(path.join(box.source, 'other.txt'), 'new');
    fs.writeFileSync(path.join(box.base, 'same.txt'), 'same');
    fs.writeFileSync(path.join(box.base, 'other.txt'), 'old');
    const archive = await batch.createArchive([
      {path: path.join(box.source, 'same.txt'), name: 'same.txt'},
      {path: path.join(box.source, 'other.txt'), name: 'other.txt'},
    ], {directory: box.base});
    await batch.extractArchive(archive.file, box.base);
    assert.deepEqual(box.visible().sort(), ['other (1).txt', 'other.txt', 'same.txt']);
    assert.equal(fs.readFileSync(path.join(box.base, 'other.txt'), 'utf8'), 'old');
    assert.equal(fs.readFileSync(path.join(box.base, 'other (1).txt'), 'utf8'), 'new');
  } finally { box.close(); }
});

test('a tampered archive is refused without touching the destination', async () => {
  const box = workspace();
  try {
    fs.writeFileSync(path.join(box.source, 'secret.txt'), 'payload');
    const archive = await batch.createArchive([{path: path.join(box.source, 'secret.txt'), name: 'secret.jpg'}], {directory: box.base});
    const data = fs.readFileSync(archive.file);
    const at = data.indexOf(Buffer.from('payload'));
    data[at] ^= 0xff;
    fs.writeFileSync(archive.file, data);
    await assert.rejects(batch.extractArchive(archive.file, box.base), /BATCH_DATA/);
    assert.deepEqual(box.visible(), [path.basename(archive.file)]);
    assert.equal(fs.existsSync(archive.file), true, 'the damaged archive is kept for the owner');
  } finally { box.close(); }
});

test('traversal names are rejected before any file is written', async () => {
  const box = workspace();
  try {
    fs.writeFileSync(path.join(box.source, 'abcdefghi.txt'), 'escape');
    const archive = await batch.createArchive([{path: path.join(box.source, 'abcdefghi.txt'), name: 'abcdefghi.txt'}], {directory: box.base});
    const data = fs.readFileSync(archive.file);
    for (let at = data.indexOf('abcdefghi.txt'); at !== -1; at = data.indexOf('abcdefghi.txt', at + 1)) data.write('../evil12.txt', at, 'latin1');
    fs.writeFileSync(archive.file, data);
    await assert.rejects(batch.extractArchive(archive.file, box.base), /BATCH_ENTRY/);
    assert.equal(fs.existsSync(path.join(path.dirname(box.base), 'evil12.txt')), false);
    assert.deepEqual(box.visible(), [path.basename(archive.file)]);
  } finally { box.close(); }
});

test('only small plain files in a sensible number are batched', () => {
  assert.equal(batch.canBatch([{path: 'a.jpg', size: 10}, {path: 'b.jpg', size: 10}]), true);
  assert.equal(batch.canBatch([{path: 'a.jpg', size: 10}]), false);
  assert.equal(batch.canBatch([{path: 'a.jpg', size: 10}, {path: 'b.jpg', size: batch.LIMITS.fileSize + 1}]), false);
  assert.equal(batch.canBatch([{path: 'a.jpg', size: batch.LIMITS.total}, {path: 'b.jpg', size: 1}]), false);
  assert.equal(batch.isBatchName('LocalLS-20261117-101500-0123abcd.localls.zip'), true);
  assert.equal(batch.isBatchName('LocalLS-20261117-101500-0123abcd.zip'), false);
  assert.equal(batch.isBatchName('photos.localls.zip'), false);
});

function sftpStub(existing = new Set()) {
  const call = (value, callback) => callback(null, value);
  return {
    on() {},
    realpath: (_, callback) => callback(null, '/dest'),
    lstat: (target, callback) => {
      if (target === '/dest') return call({isDirectory: () => true, isSymbolicLink: () => false}, callback);
      if (existing.has(path.posix.basename(target))) return call({isFile: () => true, isSymbolicLink: () => false}, callback);
      callback(Object.assign(new Error('no such file'), {code: 2}));
    },
    mkdir: (_, callback) => callback(null),
  };
}

test('the upload planner sends many small files as a single archive job', async () => {
  const box = workspace();
  try {
    const one = path.join(box.source, 'one.txt'), two = path.join(box.source, 'two.txt');
    fs.writeFileSync(one, 'one'); fs.writeFileSync(two, 'two');
    const {planUpload} = require('../src/transfer-plan.cjs');
    const jobs = await planUpload({files: [one, two], folder: '/dest', sftp: sftpStub(), binding: 'b', allowed: value => value, confirmOverwrite: async () => true});
    assert.equal(jobs.length, 1);
    assert.equal(jobs[0].direction, 'upload');
    assert.equal(jobs[0].batch.count, 2);
    assert.ok(jobs[0].remote.endsWith('.localls.zip'));
    const archive = path.join(box.base, 'received.zip');
    fs.copyFileSync(jobs[0].local, archive);
    const result = await batch.extractArchive(archive, box.base);
    assert.equal(result.count, 2);
    assert.equal(fs.readFileSync(path.join(box.base, 'one.txt'), 'utf8'), 'one');
    fs.rmSync(jobs[0].local, {force: true});
  } finally { box.close(); }
});

test('the planner falls back to separate files when a destination already exists', async () => {
  const box = workspace();
  try {
    const one = path.join(box.source, 'one.txt'), two = path.join(box.source, 'two.txt');
    fs.writeFileSync(one, 'one'); fs.writeFileSync(two, 'two');
    const {planUpload} = require('../src/transfer-plan.cjs');
    const jobs = await planUpload({files: [one, two], folder: '/dest', sftp: sftpStub(new Set(['one.txt'])), binding: 'b', allowed: value => value, confirmOverwrite: async () => true});
    assert.equal(jobs.length, 2);
    assert.equal(jobs.every(job => !job.batch), true);
  } finally { box.close(); }
});

async function serverFixture() {  const folder = fs.mkdtempSync(path.join(os.tmpdir(), 'localls-batch-server-')), root = path.join(folder, 'shared');
  fs.mkdirSync(root);
  const host = utils.generateKeyPairSync('rsa', {bits: 3072}).private;
  const identity = makeIdentity();
  const server = new EmbeddedServer({saveDevices: () => {}});
  await server.start({host: '127.0.0.1', port: 0, user: 'localls', root, privateKey: host, devices: [{...publicIdentity(identity.publicKey), key: undefined, label: 'phone'}]});
  return {folder, root, server, identity, close: async () => { await server.stop(); fs.rmSync(folder, {recursive: true, force: true}); }};
}

test('the LocalLS receiver expands a published batch and removes the archive', async () => {
  const f = await serverFixture(), client = new Server(), box = workspace();
  try {
    const code = f.server.invite('127.0.0.1');
    await client.connect({...code, folder: '/'}, {method: 'key', privateKey: f.identity.privateKey}, code.pin);
    fs.writeFileSync(path.join(box.source, 'one.txt'), 'один');
    fs.writeFileSync(path.join(box.source, 'two.txt'), 'два');
    const archive = await batch.createArchive([
      {path: path.join(box.source, 'one.txt'), name: 'one.txt'},
      {path: path.join(box.source, 'two.txt'), name: 'two.txt'},
    ], {directory: box.base});
    const name = batch.archiveName(), part = '/.localls-' + crypto.randomUUID() + '.part';
    await call(client.sftp, 'writeFile', part, fs.readFileSync(archive.file));
    await call(client.sftp, 'rename', part, '/' + name);
    const end = Date.now() + 5000;
    while (!fs.existsSync(path.join(f.root, 'two.txt')) && Date.now() < end) await new Promise(resolve => setTimeout(resolve, 20));
    assert.equal(fs.readFileSync(path.join(f.root, 'one.txt'), 'utf8'), 'один');
    assert.equal(fs.readFileSync(path.join(f.root, 'two.txt'), 'utf8'), 'два');
    assert.equal(fs.existsSync(path.join(f.root, name)), false);
  } finally { client.disconnect(); await f.close(); box.close(); }
});
