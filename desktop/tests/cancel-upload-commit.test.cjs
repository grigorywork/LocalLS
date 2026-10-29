'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {Server,Transfers}=require('../src/core.cjs'),security=require('../src/security.cjs'),{fixture}=require('./fixture.cjs');
async function connect(){
 const f=await fixture(),server=new Server(),local=fs.mkdtempSync(path.join(os.tmpdir(),'localls-upload-commit-'));
 try{const identity=await security.generateIdentity();fs.mkdirSync(path.join(f.root,'.ssh'));fs.writeFileSync(path.join(f.root,'.ssh/authorized_keys'),identity.publicKey+'\n',{mode:0o600});f.disablePasswords();await server.connect(f.profile,{method:'key',privateKey:identity.privateKey},f.fingerprint);return {f,server,local};}
 catch(error){server.disconnect();await f.close();fs.rmSync(local,{recursive:true,force:true});throw error;}
}
function idle(queue){return new Promise(resolve=>queue.on('active',active=>{if(!active)resolve();}));}
async function bounded(promise){let timer;try{return await Promise.race([promise,new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error('SFTP publication barrier was not reached')),7000);})]);}finally{clearTimeout(timer);}}
test('cancel before upload publication removes its temporary file and preserves the next queued transfer',async()=>{
 const {f,server,local}=await connect();let release;
 try{
  const bytes=Buffer.alloc(32768,19),after=Buffer.from('next job is independent\n'),source=path.join(local,'source.bin'),next=path.join(local,'next.bin');fs.writeFileSync(source,bytes);fs.writeFileSync(next,after);
  const original=server.sftp.lstat.bind(server.sftp);let entered,held=false;const barrier=new Promise(resolve=>entered=resolve);
  server.sftp.lstat=(name,callback)=>original(name,(...args)=>{if(name==='/cancel-before-commit.bin'&&!held){held=true;release=()=>callback(...args);entered();}else callback(...args);});
  const queue=new Transfers(server),finished=idle(queue);const ids=queue.add([{direction:'upload',local:source,remote:'/cancel-before-commit.bin',label:'cancel',overwrite:false},{direction:'upload',local:next,remote:'/after-cancel.bin',label:'next',overwrite:false}]);
  await bounded(barrier);queue.cancel(ids[0]);const resume=release;release=null;resume();await bounded(finished);
  assert.equal(queue.items[0].state,'cancelled');assert.equal(fs.existsSync(path.join(f.root,'cancel-before-commit.bin')),false);assert.equal(queue.items[1].state,'completed');assert.deepEqual(fs.readFileSync(path.join(f.root,'after-cancel.bin')),after);assert.equal(fs.readdirSync(f.root).some(name=>name.startsWith('.localls-')),false);assert.equal(f.passwordAttempts,0);
 }finally{release?.();server.disconnect();await f.close();fs.rmSync(local,{recursive:true,force:true});}
});
test('a cancellation after the server has committed is reported as completed rather than inventing rollback',async()=>{
 const {f,server,local}=await connect();let release;
 try{
  const bytes=Buffer.alloc(4096,23),source=path.join(local,'source.bin');fs.writeFileSync(source,bytes);const original=server.sftp.rename.bind(server.sftp);let entered,held=false;const barrier=new Promise(resolve=>entered=resolve);
  server.sftp.rename=(from,to,callback)=>original(from,to,(...args)=>{if(to==='/already-committed.bin'&&!held){held=true;release=()=>callback(...args);entered();}else callback(...args);});
  const queue=new Transfers(server),finished=idle(queue);const [id]=queue.add([{direction:'upload',local:source,remote:'/already-committed.bin',label:'committed',overwrite:false}]);
  await bounded(barrier);assert.deepEqual(fs.readFileSync(path.join(f.root,'already-committed.bin')),bytes);queue.cancel(id);const resume=release;release=null;resume();await bounded(finished);
  assert.equal(queue.items[0].state,'completed');assert.deepEqual(fs.readFileSync(path.join(f.root,'already-committed.bin')),bytes);assert.equal(fs.readdirSync(f.root).some(name=>name.startsWith('.localls-')),false);assert.equal(f.passwordAttempts,0);
 }finally{release?.();server.disconnect();await f.close();fs.rmSync(local,{recursive:true,force:true});}
});
