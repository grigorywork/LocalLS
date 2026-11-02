'use strict';
class QueueRate {
 constructor(now=()=>performance.now()){this.now=now;this.started=null;this.buckets=new Map();}
 begin(){this.started=this.now();this.buckets.clear();}
 record(bytes){const bucket=Math.floor(this.now()/250);this.buckets.set(bucket,(this.buckets.get(bucket)||0)+bytes);for(const key of this.buckets.keys())if(key<bucket-40)this.buckets.delete(key);}
 snapshot(){
  if(this.started===null)return {speed:0,ready:false};
  const now=this.now(),bucket=Math.floor(now/250),elapsed=(now-Math.max(this.started,(bucket-39)*250))/1000;
  let bytes=0;for(const [key,value] of this.buckets)if(key>=bucket-39&&key<=bucket)bytes+=value;
  return {speed:elapsed>=1?bytes/elapsed:0,ready:now-this.started>=1000};
 }
}
function queueEstimate(items,rate,clearing=false){
 const active=items.filter(j=>['queued','running'].includes(j.state));let remaining=0,unknown=0;
 for(const job of active){const size=job.directory?0:[job.total,job.sourceSnapshot?.size,job.remoteSnapshot?.size].find(n=>Number.isSafeInteger(n)&&n>=0);
  if(size===undefined){unknown++;continue;}const left=Math.max(0,size-(Number.isSafeInteger(job.bytes)&&job.bytes>0?job.bytes:0));if(Number.isSafeInteger(remaining+left))remaining+=left;else unknown++;
 }
 const current=active.find(j=>j.state==='running'),running=active.filter(j=>j.state==='running').length;
 const speed=Number.isFinite(rate.speed)&&rate.speed>0?rate.speed:0;
 const status=clearing?'cancelling':!active.length?'idle':!running?'preparing':unknown?'unknown':!remaining?'finishing':!rate.ready?'estimating':speed>0?'transferring':'waiting';
 const seconds=status==='transferring'?remaining/speed:null;
 return {pending:active.length,records:items.length,remainingBytes:unknown?null:remaining,knownRemainingBytes:remaining,unknownFiles:unknown,speed,etaSeconds:Number.isFinite(seconds)&&seconds>=0?seconds:null,status,
 current:current?{label:String(current.label||''),bytes:current.bytes||0,total:current.total??current.sourceSnapshot?.size??current.remoteSnapshot?.size??null,direction:current.direction}:null};
}
module.exports={QueueRate,queueEstimate};
