'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {pipeline}=require('node:stream/promises'),{Readable}=require('node:stream');
const {call,protect}=require('./sftp-channel.cjs');
const {Server}=require('./core.cjs');
const LIMIT=128*1024*1024;
const types={png:'image/png',jpg:'image/jpeg',jpeg:'image/jpeg',webp:'image/webp',gif:'image/gif',bmp:'image/bmp',pdf:'application/pdf',mp4:'video/mp4',webm:'video/webm',mov:'video/quicktime',m4v:'video/mp4',mp3:'audio/mpeg',ogg:'audio/ogg',wav:'audio/wav',flac:'audio/flac',m4a:'audio/mp4'};
const aborted=()=>Object.assign(new Error('PREVIEW_CANCELLED'),{name:'AbortError'});
function bounded(promise,signal,ms=10000){
 return new Promise((resolve,reject)=>{
  if(signal.aborted)return reject(aborted());
  const stop=()=>finish(aborted()),timer=setTimeout(()=>finish(new Error('PREVIEW_TIMEOUT')),ms);
  const finish=(error,value)=>{clearTimeout(timer);signal.removeEventListener('abort',stop);error?reject(error):resolve(value);};
  signal.addEventListener('abort',stop,{once:true});promise.then(v=>finish(null,v),e=>finish(e));
 });
}
async function previewConnection(profile,credential,pin,signal){
 const server=new Server(),pending=server.connect(profile,credential,pin),client=server.client;
 const close=()=>{signal.removeEventListener('abort',close);client?.destroy();};
 signal.addEventListener('abort',close,{once:true});if(signal.aborted)close();
 try{await pending;if(signal.aborted)throw aborted();return {sftp:server.sftp,close};}catch(error){close();throw error;}
}
function range(header,size){
 if(!header)return {start:0,end:size-1,status:200};
 const m=/^bytes=(\d*)-(\d*)$/.exec(header);if(!m||(!m[1]&&!m[2])||!size)return null;
 let start=m[1]?Number(m[1]):Math.max(0,size-Number(m[2])),end=m[1]?(m[2]?Math.min(size-1,Number(m[2])):size-1):size-1;
 if(!Number.isSafeInteger(start)||!Number.isSafeInteger(end)||start<0||start>=size||end<start||(!m[1]&&Number(m[2])<=0))return null;
 return {start,end,status:206};
}
class FilePreview {
 constructor(root){this.root=root;this.active=null;this.cleanups=new Set();fs.mkdirSync(root,{recursive:true,mode:0o700});if(fs.lstatSync(root).isSymbolicLink())throw new Error('PREVIEW_FAILED');for(const entry of fs.readdirSync(root,{withFileTypes:true}))if(entry.isDirectory()&&/^view-[A-Za-z0-9]{6}$/.test(entry.name))fs.rmSync(path.join(root,entry.name),{recursive:true,force:true});}
 cleanup(token){
  const task=fs.promises.rm(token.folder,{recursive:true,force:true,maxRetries:10,retryDelay:50}).catch(()=>{});
  this.cleanups.add(task);task.finally(()=>this.cleanups.delete(task));return task;
 }
 close(requestId){
  const token=this.active;if(!token||requestId&&token.requestId!==requestId)return false;
  this.active=null;token.controller.abort();token.stopRemote?.();token.sftp?.destroy();for(const s of token.streams)s.destroy();
  if(token.ready)this.cleanup(token);return true;
 }
 async open({requestId,name,remote,local,connect,isCurrent=()=>true}){
  if(typeof requestId!=='string'||!/^[a-f0-9-]{36}$/.test(requestId))throw new Error('PREVIEW_FAILED');
  const mime=types[path.extname(name).slice(1).toLowerCase()];if(!mime)throw new Error('PREVIEW_FORMAT');
  this.close();const controller=new AbortController(),token={requestId,id:crypto.randomUUID(),controller,streams:new Set(),folder:fs.mkdtempSync(path.join(this.root,'view-')),ready:false};
  this.active=token;const signal=controller.signal,file=path.join(token.folder,'content');let channel,connection;
  const ensure=()=>{if(signal.aborted||this.active!==token||!isCurrent())throw aborted();};
  const timer=setTimeout(()=>controller.abort(),120000);let copied=0;
  try {
   let before,source;
   if(remote!==undefined){
    connection=await bounded(connect(signal),signal,15000);token.stopRemote=connection.close;channel=protect(connection.sftp);token.sftp=channel;ensure();
    before=await bounded(call(channel,'lstat',remote),signal);if(!before.isFile()||before.isSymbolicLink())throw new Error('PREVIEW_FORMAT');
    if(!Number.isSafeInteger(before.size)||before.size<0||before.size>LIMIT)throw new Error('PREVIEW_LARGE');
    source=channel.createReadStream(remote);
   }else{
    before=await fs.promises.lstat(local);if(!before.isFile()||before.isSymbolicLink())throw new Error('PREVIEW_FORMAT');
    if(before.size>LIMIT)throw new Error('PREVIEW_LARGE');source=fs.createReadStream(local);
   }
   const output=fs.createWriteStream(file,{flags:'wx',mode:0o600});token.streams.add(source);token.streams.add(output);
   let idle=setTimeout(()=>controller.abort(),10000);
   source.on('data',chunk=>{copied+=chunk.length;clearTimeout(idle);idle=setTimeout(()=>controller.abort(),10000);if(copied>LIMIT||copied>before.size)controller.abort();});
   try{await pipeline(source,output,{signal});}finally{clearTimeout(idle);token.streams.delete(source);token.streams.delete(output);}
   ensure();const after=channel?await bounded(call(channel,'lstat',remote),signal):await fs.promises.lstat(local);ensure();
   if(copied!==before.size||after.size!==before.size||(channel?after.mtime!==before.mtime:after.mtimeMs!==before.mtimeMs||after.ino!==before.ino||after.dev!==before.dev))throw new Error('PREVIEW_CHANGED');
   token.ready=true;token.file=file;token.size=copied;token.mime=mime;token.name=name;token.route='/'+token.id+'/'+encodeURIComponent(name);return {requestId,url:'localls-preview://file'+token.route,mime,name,size:copied};
  }catch(error){if(this.active===token)this.active=null;controller.abort();await this.cleanup(token);throw error;}
  finally{clearTimeout(timer);connection?.close();channel?.destroy();token.sftp=null;token.stopRemote=null;}
 }
 response(request){
  const token=this.active;let url;try{url=new URL(request.url);}catch{return new Response(null,{status:404});}
  if(!token?.ready||token.controller.signal.aborted||url.protocol!=='localls-preview:'||url.hostname!=='file'||url.pathname!==token.route||url.search||url.hash)return new Response(null,{status:404});
  if(!['GET','HEAD'].includes(request.method))return new Response(null,{status:405});
  const r=range(request.headers.get('range'),token.size);if(!r)return new Response(null,{status:416,headers:{'Content-Range':'bytes */'+token.size}});
  const headers={'Content-Type':token.mime,'Content-Disposition':"inline; filename*=UTF-8''"+encodeURIComponent(token.name).replace(/['()*]/g,c=>'%'+c.charCodeAt(0).toString(16)),'Content-Length':String(Math.max(0,r.end-r.start+1)),'Accept-Ranges':'bytes','Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Access-Control-Allow-Origin':'*'};
  if(r.status===206)headers['Content-Range']=`bytes ${r.start}-${r.end}/${token.size}`;
  if(request.method==='HEAD'||!token.size)return new Response(null,{status:r.status,headers});
  const stream=fs.createReadStream(token.file,{start:r.start,end:r.end});token.streams.add(stream);stream.on('close',()=>token.streams.delete(stream));return new Response(Readable.toWeb(stream),{status:r.status,headers});
 }
 async destroy(){this.close();await Promise.all([...this.cleanups]);}
}
module.exports={FilePreview,previewConnection,LIMIT};
