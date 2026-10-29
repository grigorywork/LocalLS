'use strict';
const fs=require('node:fs'),path=require('node:path');
const {unusedDxc}=require('../src/runtime-policy.cjs');
function imports(b){
 function range(offset,size){if(!Number.isSafeInteger(offset)||offset<0||offset+size>b.length)throw new Error('Invalid PE bounds');}
 range(0,64);if(b.toString('ascii',0,2)!=='MZ')throw new Error('Not a PE file');const pe=b.readUInt32LE(60);range(pe,24);if(b.readUInt32LE(pe)!==0x4550)throw new Error('Invalid PE signature');
 const count=b.readUInt16LE(pe+6),optionalSize=b.readUInt16LE(pe+20),opt=pe+24;range(opt,optionalSize);const magic=b.readUInt16LE(opt),directory=opt+(magic===0x20b?112:magic===0x10b?96:(()=>{throw new Error('Unknown PE format');})());range(directory+8,8);
 const sections=[];for(let i=0;i<count;i++){const o=opt+optionalSize+40*i;range(o,40);sections.push({va:b.readUInt32LE(o+12),size:Math.max(b.readUInt32LE(o+8),b.readUInt32LE(o+16)),raw:b.readUInt32LE(o+20)});}
 function offset(rva){if(rva<b.readUInt32LE(opt+60)){range(rva,1);return rva;}for(const s of sections)if(rva>=s.va&&rva<s.va+s.size){const value=s.raw+rva-s.va;range(value,1);return value;}throw new Error('Invalid PE RVA');}
 const start=b.readUInt32LE(directory+8),size=b.readUInt32LE(directory+12);if(!start)return [];
 const out=[],first=offset(start);for(let i=0;i<Math.min(Math.floor(size/20),4096);i++){const o=first+i*20;range(o,20);if(b.subarray(o,o+20).every(x=>x===0))return out;const name=offset(b.readUInt32LE(o+12)),end=b.indexOf(0,name);if(end<0||end-name>260)throw new Error('Invalid PE import name');out.push(b.toString('ascii',name,end).toLowerCase());}throw new Error('Unterminated PE imports');
}
function slimRuntime(dir){
 const removed=new Set(unusedDxc),files=fs.readdirSync(dir).filter(n=>/\.(exe|dll)$/i.test(n));
 // Fail closed before deleting anything if a retained binary requires a candidate.
 for(const name of files)if(!removed.has(name.toLowerCase()))for(const dependency of imports(fs.readFileSync(path.join(dir,name))))if(removed.has(dependency))throw new Error(name+' requires '+dependency);
 for(const name of ['LICENSES.chromium.html','LICENSE.electron.txt','d3dcompiler_47.dll','vulkan-1.dll','vk_swiftshader.dll'])if(!fs.existsSync(path.join(dir,name)))throw new Error('Required runtime file missing: '+name);
 const report=[];for(const name of unusedDxc){const file=path.join(dir,name);if(fs.existsSync(file)){const size=fs.statSync(file).size;fs.unlinkSync(file);report.push({name,bytes:size});}}
 return report;
}
module.exports={imports,slimRuntime};
