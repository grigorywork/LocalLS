'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path'),crypto=require('node:crypto');
const {Server:SSHServer}=require('ssh2');
const {Server,fingerprint}=require('../src/core.cjs');
const {EmbeddedServer,publicIdentity}=require('../src/embedded-server.cjs');
const {SharedFiles,serveFiles}=require('../src/server-files.cjs');
const {identity}=require('./ed25519-fixture.cjs');
const {fixture}=require('./fixture.cjs');
function bounded(p,ms=12000){let timer;return Promise.race([p,new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error('AUDIT_BOUND_EXCEEDED')),ms);})]).finally(()=>clearTimeout(timer));}
function barrier(){let resolve;return {promise:new Promise(r=>resolve=r),resolve:()=>resolve()};}
test('real status v2 preserves storage capacity and live charging state for existing UI',async()=>{
 const folder=fs.mkdtempSync(path.join(os.tmpdir(),'localls-metrics-')),root=path.join(folder,'share');fs.mkdirSync(root);
 const key=identity(),pub=publicIdentity(key.publicKey),host=crypto.generateKeyPairSync('rsa',{modulusLength:3072,privateKeyEncoding:{type:'pkcs1',format:'pem'},publicKeyEncoding:{type:'spki',format:'pem'}}).privateKey;
 let charging=1;const service=new EmbeddedServer({telemetry:()=>({battery:63,free:1024,total:4096,charging})}),client=new Server();
 try{await service.start({host:'127.0.0.1',port:0,user:'localls',root,privateKey:host,devices:[{...pub,key:undefined}]});const code=service.invite('127.0.0.1');await client.connect({...code,folder:'/'},{method:'key',privateKey:key.privateKey},code.pin);
 for(const state of [1,0,-1]){charging=state;const value=await client.stats();assert.equal(value.battery,'63%');assert.equal(value.freeBytes,1024);assert.equal(value.totalBytes,4096);assert.equal(value.charging,state);}
 }finally{client.disconnect();await service.stop();fs.rmSync(folder,{recursive:true,force:true});}
});
test('legacy v1 status retains capacity and marks charging as unknown without exec',async()=>{
 const f=await fixture(),client=new Server();try{await client.connect(f.profile,f.password,f.fingerprint);f.setTelemetry('localls-status-v1\n73\n1024\n8192\n3660\n');const value=await client.stats();assert.equal(value.totalBytes,8192);assert.equal(value.charging,-1);assert.equal(value.battery,'73%');assert.equal(f.execAttempts,0);}finally{client.disconnect();await f.close();}
});
async function stalledExec(acceptCommand,telemetry){
 const folder=fs.mkdtempSync(path.join(os.tmpdir(),'localls-stats-')),share=new SharedFiles(folder),clients=new Set(),entered=barrier();let execAttempts=0;const key=crypto.generateKeyPairSync('rsa',{modulusLength:2048,privateKeyEncoding:{type:'pkcs1',format:'pem'},publicKeyEncoding:{type:'spki',format:'pem'}}).privateKey;
 const pin=fingerprint(require('ssh2').utils.parseKey(key).getPublicSSH());
 const server=new SSHServer({hostKeys:[key]},c=>{clients.add(c);c.on('error',()=>{});c.on('close',()=>clients.delete(c));c.on('authentication',ctx=>ctx.method==='password'?ctx.accept():ctx.reject(['password']));c.on('ready',()=>c.on('session',accept=>{const session=accept();session.on('sftp',accept=>serveFiles(accept(),share));session.on('subsystem',(accept,reject,info)=>{const value=telemetry?.(info.name);if(value===undefined)return reject();const stream=accept();stream.resume();stream.end(value);});session.on('exec',(accept,reject)=>{execAttempts++;acceptCommand?.(accept,reject);entered.resolve();});}));});
 await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'127.0.0.1',resolve);});const client=new Server();
 await client.connect({host:'127.0.0.1',port:server.address().port,user:'audit',folder:'/'},{method:'password',password:crypto.randomBytes(8).toString('hex')},pin);
 return {client,entered:entered.promise,get execAttempts(){return execAttempts;},close:async()=>{client.disconnect();for(const c of clients)c._sock.destroy();await new Promise(resolve=>server.close(resolve));fs.rmSync(folder,{recursive:true,force:true});}};
}
test('real SSH statistics timeout covers a server that never accepts the exec request',async()=>{
 let acceptLate,requests=0;
 const reply=accept=>{const channel=accept();channel.resume();channel.exit(0);channel.end('up 2 days\nFilesystem 1024-blocks Used Available Capacity Mounted on\nfixture 100 40 60 40% /\n');return channel;};
 const h=await stalledExec(accept=>{if(++requests===1)acceptLate=accept;else reply(accept);});
 try{const result=assert.rejects(bounded(h.client.stats()),e=>e.message==='TIMEOUT');await bounded(h.entered);await result;
 assert.equal(h.execAttempts,1);await assert.rejects(bounded(h.client.stats(),250),e=>e.message==='TIMEOUT');assert.equal(h.execAttempts,1,'Repeated refresh must not leak another pending SSH channel');
 assert.equal((await h.client.list('/')).path,'/','Statistics timeout must leave working SFTP intact');
 const late=reply(acceptLate);await bounded(new Promise(resolve=>late.once('close',resolve)),2000);
 const fresh=await h.client.stats();assert.match(fresh.uptime,/up 2 days/);assert.equal(fresh.totalBytes,100*1024);assert.equal(h.execAttempts,2);
 }finally{await h.close();}
});
test('disconnect during legacy stats rejects instead of publishing stale metrics',async()=>{
 const h=await stalledExec(accept=>{const stream=accept();stream.write('old server metrics\n');});
 try{const result=assert.rejects(bounded(h.client.stats(),2000),e=>e.message==='NOT_CONNECTED');await bounded(h.entered);h.client.disconnect();await result;}finally{await h.close();}
});

test('malformed v2 metrics fall back to validated v1 without inventing charging data',async()=>{
 const bad=['localls-status-v2\n50\n1\n2\n0\n2\n','localls-status-v2\n50\n3\n2\n0\n1\n','localls-status-v2\n50\n1\n9007199254740992\n0\n1\n','localls-status-v2\n50\n1\n2\n0\ntrue\n','localls-status-v1\n50\n1\n2\n0\n'];let value=bad[0];
 const h=await stalledExec(()=>assert.fail('Valid legacy telemetry must not require exec'),protocol=>protocol==='localls-status-v2'?value:'localls-status-v1\n0\n1024\n4096\n60\n');
 try{for(const frame of bad){value=frame;const stats=await h.client.stats();assert.equal(stats.battery,'0%');assert.equal(stats.totalBytes,4096);assert.equal(stats.freeBytes,1024);assert.equal(stats.charging,-1);}assert.equal(h.execAttempts,0);}finally{await h.close();}
});
