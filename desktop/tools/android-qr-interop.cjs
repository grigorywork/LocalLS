'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const QRCode=require('qrcode'),{PNG}=require('pngjs'),pairing=require('../src/pairing.cjs'),security=require('../src/security.cjs'),{Server,call}=require('../src/core.cjs');
(async()=>{
 const input=JSON.parse(fs.readFileSync(process.argv[2],'utf8')),png=PNG.sync.read(await QRCode.toBuffer(JSON.stringify(input),{width:720,margin:4}));
 const code=pairing.decodePixels(png.data,png.width,png.height),identity=await security.generateIdentity();assert.equal(await pairing.loginAttempt(code,identity),false);
 process.stdout.write(JSON.stringify({publicKey:identity.publicKey,code:pairing.syncCode(code,identity.publicKey)})+'\n');
 const until=Date.now()+20000;let approved=false;while(Date.now()<until){if(await pairing.loginAttempt(code,identity)){approved=true;break;}if(await pairing.loginAttempt(code,identity,true)){approved=true;break;}await new Promise(r=>setTimeout(r,100));}
 assert.ok(approved,'Owner approval did not enable desktop key');const server=new Server();try{
  await server.connect({...code,folder:'.',fingerprint:code.pin},{method:'key',privateKey:identity.privateKey},code.pin);
  const metrics=await server.stats();assert.ok(Number.isSafeInteger(metrics.freeBytes)&&metrics.freeBytes>=0);assert.match(metrics.battery,/^(—|(?:100|[0-9]{1,2})%)$/);
  const bytes=Buffer.from('PC QR to Android SFTP\n');await call(server.sftp,'writeFile','/computer.txt',bytes);assert.deepEqual(await call(server.sftp,'readFile','/computer.txt'),bytes);
 }finally{server.disconnect();}
 assert.equal(await pairing.loginAttempt({...code,expires:Date.now()-1},identity),true);process.stdout.write('INTEROP_OK\n');
})().catch(()=>{console.error('Desktop QR interoperability failed');process.exitCode=1;});
