'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {Server}=require('../src/core.cjs'),{fixture}=require('./fixture.cjs');
test('authenticated status subsystem displays fresh battery and storage without exec; invalid data falls back safely',async()=>{
 const f=await fixture(),s=new Server();try{
  await s.connect(f.profile,f.password,f.fingerprint);
  f.setTelemetry('localls-status-v1\n73\n2147483648\n4294967296\n3660\n');let stats=await s.stats();assert.equal(stats.battery,'73%');assert.equal(stats.disk,'2.0 ГБ');assert.equal(stats.uptime,'1 ч 1 мин');assert.equal(f.execAttempts,0);
  f.setTelemetry('localls-status-v1\n0\n0\n4294967296\n3720\n');stats=await s.stats();assert.equal(stats.battery,'0%');assert.equal(stats.disk,'0 Б');assert.equal(f.execAttempts,0);
  for(const invalid of ['localls-status-v1\n101\n0\n10\n0\n','localls-status-v1\n50\n11\n10\n0\n','localls-status-v1\n50\n-2\n10\n0\n','x'.repeat(300),'localls-status-v1\n50\n0\n10\n0\n<script>']){f.setTelemetry(invalid);stats=await s.stats();assert.equal(stats.battery,'—');assert.equal(stats.disk,'58.6 МБ');}
  assert.equal(f.execAttempts,5);
 }finally{s.disconnect();await f.close();}
});
