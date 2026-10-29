'use strict';
const crypto=require('node:crypto');
const {profile}=require('./core.cjs');
const jsQR=require('jsqr');
function decode(text,now=Date.now()){
 if(typeof text!=='string'||text.length>12000)throw new Error('QR_INVALID');
 let code;try{code=JSON.parse(text);}catch{throw new Error('QR_INVALID');}
 const fields=['format','version','type','host','port','user','pin','nonce','expires'];
 if(!code||Array.isArray(code)||Object.keys(code).length!==fields.length||fields.some(k=>!Object.hasOwn(code,k))||code.format!=='LocalLS-pairing'||code.version!==1||code.type!=='server'||!Number.isInteger(code.port)||typeof code.host!=='string'||code.host!==code.host.trim()||typeof code.user!=='string'||code.user!==code.user.trim()||typeof code.pin!=='string'||!/^SHA256:[A-Za-z0-9+/]{43}$/.test(code.pin)||typeof code.nonce!=='string'||! /^[a-f0-9-]{36}$/.test(code.nonce)||!Number.isSafeInteger(code.expires))throw new Error('QR_INVALID');
 profile({...code,fingerprint:code.pin,folder:'.'});
 if(code.expires<=now||code.expires-now>600000)throw new Error('QR_EXPIRED');return code;
}
function normalized(publicKey){const parts=publicKey.trim().split(/\s+/);return parts[0]+' '+parts[1]+' LocalLS';}
function syncCode(code,publicKey){
 const transcript=['LocalLS-login-v1',code.host,code.port,code.user,code.pin,code.nonce,code.expires,normalized(publicKey)].join('\n');
 const hash=crypto.createHash('sha256').update(transcript,'utf8').digest();let value=0;for(let i=0;i<6;i++)value=value*256+hash[i];
 const digits=String(value%100000000).padStart(8,'0');return digits.slice(0,4)+' '+digits.slice(4);
}
function decodePixels(data,width,height){
 if(!Number.isInteger(width)||!Number.isInteger(height)||width<21||height<21||width*height>20000000||data.length!==width*height*4)throw new Error('QR_IMAGE_INVALID');
 const pixels=new Uint8ClampedArray(data);
 for(let i=0;i<pixels.length;i+=4){const alpha=pixels[i+3]/255;if(alpha<1){for(let c=0;c<3;c++)pixels[i+c]=pixels[i+c]*alpha+255*(1-alpha);pixels[i+3]=255;}}
 const result=jsQR(pixels,width,height,{inversionAttempts:'attemptBoth'});
 if(!result)throw new Error('QR_IMAGE_INVALID');return decode(result.data);
}
function imageCode(image){
 if(!image||image.isEmpty())throw new Error('QR_IMAGE_INVALID');const {width,height}=image.getSize(1);
 if(width*height>20000000)throw new Error('QR_IMAGE_INVALID');
 const bitmap=image.toBitmap({scaleFactor:1});for(let i=0;i<bitmap.length;i+=4){const blue=bitmap[i];bitmap[i]=bitmap[i+2];bitmap[i+2]=blue;}
 return decodePixels(bitmap,width,height);
}
function loginAttempt(code,identity,pairing=false,Client=require('ssh2').Client){
 return new Promise((resolve,reject)=>{
  const client=new Client();let done=false,mismatch=false;
  const end=(error,allowed)=>{if(done)return;done=true;clearTimeout(timer);client.destroy();error?reject(error):resolve(allowed);};
  const timer=setTimeout(()=>end(Object.assign(new Error('TIMEOUT'),{code:'ETIMEDOUT'})),8500);
  client.once('ready',()=>end(null,true));client.on('error',error=>{
   if(mismatch)return end(Object.assign(new Error('CHANGED'),{code:'HOST_KEY_CHANGED'}));
   if(error.level==='client-authentication')return end(null,false);end(error);
  });client.once('close',()=>{if(!done)end(new Error('CLOSED'));});
  client.connect({host:code.host,port:code.port,username:code.user+(pairing?'#'+code.nonce:''),privateKey:identity.privateKey,passphrase:identity.passphrase||'',authHandler:['publickey'],readyTimeout:8000,
   hostVerifier:key=>{mismatch='SHA256:'+crypto.createHash('sha256').update(key).digest('base64').replace(/=+$/,'')!==code.pin;return !mismatch;}});
 });
}
module.exports={decode,syncCode,decodePixels,imageCode,loginAttempt};
