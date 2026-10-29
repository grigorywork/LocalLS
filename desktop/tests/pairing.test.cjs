'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const QRCode=require('qrcode'),{PNG}=require('pngjs');
const pairing=require('../src/pairing.cjs'),security=require('../src/security.cjs'),{fixture}=require('./fixture.cjs');
const invitation=overrides=>({format:'LocalLS-pairing',version:1,type:'server',host:'127.0.0.1',port:8022,user:'localls',pin:'SHA256:'+Buffer.alloc(32,7).toString('base64').replace(/=+$/,''),nonce:crypto.randomUUID(),expires:Date.now()+600000,...overrides});
test('actual QR image imports public parameters, including a rotated photo',async()=>{
 const code=invitation(),png=PNG.sync.read(await QRCode.toBuffer(JSON.stringify(code),{width:720,margin:4}));
 assert.deepEqual(pairing.decodePixels(png.data,png.width,png.height),code);
 const rotated=Buffer.alloc(png.data.length);for(let y=0;y<png.height;y++)for(let x=0;x<png.width;x++)png.data.copy(rotated,(x*png.height+png.height-1-y)*4,(y*png.width+x)*4,(y*png.width+x+1)*4);
 assert.deepEqual(pairing.decodePixels(rotated,png.height,png.width),code);
 const bgra=Buffer.from(png.data);for(let i=0;i<bgra.length;i+=4)[bgra[i],bgra[i+2]]=[bgra[i+2],bgra[i]];
 assert.deepEqual(pairing.imageCode({isEmpty:()=>false,getSize:()=>({width:png.width,height:png.height}),toBitmap:()=>bgra}),code);
});
test('expired, malformed, secret-bearing and oversized QR payloads fail closed',()=>{
 for(const value of [invitation({expires:Date.now()-1}),invitation({port:'8022'}),invitation({user:'user;command'}),invitation({pin:'wrong'}),{...invitation(),privateKey:'never-import-this'},invitation({type:'client'}),null])assert.throws(()=>pairing.decode(JSON.stringify(value)));
 assert.throws(()=>pairing.decode(' '.repeat(12001)));assert.throws(()=>pairing.decodePixels(Buffer.alloc(4),30000,30000));assert.throws(()=>pairing.decodePixels(Buffer.alloc(40*40*4),40,40));
});
test('transparent PNG reads on white instead of turning every transparent module black',async()=>{
 const code=invitation(),png=PNG.sync.read(await QRCode.toBuffer(JSON.stringify(code),{width:720,margin:4,color:{dark:'#000000ff',light:'#00000000'}}));
 assert.deepEqual(pairing.decodePixels(png.data,png.width,png.height),code);
});
test('matching code binds exact server, nonce and client identity',async()=>{
 const code=invitation(),key=await security.generateIdentity(),other=await security.generateIdentity();const value=pairing.syncCode(code,key.publicKey);assert.match(value,/^\d{4} \d{4}$/);
 assert.equal(value,pairing.syncCode(code,key.publicKey.replace(/ LocalLS$/,' comment')));assert.notEqual(value,pairing.syncCode({...code,nonce:crypto.randomUUID()},key.publicKey));assert.notEqual(value,pairing.syncCode(code,other.publicKey));
});
test('desktop QR login requires local owner approval and keeps working without the QR',async()=>{
 const offer=invitation(),f=await fixture('fixture',offer);f.disablePasswords();const code={...offer,...f.profile,pin:f.fingerprint};delete code.folder;delete code.fingerprint;code.format='LocalLS-pairing';const key=await security.generateIdentity();
 try{
  assert.equal(await pairing.loginAttempt(code,key),false);assert.equal(f.pairingPending,'');
  assert.equal(await pairing.loginAttempt({...code,nonce:crypto.randomUUID()},key,true),false);assert.equal(f.pairingPending,'');
  assert.equal(await pairing.loginAttempt(code,key,true),false);assert.ok(f.pairingPending);assert.equal(await pairing.loginAttempt(code,key),false);f.approvePairing();
  assert.equal(await pairing.loginAttempt({...code,expires:Date.now()-1},key),true);assert.equal(f.passwordAttempts,0);
 }finally{await f.close();}
});
test('changed server pin is blocked before client authentication',async()=>{
 const f=await fixture(),key=await security.generateIdentity();try{const code=invitation({...f.profile,pin:'SHA256:'+Buffer.alloc(32,9).toString('base64').replace(/=+$/,'')});await assert.rejects(pairing.loginAttempt(code,key),e=>e.code==='HOST_KEY_CHANGED');assert.equal(f.publicKeyAttempts,0);assert.equal(f.passwordAttempts,0);}finally{await f.close();}
});
