'use strict';
const fs = require('node:fs');
const path = require('node:path');
const security = require('./security.cjs');

function available(storage) {
  return storage.isEncryptionAvailable() && storage.getSelectedStorageBackend?.() !== 'basic_text';
}
async function hostIdentity(file, storage) {
  if (!available(storage)) throw new Error('SERVER_VAULT_REQUIRED');
  const read = () => {
    try {
      const text = storage.decryptString(fs.readFileSync(file));
      const record = JSON.parse(text);
      if (record.version !== 1) throw new Error();
      const info = security.inspectIdentity(record.privateKey);
      return {privateKey: record.privateKey, fingerprint: info.fingerprint};
    } catch { throw new Error('SERVER_VAULT_UNREADABLE'); }
  };
  if (fs.existsSync(file)) return read();
  const key = await security.generateIdentity();
  const encrypted = storage.encryptString(JSON.stringify({version: 1, privateKey: key.privateKey}));
  fs.mkdirSync(path.dirname(file), {recursive: true});
  // Exclusive creation: a concurrent writer or an old identity is never replaced.
  try { fs.writeFileSync(file, encrypted, {flag: 'wx', mode: 0o600}); }
  catch (error) { if (error.code === 'EEXIST') return read(); throw new Error('SERVER_VAULT_WRITE'); }
  return {privateKey: key.privateKey, fingerprint: key.fingerprint};
}
function readConfig(file, fallback) {
  if (!fs.existsSync(file)) return fallback;
  try {
    const config = JSON.parse(fs.readFileSync(file, 'utf8'));
    if (!config || !Number.isInteger(config.port) || config.port < 1 || config.port > 65535 || typeof config.root !== 'string' || !config.root || !Array.isArray(config.devices) || config.devices.length > 7 || typeof config.user !== 'string') throw new Error();
    return config;
  } catch { throw new Error('SERVER_CONFIG_UNREADABLE'); }
}
module.exports = {available, hostIdentity, readConfig};
