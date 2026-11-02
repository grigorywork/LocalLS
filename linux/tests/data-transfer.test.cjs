'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),os=require('node:os'),crypto=require('node:crypto');
const {uploadData,downloadData}=require('../src/data-transfer.cjs');
function channel(data,{partial=false,closeError=false,abort}={}){
 let pending=0,max=0,closed=0;const target=Buffer.alloc(data.length);
 const rpc=(offset,done)=>{pending++;max=Math.max(max,pending);setTimeout(()=>{pending--;done();},(7-Math.floor(offset/32768)%8)*2+1);};
 return {target,get max(){return max;},get closed(){return closed;},open:(_p,...args)=>args.at(-1)(null,Buffer.from('h')),read:(_h,buf,off,len,pos,cb)=>rpc(pos,()=>{const n=Math.min(partial?997:len,len,data.length-pos);data.copy(buf,off,pos,pos+n);cb(null,n);}),write:(_h,buf,off,len,pos,cb)=>rpc(pos,()=>{buf.copy(target,pos,off,off+len);abort?.();cb();}),close:(_h,cb)=>{assert.equal(pending,0,'No CLOSE before outstanding blocks have settled');closed++;cb(closeError?new Error('CLOSE_FAIL'):null);}};
}
async function files(){const dir=fs.mkdtempSync(path.join(os.tmpdir(),'localls-window-')),source=path.join(dir,'source'),target=path.join(dir,'target'),bytes=crypto.randomBytes(900123);fs.writeFileSync(source,bytes);return {dir,source,target,bytes,close:()=>fs.rmSync(dir,{recursive:true,force:true})};}
test('windowed download preserves ordered bytes with short reads and out-of-order replies, bounded to eight requests',async()=>{
 const h=await files(),s=channel(h.bytes,{partial:true}),output=await fs.promises.open(h.target,'wx');let bytes=0;
 try{await downloadData(s,'/source',output,h.bytes.length,new AbortController().signal,n=>bytes+=n);await output.close();assert.deepEqual(fs.readFileSync(h.target),h.bytes);assert.equal(bytes,h.bytes.length);assert.ok(s.max>1&&s.max<=8);assert.equal(s.closed,1);}finally{await output.close().catch(()=>{});h.close();}
});
test('windowed upload preserves every offset with out-of-order acknowledgements and closes after all writes',async()=>{
 const h=await files(),s=channel(h.bytes);let bytes=0;
 try{await uploadData(s,h.source,'/temporary',h.bytes.length,new AbortController().signal,n=>bytes+=n);assert.deepEqual(s.target,h.bytes);assert.equal(bytes,h.bytes.length);assert.ok(s.max>1&&s.max<=8);assert.equal(s.closed,1);}finally{h.close();}
});
test('a failed remote CLOSE still closes the local upload descriptor',async()=>{
 const h=await files(),s=channel(h.bytes,{closeError:true}),open=fs.promises.open;let closed=0;
 fs.promises.open=async(...args)=>{const handle=await open(...args),close=handle.close.bind(handle);handle.close=async()=>{closed++;return close();};return handle;};
 try{await assert.rejects(uploadData(s,h.source,'/temporary',h.bytes.length,new AbortController().signal,()=>{}),/CLOSE_FAIL/);assert.equal(closed,1);}finally{fs.promises.open=open;h.close();}
});
test('aborting a window waits for its replies and sends no following window',async()=>{
 const h=await files(),controller=new AbortController(),s=channel(h.bytes,{abort:()=>controller.abort()});let bytes=0;
 try{await assert.rejects(uploadData(s,h.source,'/temporary',h.bytes.length,controller.signal,n=>bytes+=n),e=>e.name==='AbortError');assert.equal(bytes,8*32768);assert.equal(s.closed,1);assert.ok(s.target.subarray(8*32768).every(n=>n===0));}finally{h.close();}
});

test('a peer returning more bytes than requested cannot publish downloaded data',async()=>{
 const h=await files(),s=channel(h.bytes),output=await fs.promises.open(h.target,'wx');s.read=(_h,_buf,_off,len,_pos,cb)=>cb(null,len+1);
 try{await assert.rejects(downloadData(s,'/source',output,h.bytes.length,new AbortController().signal,()=>{}),/SOURCE_CHANGED/);assert.equal(fs.statSync(h.target).size,0);assert.equal(s.closed,1);}finally{await output.close();h.close();}
});
