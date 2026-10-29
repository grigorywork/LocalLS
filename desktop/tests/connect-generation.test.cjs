'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {Client}=require('ssh2'),{Server}=require('../src/core.cjs'),security=require('../src/security.cjs'),{fixture}=require('./fixture.cjs');
function bounded(promise){let timer;return Promise.race([promise,new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error('Connection barrier was not reached')),7000);})]).finally(()=>clearTimeout(timer));}
async function setup(stage){
 const f=await fixture();let entered,release,first=true;const barrier=new Promise(resolve=>entered=resolve);
 class DelayedClient extends Client{
  sftp(callback){const hold=first;first=false;return super.sftp((error,s)=>{
   if(!hold||error)return callback(error,s);
   if(stage==='sftp'){release=()=>callback(error,s);entered();return;}
   const original=s[stage].bind(s);let once=true;
   s[stage]=(...args)=>{if(stage==='readdir'&&typeof args[0]!=='string')return original(...args);const cb=args.pop();return original(...args,(...result)=>{if(!once)return cb(...result);once=false;release=()=>cb(...result);entered();});};
   callback(error,s);
  });}
 }
 const server=new Server(DelayedClient);
 try{const identity=await security.generateIdentity();fs.mkdirSync(path.join(f.root,'.ssh'));fs.writeFileSync(path.join(f.root,'.ssh/authorized_keys'),identity.publicKey+'\n',{mode:0o600});f.disablePasswords();return {f,server,barrier,auth:{method:'key',privateKey:identity.privateKey},release(){const cb=release;release=null;cb?.();}};}
 catch(error){server.disconnect();await f.close();throw error;}
}
test('disconnect during SFTP setup never resurrects a disconnected channel',async()=>{
 const h=await setup('sftp');const {f,server}=h;
 try{const pending=server.connect(f.profile,h.auth,f.fingerprint);const rejected=assert.rejects(pending);await bounded(h.barrier);server.disconnect();h.release();await bounded(rejected);assert.equal(server.client,null);assert.equal(server.sftp,null);assert.throws(()=>server.requireSftp(),/NOT_CONNECTED/);assert.equal(f.passwordAttempts,0);}
 finally{h.release();server.disconnect();await f.close();}
});
for(const stage of ['sftp','realpath','readdir'])test('late '+stage+' from an older connection cannot replace or report the new session',async()=>{
 const h=await setup(stage);const {f,server}=h;
 try{const pending=server.connect(f.profile,h.auth,f.fingerprint);const outcome=pending.then(value=>({value}),error=>({error}));await bounded(h.barrier);
  const current=await server.connect({...f.profile,folder:'/documents'},h.auth,f.fingerprint),s=server.sftp,c=server.client;assert.equal(current.path,'/documents');h.release();const result=await bounded(outcome);
  assert.ok(result.error,'The superseded connection must reject');assert.equal(server.client,c);assert.equal(server.sftp,s);assert.equal(server.location,'/documents');const list=await server.list('/');assert.ok(list.entries.some(e=>e.name==='hello.txt'));assert.equal(f.passwordAttempts,0);
 }finally{h.release();server.disconnect();await f.close();}
});
