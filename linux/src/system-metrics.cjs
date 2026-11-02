'use strict';
const fs=require('node:fs'),path=require('node:path');
class LinuxBattery {
 constructor({root='/sys/class/power_supply',now=Date.now}={}){this.root=root;this.now=now;this.next=0;this.value={battery:-1,charging:-1};}
 read(){
  if(this.now()<this.next)return {...this.value};this.next=this.now()+30000;let battery=-1,charging=-1,online=-1;
  try{for(const name of fs.readdirSync(this.root).slice(0,64)){
   const read=key=>{try{return fs.readFileSync(path.join(this.root,name,key),'utf8').trim();}catch{return '';}};
   const type=read('type');if(type==='Battery'&&battery<0){const raw=read('capacity'),n=/^\d{1,3}$/.test(raw)?Number(raw):-1;if(n>=0&&n<=100)battery=n;const status=read('status');if(['Charging','Full'].includes(status))charging=1;else if(status==='Discharging')charging=0;}
   else if(['Mains','USB','USB_C','USB_PD'].includes(type)){const state=read('online');if(state==='1')online=1;else if(state==='0'&&online<0)online=0;}
  }}catch{}if(charging<0)charging=online;this.value={battery,charging};return {...this.value};
 }
}
module.exports={LinuxBattery};
