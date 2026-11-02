'use strict';
const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), os = require('node:os'), path = require('node:path'), crypto = require('node:crypto');
const {hostIdentity, readConfig} = require('../src/server-vault.cjs');
function vault() {
  const key = crypto.randomBytes(32);
  return {isEncryptionAvailable: () => true, getSelectedStorageBackend: () => 'test_encrypted',
    encryptString: text => { const iv = crypto.randomBytes(12), cipher = crypto.createCipheriv('aes-256-gcm', key, iv); return Buffer.concat([iv, cipher.update(text, 'utf8'), cipher.final(), cipher.getAuthTag()]); },
    decryptString: data => { const decipher = crypto.createDecipheriv('aes-256-gcm', key, data.subarray(0, 12)); decipher.setAuthTag(data.subarray(-16)); return Buffer.concat([decipher.update(data.subarray(12, -16)), decipher.final()]).toString(); }};
}
test('Windows host identity is encrypted, restored unchanged and never replaced by another concurrent creation', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'localls-server-vault-')), file = path.join(dir, 'host.bin'), storage = vault();
  try {
    const [a, b] = await Promise.all([hostIdentity(file, storage), hostIdentity(file, storage)]);
    assert.equal(a.fingerprint, b.fingerprint); assert.equal(a.privateKey, b.privateKey);
    const bytes = fs.readFileSync(file); assert.ok(!bytes.includes(Buffer.from('PRIVATE KEY')));
    assert.deepEqual(await hostIdentity(file, storage), a); assert.deepEqual(fs.readFileSync(file), bytes);
    await assert.rejects(hostIdentity(file, vault()), /SERVER_VAULT_UNREADABLE/); assert.deepEqual(fs.readFileSync(file), bytes);
    fs.writeFileSync(file, 'corrupt-existing-vault'); await assert.rejects(hostIdentity(file, storage), /SERVER_VAULT_UNREADABLE/); assert.equal(fs.readFileSync(file, 'utf8'), 'corrupt-existing-vault');
  } finally { fs.rmSync(dir, {recursive: true, force: true}); }
});
test('Unavailable or plaintext OS storage never creates a Windows server identity', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'localls-server-vault-')), file = path.join(dir, 'host.bin');
  try {
    for (const storage of [{...vault(), isEncryptionAvailable: () => false}, {...vault(), getSelectedStorageBackend: () => 'basic_text'}]) await assert.rejects(hostIdentity(file, storage), /SERVER_VAULT_REQUIRED/);
    assert.equal(fs.existsSync(file), false);
  } finally { fs.rmSync(dir, {recursive: true, force: true}); }
});
test('An unreadable server configuration is preserved rather than resetting trusted devices', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'localls-server-config-')), file = path.join(dir, 'server.json');
  try {
    const fallback = {port: 8022, root: dir, user: 'localls', devices: []}; assert.equal(readConfig(file, fallback), fallback);
    fs.writeFileSync(file, '{broken'); assert.throws(() => readConfig(file, fallback), /SERVER_CONFIG_UNREADABLE/); assert.equal(fs.readFileSync(file, 'utf8'), '{broken');
    fs.writeFileSync(file, JSON.stringify({...fallback, devices: [{publicKey: 'public-only'}]})); assert.equal(readConfig(file, fallback).devices.length, 1);
  } finally { fs.rmSync(dir, {recursive: true, force: true}); }
});
