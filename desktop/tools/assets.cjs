'use strict';
const fs=require('node:fs');const {Resvg}=require('@resvg/resvg-js');const svg=fs.readFileSync('assets/logo.svg');
const render=size=>Buffer.from(new Resvg(svg,{fitTo:{mode:'width',value:size}}).render().asPng());
fs.writeFileSync('assets/icon.png',render(512));
const sizes=[256,128,64,48,32,16],images=sizes.map(render),header=Buffer.alloc(6+16*sizes.length);header.writeUInt16LE(1,2);header.writeUInt16LE(sizes.length,4);let offset=header.length;
sizes.forEach((size,i)=>{const o=6+i*16;header[o]=size===256?0:size;header[o+1]=header[o];header.writeUInt16LE(1,o+4);header.writeUInt16LE(32,o+6);header.writeUInt32LE(images[i].length,o+8);header.writeUInt32LE(offset,o+12);offset+=images[i].length;});fs.writeFileSync('assets/icon.ico',Buffer.concat([header,...images]));
