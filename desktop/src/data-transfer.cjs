'use strict';
const fs=require('node:fs');
const {call}=require('./sftp-channel.cjs');
const BLOCK=32768,WINDOW=8;
function check(signal){if(signal.aborted)throw Object.assign(new Error('CANCELLED'),{name:'AbortError'});}
async function batch(promises){const results=await Promise.allSettled(promises);const error=results.find(r=>r.status==='rejected');if(error)throw error.reason;return results.map(r=>r.value);}
async function readBlock(sftp,handle,offset,length,signal,progress){
 const buffer=Buffer.allocUnsafe(length);let filled=0;
 while(filled<length){check(signal);const n=await call(sftp,'read',handle,buffer,filled,length-filled,offset+filled);if(!Number.isSafeInteger(n)||n<=0||n>length-filled)throw new Error('SOURCE_CHANGED');filled+=n;progress(n);}
 return {buffer,offset};
}
async function downloadData(sftp,remote,output,size,signal,progress){
 let handle;
 try{check(signal);handle=await call(sftp,'open',remote,'r');
  for(let position=0;position<size;position+=BLOCK*WINDOW){
   check(signal);const reads=[];
   for(let i=0;i<WINDOW&&position+i*BLOCK<size;i++){const offset=position+i*BLOCK;reads.push(readBlock(sftp,handle,offset,Math.min(BLOCK,size-offset),signal,progress));}
   const blocks=await batch(reads);check(signal);
   for(const {buffer,offset} of blocks){let written=0;while(written<buffer.length){check(signal);const {bytesWritten}=await output.write(buffer,written,buffer.length-written,offset+written);if(!bytesWritten)throw new Error('WRITE_FAILED');written+=bytesWritten;}}
  }
 }finally{if(handle)await call(sftp,'close',handle);}
}
async function uploadData(sftp,local,remote,size,signal,progress){
 let input,handle;
 try{check(signal);input=await fs.promises.open(local,'r');handle=await call(sftp,'open',remote,'wx',0o600);
  for(let position=0;position<size;position+=BLOCK*WINDOW){
   check(signal);const blocks=[];
   for(let i=0;i<WINDOW&&position+i*BLOCK<size;i++){
    check(signal);const offset=position+i*BLOCK,length=Math.min(BLOCK,size-offset),buffer=Buffer.allocUnsafe(length);let filled=0;
    while(filled<length){const {bytesRead}=await input.read(buffer,filled,length-filled,offset+filled);if(!bytesRead)throw new Error('SOURCE_CHANGED');filled+=bytesRead;}
    blocks.push({buffer,offset,length});
   }
   check(signal);await batch(blocks.map(({buffer,offset,length})=>call(sftp,'write',handle,buffer,0,length,offset).then(()=>progress(length))));
  }
  check(signal);if((await input.stat()).size!==size)throw new Error('SOURCE_CHANGED');
 }finally{try{if(handle)await call(sftp,'close',handle);}finally{await input?.close();}}
}
module.exports={downloadData,uploadData};
