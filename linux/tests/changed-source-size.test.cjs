'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {Server,Transfers}=require('../src/core.cjs'),security=require('../src/security.cjs'),{fixture}=require('./fixture.cjs');
async function setup(){const f=await fixture(),server=new Server(),local=fs.mkdtempSync(path.join(os.tmpdir(),'localls-source-size-'));try{const key=await security.generateIdentity();fs.mkdirSync(path.join(f.root,'.ssh'));fs.writeFileSync(path.join(f.root,'.ssh/authorized_keys'),key.publicKey+'\n',{mode:0o600});f.disablePasswords();await server.connect(f.profile,{method:'key',privateKey:key.privateKey},f.fingerprint);return {f,server,local};}catch(e){server.disconnect();await f.close();fs.rmSync(local,{recursive:true,force:true});throw e;}}
function idle(queue){return new Promise(resolve=>queue.on('active',active=>{if(!active)resolve();}));}
async function bounded(promise){let timer;try{return await Promise.race([promise,new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error('Transfer queue did not settle')),7000);})]);}finally{clearTimeout(timer);}}
for(const direction of ['download','upload'])for(const changedSize of [4,65536])for(const overwrite of (direction==='upload'?[false,true]:[false]))test(direction+' rejects changed source size '+changedSize+(overwrite?' and preserves confirmed overwrite target':''),async()=>{
 const {f,server,local}=await setup();const original=direction==='download'?server.sftp.stat.bind(server.sftp):fs.promises.stat;
 try{
  const source=direction==='download'?path.join(f.root,'changing.bin'):path.join(local,'changing.bin'),target=direction==='download'?path.join(local,'copy.bin'):path.join(f.root,'copy.bin'),prior=Buffer.from('preserve the previous server file\n'),next=Buffer.from('queue continues after a failed source\n');fs.writeFileSync(source,Buffer.alloc(32768,17));fs.writeFileSync(path.join(local,'next.bin'),next);if(overwrite)fs.writeFileSync(target,prior);
  let changed=false;
  if(direction==='download')server.sftp.stat=(p,cb)=>original(p,(error,attrs)=>{if(p==='/changing.bin'&&!changed){changed=true;fs.writeFileSync(source,Buffer.alloc(changedSize,23));}cb(error,attrs);});
  else fs.promises.stat=async(...args)=>{const attrs=await original(...args);if(args[0]===source&&!changed){changed=true;fs.writeFileSync(source,Buffer.alloc(changedSize,23));}return attrs;};
  const queue=new Transfers(server),finished=idle(queue);queue.add([{direction,local:direction==='download'?target:source,remote:direction==='download'?'/changing.bin':'/copy.bin',overwrite},{direction:'upload',local:path.join(local,'next.bin'),remote:'/next-copy.bin'}]);await bounded(finished);
  assert.equal(changed,true);assert.equal(queue.items[0].state,'failed');assert.match(queue.items[0].error,/Исходный файл изменился/);assert.ok(queue.items[0].bytes<=queue.items[0].total,'Progress must never exceed the advertised total');
  if(overwrite)assert.deepEqual(fs.readFileSync(target),prior);else assert.equal(fs.existsSync(target),false);
  assert.equal(queue.items[1].state,'completed');assert.deepEqual(fs.readFileSync(path.join(f.root,'next-copy.bin')),next);for(const folder of [local,f.root])assert.equal(fs.readdirSync(folder).some(n=>n.startsWith('.localls-')),false);assert.equal(f.passwordAttempts,0);
 }finally{if(direction==='download')server.sftp.stat=original;else fs.promises.stat=original;server.disconnect();await f.close();fs.rmSync(local,{recursive:true,force:true});}
});
