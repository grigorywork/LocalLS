'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),os=require('node:os'),crypto=require('node:crypto');
const {EmbeddedServer,publicIdentity}=require('../src/embedded-server.cjs'),{Server,Transfers}=require('../src/core.cjs'),{FilePreview,previewConnection,LIMIT}=require('../src/file-preview.cjs'),{identity}=require('./ed25519-fixture.cjs');
const delay=ms=>new Promise(r=>setTimeout(r,ms));
async function bounded(p,ms=8000){let timer;try{return await Promise.race([p,new Promise((_,reject)=>timer=setTimeout(()=>reject(new Error('PREVIEW_TEST_BOUND')),ms))]);}finally{clearTimeout(timer);}}
async function fixture(){
 const folder=fs.mkdtempSync(path.join(os.tmpdir(),'localls-preview-')),root=path.join(folder,'share'),cache=path.join(folder,'cache');fs.mkdirSync(root);
 const key=identity(),host=crypto.generateKeyPairSync('rsa',{modulusLength:3072,privateKeyEncoding:{format:'pem',type:'pkcs1'},publicKeyEncoding:{format:'pem',type:'spki'}}).privateKey;
 const server=new EmbeddedServer(),client=new Server(),preview=new FilePreview(cache);
 await server.start({host:'127.0.0.1',port:0,user:'localls',root,privateKey:host,devices:[{...publicIdentity(key.publicKey),key:undefined}]});
 const code=server.invite('127.0.0.1');await client.connect({...code,folder:'/'},{method:'key',privateKey:key.privateKey},code.pin);
 const connect=signal=>previewConnection({...code,folder:'/'},{method:'key',privateKey:key.privateKey},code.pin,signal);
 return {folder,root,cache,server,client,preview,connect,requestId:()=>crypto.randomUUID(),close:async()=>{await preview.destroy();client.disconnect();await server.stop();fs.rmSync(folder,{recursive:true,force:true});}};
}
function drained(q){return bounded(new Promise(resolve=>{if(!q.running)return resolve();q.on('active',active=>{if(!active)resolve();});}));}
for(const clear of [false,true])test((clear?'clearing':'cancelling')+' all covers a 1000-job queue midstream without publishing files or starting queued jobs',async()=>{
 const h=await fixture();try{
  const source=path.join(h.folder,'source.bin'),bytes=crypto.randomBytes(8*1024*1024);fs.writeFileSync(source,bytes);const queue=new Transfers(h.client);let reached;
  const barrier=new Promise(r=>reached=r);let seen=false;h.server.on('traffic',event=>{if(!seen&&event.direction==='received'&&h.server.received>=128*1024){seen=true;reached();}});
  queue.add(Array.from({length:1000},(_,i)=>({direction:'upload',local:source,remote:'/file-'+i+'.bin',label:'file-'+i,overwrite:false})));
  await bounded(barrier);let clearing;
  if(clear){clearing=queue.clearAll();assert.throws(()=>queue.add([]),/BUSY/);await bounded(clearing);assert.equal(queue.items.length,0);}
  else {assert.equal(queue.cancelAll(),1000);await drained(queue);assert.equal(queue.items.length,1000);assert.ok(queue.items.every(j=>j.state==='cancelled'));assert.ok(queue.items.slice(1).every(j=>!j.started));}
  assert.equal(queue.running,false);assert.deepEqual(fs.readdirSync(h.root),[]);assert.deepEqual(fs.readFileSync(source),bytes);
 }finally{await h.close();}
});
test('clearing keeps recoverable history when the durable empty journal cannot be written',async()=>{
 const q=new Transfers({});const record={id:'old',state:'failed',label:'preserve',local:'/source',remote:'/destination'};q.items=[record];q.beforeEnqueue=()=>{throw new Error('QUEUE_STORE');};await assert.rejects(q.clearAll(),/QUEUE_STORE/);assert.deepEqual(q.items,[record]);assert.equal(q.clearing,false);
 let saved;q.beforeEnqueue=items=>saved=items;assert.equal(await q.clearAll(),1);assert.deepEqual(saved,[]);assert.deepEqual(q.items,[]);
});
test('remote preview preserves bytes, supplies bounded HTTP ranges and revokes capability on close without touching SFTP',async()=>{
 const h=await fixture();try{
  const bytes=crypto.randomBytes(256*1024);fs.writeFileSync(path.join(h.root,'photo.png'),bytes);const requestId=h.requestId();
  const result=await h.preview.open({requestId,name:'photo.png',remote:'/photo.png',connect:h.connect});assert.equal(result.mime,'image/png');
  let reply=h.preview.response(new Request(result.url));assert.equal(reply.status,200);assert.deepEqual(Buffer.from(await reply.arrayBuffer()),bytes);
  reply=h.preview.response(new Request(result.url,{headers:{Range:'bytes=50-99'}}));assert.equal(reply.status,206);assert.equal(reply.headers.get('content-range'),'bytes 50-99/'+bytes.length);assert.deepEqual(Buffer.from(await reply.arrayBuffer()),bytes.subarray(50,100));
  reply=h.preview.response(new Request(result.url,{headers:{Range:'bytes=-5'}}));assert.deepEqual(Buffer.from(await reply.arrayBuffer()),bytes.subarray(-5));
  assert.equal(h.preview.response(new Request(result.url,{method:'HEAD'})).headers.get('content-length'),String(bytes.length));
  for(const range of ['bytes=9999999-','bytes=9-2','bytes=-0','bytes=0-1,4-5','bytes=a-b'])assert.equal(h.preview.response(new Request(result.url,{headers:{Range:range}})).status,416);
  assert.equal(h.preview.response(new Request(result.url,{method:'POST'})).status,405);assert.equal(h.preview.response(new Request(result.url+'?other')).status,404);
  assert.equal(h.preview.close(requestId),true);await h.preview.destroy();assert.equal(h.preview.response(new Request(result.url)).status,404);assert.deepEqual(fs.readdirSync(h.cache),[]);
  assert.equal((await h.client.list('/')).entries[0].name,'photo.png');assert.deepEqual(fs.readFileSync(path.join(h.root,'photo.png')),bytes);
 }finally{await h.close();}
});
test('local preview refuses active document types, symlinks and oversized files without reading or modifying them',async()=>{
 const h=await fixture();try{
  const png=path.join(h.folder,'photo.png');fs.writeFileSync(png,'local bytes');const linked=path.join(h.folder,'linked.png');fs.symlinkSync(png,linked);
  for(const name of ['run.html','image.svg','program.exe'])await assert.rejects(h.preview.open({requestId:h.requestId(),name,local:png}),/PREVIEW_FORMAT/);
  await assert.rejects(h.preview.open({requestId:h.requestId(),name:'linked.png',local:linked}),/PREVIEW_FORMAT/);
  const huge=path.join(h.folder,'large.mp4');fs.writeFileSync(huge,'');fs.truncateSync(huge,LIMIT+1);await assert.rejects(h.preview.open({requestId:h.requestId(),name:'large.mp4',local:huge}),/PREVIEW_LARGE/);assert.equal(fs.statSync(huge).size,LIMIT+1);
  const result=await h.preview.open({requestId:h.requestId(),name:'photo.png',local:png});assert.equal(await h.preview.response(new Request(result.url)).text(),'local bytes');assert.equal(fs.readFileSync(png,'utf8'),'local bytes');
 }finally{await h.close();}
});
test('releasing before the remote channel callback cancels preparation; an old close cannot erase a newer preview',async()=>{
 const h=await fixture();try{
  const png=path.join(h.folder,'new.png');fs.writeFileSync(png,'new preview');let arrived,release;const ready=new Promise(r=>arrived=r);
  const requestId=h.requestId();const outcome=h.preview.open({requestId,name:'old.png',remote:'/old.png',connect:signal=>{arrived();return new Promise((resolve,reject)=>{signal.addEventListener('abort',()=>reject(Object.assign(new Error('PREVIEW_CANCELLED'),{name:'AbortError'})),{once:true});release=()=>h.connect(signal).then(resolve,reject);});}}).then(()=>null,error=>error);
  await ready;h.preview.close(requestId);assert.match((await bounded(outcome)).message,/PREVIEW_CANCELLED/);
  const next=await h.preview.open({requestId:h.requestId(),name:'new.png',local:png});release();await delay(60);
  assert.equal(h.preview.close(requestId),false);assert.equal(await h.preview.response(new Request(next.url)).text(),'new preview');assert.equal((await h.client.list('/')).path,'/');
 }finally{await h.close();}
});
test('connection loss while previewing deletes partial cache and never writes into server files',async()=>{
 const h=await fixture();let stopped;try{
  const bytes=crypto.randomBytes(8*1024*1024);fs.writeFileSync(path.join(h.root,'movie.mp4'),bytes);let cut=false;
  h.server.on('traffic',event=>{if(!cut&&event.direction==='sent'&&h.server.sent>=128*1024){cut=true;stopped=h.server.stop();}});
  await assert.rejects(bounded(h.preview.open({requestId:h.requestId(),name:'movie.mp4',remote:'/movie.mp4',connect:h.connect}),12000));assert.ok(cut);await stopped;
  assert.deepEqual(fs.readdirSync(h.cache),[]);assert.deepEqual(fs.readFileSync(path.join(h.root,'movie.mp4')),bytes);
 }finally{await h.close();}
});
test('closing a preview with a silent peer drains its private connection and cache while the file manager keeps working',async()=>{
 const h=await fixture();let peer;try{
  fs.writeFileSync(path.join(h.root,'silent.mp4'),crypto.randomBytes(16*1024*1024));
  h.server.instance.on('connection',client=>{peer=client._sock;});let reached;const barrier=new Promise(r=>reached=r);let paused=false;
  h.server.on('traffic',event=>{if(!paused&&event.direction==='sent'&&h.server.sent>=128*1024){paused=true;peer.pause();reached();}});
  const requestId=h.requestId(),outcome=h.preview.open({requestId,name:'silent.mp4',remote:'/silent.mp4',connect:h.connect}).then(()=>null,e=>e);
  await bounded(barrier);const original=h.client.client;assert.equal((await h.client.list('/')).entries[0].name,'silent.mp4');
  h.preview.close(requestId);assert.ok(await bounded(outcome,1500));await h.preview.destroy();assert.deepEqual(fs.readdirSync(h.cache),[]);
  assert.equal(h.client.client,original);assert.equal((await h.client.list('/')).entries[0].name,'silent.mp4');
 }finally{peer?.resume();await h.close();}
});
