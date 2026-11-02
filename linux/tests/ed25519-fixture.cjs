'use strict';
// ssh2's generator strips leading zero bytes from Ed25519 public keys. Keep all
// 32 bytes from Node's crypto API instead; do not relax production key parsing.
const crypto = require('node:crypto');
const uint = n => { const b = Buffer.alloc(4); b.writeUInt32BE(n); return b; };
const string = b => { b = Buffer.from(b); return Buffer.concat([uint(b.length), b]); };
function identity(seed) {
  const pair = seed
    ? {privateKey: crypto.createPrivateKey({key: Buffer.concat([Buffer.from('302e020100300506032b657004220420', 'hex'), seed]), format: 'der', type: 'pkcs8'})}
    : crypto.generateKeyPairSync('ed25519');
  const jwk = pair.privateKey.export({format: 'jwk'});
  const pub = Buffer.from(jwk.x, 'base64url'), secret = Buffer.from(jwk.d, 'base64url');
  const publicBlob = Buffer.concat([string('ssh-ed25519'), string(pub)]);
  const check = crypto.randomBytes(4);
  let block = Buffer.concat([check, check, string('ssh-ed25519'), string(pub), string(Buffer.concat([secret, pub])), string('test-only')]);
  const padding = 8 - block.length % 8;
  block = Buffer.concat([block, Buffer.from(Array.from({length: padding}, (_, i) => i + 1))]);
  const wire = Buffer.concat([Buffer.from('openssh-key-v1\0'), string('none'), string('none'), string(''), uint(1), string(publicBlob), string(block)]);
  return {publicKey: 'ssh-ed25519 ' + publicBlob.toString('base64'),
    privateKey: '-----BEGIN OPENSSH PRIVATE KEY-----\n' + wire.toString('base64').match(/.{1,70}/g).join('\n') + '\n-----END OPENSSH PRIVATE KEY-----\n'};
}
module.exports = {identity};
