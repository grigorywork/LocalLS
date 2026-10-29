'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const {ActivityPower} = require('../src/activity-power.cjs');
const {batteryPercent, WindowsBattery} = require('../src/system-metrics.cjs');
test('A zero-valued Electron power blocker is held once and released exactly once', () => {
  const calls = [], p = new ActivityPower({start: kind => { calls.push(['start',kind]); return 0; }, stop: id => calls.push(['stop',id])});
  p.acquire(); p.acquire(); p.release(); p.release();
  assert.deepEqual(calls, [['start','prevent-app-suspension'], ['stop',0]]);
});
test('Server traffic refreshes its power lease and idle expiry releases blocker zero', () => {
  const timers = new Map(), stopped = []; let next = 0;
  const p = new ActivityPower({start: () => 0, stop: id => stopped.push(id)}, fn => { const id = ++next; timers.set(id,fn); return id; }, id => timers.delete(id));
  p.pulse(); p.pulse(); assert.equal(timers.size,1); [...timers.values()][0](); assert.deepEqual(stopped,[0]); assert.equal(timers.size,0); p.release(); assert.deepEqual(stopped,[0]);
});
test('Windows battery parsing preserves zero and rejects absent, malformed and impossible readings', () => {
  for (const n of [0,1,73,100]) assert.equal(batteryPercent(JSON.stringify({EstimatedChargeRemaining:n})),n);
  for (const v of ['', 'null', '{}', '[]', 'bad', '{"EstimatedChargeRemaining":101}', '{"EstimatedChargeRemaining":-1}', '{"EstimatedChargeRemaining":"73"}']) assert.equal(batteryPercent(v),-1);
});
test('Windows battery lookup uses a fixed bounded command and no calls on other platforms', () => {
  let now = 0, callback, calls = 0;
  const b = new WindowsBattery({platform:'win32',systemRoot:'C:\\Windows',now:()=>now,run:(binary,args,options,cb)=>{calls++;assert.equal(binary,'C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe');assert.ok(args.includes('-NonInteractive'));assert.equal(options.timeout,2500);assert.ok(!options.shell);callback=cb;}});
  assert.equal(b.read(),-1); assert.equal(b.read(),-1); assert.equal(calls,1); callback(null,'{"EstimatedChargeRemaining":73}'); assert.equal(b.read(),73); assert.equal(calls,1);
  now=31000;assert.equal(b.read(),73);callback(new Error('unavailable'),'');assert.equal(b.read(),-1);assert.equal(calls,2);
  const other=new WindowsBattery({platform:'linux',run:()=>assert.fail('Must not spawn Windows commands on Linux')});assert.equal(other.read(),-1);
});
