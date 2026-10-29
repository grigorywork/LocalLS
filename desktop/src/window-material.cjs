'use strict';
const os=require('node:os');
function supportsAcrylic(platform=process.platform,release=os.release()){
 const parts=/^(\d+)\.(\d+)\.(\d+)/.exec(release);
 return platform==='win32'&&!!parts&&(Number(parts[1])>10||Number(parts[1])===10&&Number(parts[3])>=22621);
}
function opaqueBackground(settings){return settings.light?'#f3f4f6':'#121019';}
function windowOptions(settings,environment){
 const native=settings.glass===true&&supportsAcrylic(environment?.platform,environment?.release);
 return {backgroundColor:native?'#00000000':opaqueBackground(settings),...(supportsAcrylic(environment?.platform,environment?.release)?{backgroundMaterial:native?'acrylic':'none'}:{})};
}
function applyWindowMaterial(window,settings,environment){
 if(!window||window.isDestroyed())return false;
 let native=false;
 if(supportsAcrylic(environment?.platform,environment?.release)){
  try{window.setBackgroundMaterial(settings.glass?'acrylic':'none');native=settings.glass===true;}
  catch{try{window.setBackgroundMaterial('none');}catch{}}
 }
 window.setBackgroundColor(native?'#00000000':opaqueBackground(settings));return native;
}
module.exports={supportsAcrylic,windowOptions,applyWindowMaterial};
