'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const {TrafficHistory} = require('../src/traffic-history.cjs');
test('Payload rates use actual main-process time and survive delayed UI polling', () => {
  let now = 0; const h = new TrafficHistory(() => now);
  now=100;h.record(100);now=900;h.record(900);now=1000;let s=h.snapshot();assert.equal(s.bytes,1000);assert.equal(s.speed,1000);assert.equal(s.points[58],1000);
  now=1500;h.record(500);now=1700;s=h.snapshot();assert.equal(s.bytes,1500);assert.equal(s.speed,1500/1.7);
  now=3500;s=h.snapshot();assert.equal(s.speed,0);assert.equal(s.points[56],1000);assert.equal(s.points[57],500);
});
test('Queue boundaries and local publication cannot reset or fabricate network history', () => {
  let now=0;const h=new TrafficHistory(()=>now);now=500;h.record(4096);now=1000;h.record(2048);now=1500;const before=h.snapshot();assert.equal(before.bytes,6144);
  // No record call is made for a rename, progress repaint or queue history pruning.
  assert.deepEqual(h.snapshot(),before);now=62000;const after=h.snapshot();assert.equal(after.bytes,6144);assert.equal(after.speed,0);assert.ok(after.points.every(v=>v===0));
});
test('Zero-length traffic is valid and invalid byte deltas are rejected', () => {
  const h=new TrafficHistory(()=>1000);h.record(0);assert.equal(h.snapshot().bytes,0);for(const n of [-1,NaN,Infinity,1.5])assert.throws(()=>h.record(n));
});
