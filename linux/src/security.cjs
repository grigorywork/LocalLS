'use strict';
const crypto=require('node:crypto'),fs=require('node:fs'),tls=require('node:tls'),https=require('node:https'),http=require('node:http'),path=require('node:path');
const {utils}=require('ssh2');
const call=(s,m,...a)=>new Promise((resolve,reject)=>s[m](...a,(e,v)=>e?reject(e):resolve(v)));
function fail(code){throw new Error(code);}
function inspectIdentity(privateKey,passphrase=''){
 if(typeof privateKey!=='string'||Buffer.byteLength(privateKey)>128*1024||typeof passphrase!=='string'||passphrase.length>4096)fail('KEY_INVALID');
 let parsed;try{parsed=utils.parseKey(privateKey,passphrase);}catch{fail('KEY_INVALID');}
 if(Array.isArray(parsed)){if(parsed.length!==1)fail('KEY_INVALID');parsed=parsed[0];}
 if(!parsed||parsed instanceof Error||!parsed.isPrivateKey())fail('KEY_INVALID');
 if(!['ssh-rsa','ssh-ed25519','ecdsa-sha2-nistp256','ecdsa-sha2-nistp384','ecdsa-sha2-nistp521'].includes(parsed.type))fail('KEY_UNSUPPORTED');
 const raw=parsed.getPublicSSH();
 if(parsed.type==='ssh-rsa'){let offset=0;const fields=[];while(offset<raw.length){const n=raw.readUInt32BE(offset);fields.push(raw.subarray(offset+4,offset+4+n));offset+=4+n;}const n=fields[2];let i=0;while(i<n.length&&n[i]===0)i++;const bits=(n.length-i-1)*8+(i<n.length?32-Math.clz32(n[i]):0);if(bits<3072)fail('KEY_WEAK');}
 return {publicKey:parsed.type+' '+raw.toString('base64')+' LocalLS',fingerprint:'SHA256:'+crypto.createHash('sha256').update(raw).digest('base64').replace(/=+$/,'')};
}
function generateIdentity(){return new Promise((resolve,reject)=>utils.generateKeyPair('rsa',{bits:3072,comment:'LocalLS'},(e,key)=>{if(e)return reject(new Error('KEY_GENERATION_FAILED'));resolve({privateKey:key.private,passphrase:'',...inspectIdentity(key.private)});}));}
async function installPublicKey(sftp,publicKey){
 if(typeof publicKey!=='string'||!/^ssh-(?:rsa|ed25519) [A-Za-z0-9+/=]+ LocalLS$|^ecdsa-sha2-nistp(?:256|384|521) [A-Za-z0-9+/=]+ LocalLS$/.test(publicKey))fail('KEY_INVALID');
 const home=await call(sftp,'realpath','.'),dir=path.posix.join(home,'.ssh'),file=path.posix.join(dir,'authorized_keys');
 let st;try{st=await call(sftp,'lstat',dir);if(!st.isDirectory()||st.isSymbolicLink())fail('KEY_REMOTE_UNSAFE');}catch(e){if(e.code!==2)throw e;await call(sftp,'mkdir',dir,{mode:0o700});}
 await call(sftp,'chmod',dir,0o700);
 try{st=await call(sftp,'lstat',file);if(!st.isFile()||st.isSymbolicLink()||st.size>512*1024)fail('KEY_REMOTE_UNSAFE');}catch(e){if(e.code!==2)throw e;st=null;}
 const old=st?await call(sftp,'readFile',file):Buffer.alloc(0);const fields=publicKey.split(' ');const match=old.toString('utf8').split(/\r?\n/).some(line=>{const p=line.trim().split(/\s+/);return !line.trim().startsWith('#')&&p.some((v,i)=>v===fields[0]&&p[i+1]===fields[1]);});
 if(!match)await call(sftp,'appendFile',file,Buffer.from((old.length&&!old.toString('utf8').endsWith('\n')?'\n':'')+publicKey+'\n'),{mode:0o600});
 await call(sftp,'chmod',file,0o600);return true;
}
function agentEndpoint(host,port){if(typeof host!=='string'||! /^[a-zA-Z0-9][a-zA-Z0-9.:-]{0,252}$/.test(host)||!Number.isInteger(port)||port<1||port>65535)fail('AGENT_INVALID');return host.toLowerCase()+':'+port;}
function probeAgent(host,port){agentEndpoint(host,port);return new Promise((resolve,reject)=>{const socket=tls.connect({host,port,minVersion:'TLSv1.2',rejectUnauthorized:false});const timer=setTimeout(()=>{socket.destroy();reject(new Error('AGENT_TLS_FAILED'));},5000);socket.once('error',()=>{clearTimeout(timer);reject(new Error('AGENT_TLS_FAILED'));});socket.once('secureConnect',()=>{try{const cert=socket.getPeerCertificate();if(!cert.raw||cert.raw.length>16384)fail('AGENT_TLS_FAILED');const fingerprint='SHA256:'+crypto.createHash('sha256').update(cert.raw).digest('hex');const certificate='-----BEGIN CERTIFICATE-----\n'+cert.raw.toString('base64').match(/.{1,64}/g).join('\n')+'\n-----END CERTIFICATE-----\n';resolve({fingerprint,certificate});}catch{reject(new Error('AGENT_TLS_FAILED'));}finally{clearTimeout(timer);socket.destroy();}});});}
function agentRequest({host,port,token,action,pin,legacyHTTP=false}){
 agentEndpoint(host,port);if(!['status','start','stop','restart'].includes(action)||typeof token!=='string'||!token||token.length>4096||/[\r\n\x00]/.test(token))fail('AGENT_INVALID');
 if(!legacyHTTP&&(!pin||typeof pin.certificate!=='string'||!/^SHA256:[a-f0-9]{64}$/.test(pin.fingerprint)))fail('AGENT_TRUST_REQUIRED');
 return new Promise((resolve,reject)=>{const transport=legacyHTTP?http:https;const opts={host,port,path:'/'+action,method:action==='status'?'GET':'POST',headers:{Authorization:'Bearer '+token,Accept:'application/json'},timeout:8000};
 if(!legacyHTTP)Object.assign(opts,{minVersion:'TLSv1.2',rejectUnauthorized:true,ca:pin.certificate,checkServerIdentity:(_host,cert)=>{const observed='SHA256:'+crypto.createHash('sha256').update(cert.raw).digest('hex');if(observed!==pin.fingerprint)return new Error('AGENT_CERT_CHANGED');}});
 const req=transport.request(opts,res=>{let bytes=0,parts=[];res.on('data',chunk=>{bytes+=chunk.length;if(bytes>65536){req.destroy(new Error('AGENT_RESPONSE_LARGE'));return;}parts.push(chunk);});res.on('error',()=>reject(new Error('AGENT_FAILED')));res.on('end',()=>{if(res.statusCode!==200)return reject(new Error('AGENT_FAILED'));try{const data=JSON.parse(Buffer.concat(parts));resolve({ok:data.ok===true,running:data.sshd==='running',message:action==='status'?'Статус получен':'Команда отправлена агенту'});}catch{reject(new Error('AGENT_FAILED'));}});});
 req.on('timeout',()=>req.destroy(new Error('AGENT_FAILED')));req.on('error',()=>reject(new Error(legacyHTTP?'AGENT_FAILED':'AGENT_TLS_FAILED')));req.end();
 });
}
module.exports={inspectIdentity,generateIdentity,installPublicKey,agentEndpoint,probeAgent,agentRequest};
