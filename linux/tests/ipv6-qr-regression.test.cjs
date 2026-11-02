'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const pairing=require('../src/pairing.cjs'),security=require('../src/security.cjs'),{Server,call}=require('../src/core.cjs'),{fixture}=require('./fixture.cjs');
const invitation=overrides=>({format:'LocalLS-pairing',version:1,type:'server',host:'::1',port:8022,user:'localls',pin:'SHA256:'+Buffer.alloc(32,7).toString('base64').replace(/=+$/,''),nonce:crypto.randomUUID(),expires:Date.now()+600000,...overrides});
test('QR keeps valid leading-colon IPv6 addresses and rejects padded protocol fields',()=>{
 for(const host of ['::1','::ffff:127.0.0.1','2001:db8::1']){const code=invitation({host});assert.deepEqual(pairing.decode(JSON.stringify(code)),code);}
 for(const host of [':::1','::evil','[::1]',' ::1','::1\n','127.0.0.1\n'])assert.throws(()=>pairing.decode(JSON.stringify(invitation({host}))));
 for(const user of [' localls','localls\n'])assert.throws(()=>pairing.decode(JSON.stringify(invitation({user}))));
});
test('IPv6 QR login requires approval, transfers real SFTP bytes and pins before authentication',async()=>{
 const offer=invitation(),f=await fixture('localls',offer,'::1'),identity=await security.generateIdentity(),server=new Server();
 try{
  f.disablePasswords();assert.equal(f.profile.host,'::1');const code=pairing.decode(JSON.stringify(invitation({port:f.profile.port,pin:f.fingerprint,nonce:offer.nonce,expires:offer.expires})));
  assert.equal(await pairing.loginAttempt(code,identity),false);assert.equal(f.pairingPending,'');assert.equal(await pairing.loginAttempt(code,identity,true),false);assert.ok(f.pairingPending);assert.equal(await pairing.loginAttempt(code,identity),false);
  f.approvePairing();assert.equal(await pairing.loginAttempt(code,identity),true);await server.connect({...code,folder:'.',fingerprint:code.pin},{method:'key',privateKey:identity.privateKey},code.pin);
  const bytes=crypto.randomBytes(4096);await call(server.sftp,'writeFile','/ipv6.bin',bytes);assert.deepEqual(await call(server.sftp,'readFile','/ipv6.bin'),bytes);
  const before=f.publicKeyAttempts;await assert.rejects(pairing.loginAttempt({...code,pin:'SHA256:'+Buffer.alloc(32,9).toString('base64').replace(/=+$/,'')},identity),e=>e.code==='HOST_KEY_CHANGED');assert.equal(f.publicKeyAttempts,before);assert.equal(f.passwordAttempts,0);
 }finally{server.disconnect();await f.close();}
});
