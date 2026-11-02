'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {QueueRate,queueEstimate}=require('../src/queue-estimate.cjs');
test('whole queue estimate covers all 200 jobs beyond the visible snapshot and both transfer directions',()=>{
 const jobs=Array.from({length:200},(_,i)=>({state:i?'queued':'running',label:'file-'+i,bytes:i?0:50,...(i%2?{remoteSnapshot:{size:100}}:{sourceSnapshot:{size:100}})}));
 const s=queueEstimate(jobs,{speed:100,ready:true});assert.equal(s.pending,200);assert.equal(s.remainingBytes,19950);assert.equal(s.etaSeconds,199.5);assert.equal(s.current.label,'file-0');
});
test('failed, completed, cancelled files and empty directories do not inflate remaining bytes',()=>{
 const jobs=[{state:'running',total:100,bytes:25},{state:'queued',directory:true},...['failed','completed','cancelled'].map(state=>({state,total:99999,bytes:1}))];const s=queueEstimate(jobs,{speed:25,ready:true});assert.equal(s.pending,2);assert.equal(s.records,5);assert.equal(s.remainingBytes,75);assert.equal(s.etaSeconds,3);
});
test('unknown file sizes never invent an ETA and become measurable when metadata arrives',()=>{
 const jobs=[{state:'running',bytes:0}];let s=queueEstimate(jobs,{speed:100,ready:true});assert.equal(s.unknownFiles,1);assert.equal(s.remainingBytes,null);assert.equal(s.etaSeconds,null);jobs[0].total=200;s=queueEstimate(jobs,{speed:100,ready:true});assert.equal(s.etaSeconds,2);
});
test('warm-up, a silent connection, cancellation and final publication have explicit states instead of zero seconds',()=>{
 const jobs=[{state:'running',total:100,bytes:0}];assert.equal(queueEstimate(jobs,{speed:0,ready:false}).status,'estimating');assert.equal(queueEstimate(jobs,{speed:0,ready:true}).status,'waiting');assert.equal(queueEstimate(jobs,{speed:1,ready:true},true).status,'cancelling');jobs[0].bytes=100;const s=queueEstimate(jobs,{speed:100,ready:true});assert.equal(s.status,'finishing');assert.equal(s.etaSeconds,null);jobs[0].state='completed';assert.equal(queueEstimate(jobs,{speed:100,ready:true}).status,'idle');
});
test('queue rate uses measured bytes and monotonic time, expires stale traffic and resets for a new drain',()=>{
 let now=0;const r=new QueueRate(()=>now);r.begin();now=500;r.record(1000);assert.equal(r.snapshot().ready,false);now=1000;assert.equal(r.snapshot().speed,1000);now=2000;r.record(1000);assert.equal(r.snapshot().speed,1000);now=13000;assert.equal(r.snapshot().speed,0);r.begin();assert.deepEqual(r.snapshot(),{speed:0,ready:false});
});
test('unsafe combined sizes and malformed rates cannot produce a fabricated time',()=>{
 const jobs=[{state:'running',total:Number.MAX_SAFE_INTEGER,bytes:0},{state:'queued',total:100,bytes:0}];const s=queueEstimate(jobs,{speed:Infinity,ready:true});assert.equal(s.remainingBytes,null);assert.equal(s.etaSeconds,null);assert.equal(s.speed,0);
});
