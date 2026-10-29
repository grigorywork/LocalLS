'use strict';
const { _electron: electron }=require('playwright');
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),os=require('node:os');
const {fixture}=require('../tests/fixture.cjs');
(async()=>{
 const first=await fixture(),second=await fixture(),temp=fs.mkdtempSync(path.join(os.tmpdir(),'localls-connect-state-'));let app;
 const initial={profile:first.profile,pins:{['127.0.0.1:'+first.profile.port]:first.fingerprint,['127.0.0.1:'+second.profile.port]:second.fingerprint},authMethod:'password',role:'client',appearanceVersion:2,remember:false};
 fs.writeFileSync(path.join(temp,'settings.json'),JSON.stringify(initial));
 try {
  app=await electron.launch({args:[...(process.platform==='win32'?[]:['--no-sandbox']),'--disable-gpu','--user-data-dir='+temp,path.resolve(process.env.LOCALLS_TEST_APP||path.join(__dirname,'..'))],env:{...process.env,DISPLAY:process.env.DISPLAY||':99'},timeout:30000});
  const page=await app.firstWindow();await page.waitForFunction(()=>window.localLS&&document.getElementById('hostMetric').textContent==='127.0.0.1');
  assert.equal((await page.evaluate(arg=>window.localLS.saveSettings(arg),{...initial,password:first.password})).ok,true);
  if(process.argv.includes('--invalid-save')){
   const before=await page.evaluate(()=>window.localLS.state()),disk=fs.readFileSync(path.join(temp,'settings.json'));
   const result=await page.evaluate(arg=>window.localLS.saveSettings(arg),{...initial,profile:second.profile,password:'x'.repeat(4097)});assert.equal(result.ok,false);
   const after=await page.evaluate(()=>window.localLS.state());assert.deepEqual(after.data.settings,before.data.settings,'Rejected save must retain in-memory profile/auth settings');assert.deepEqual(fs.readFileSync(path.join(temp,'settings.json')),disk);assert.equal(first.passwordAttempts,0);assert.equal(second.passwordAttempts,0);
   console.log('PASS: rejected password validation preserves settings in memory and on disk');return;
  }
  await app.evaluate(({app})=>{const require=process.getBuiltinModule('node:module').createRequire(app.getAppPath()+'/package.json'),core=require('./src/core.cjs'),original=core.Server.prototype.probe;globalThis.testOriginalProbe=original;globalThis.testProbeReached=false;core.Server.prototype.probe=async function(p){globalThis.testProbeReached=true;await new Promise(r=>{globalThis.releaseTestProbe=r;});return original.call(this,p);};});
  const pending=page.evaluate(()=>window.localLS.connect());
  for(let i=0;i<100&&!await app.evaluate(()=>globalThis.testProbeReached);i++)await page.waitForTimeout(20);
  assert.equal(await app.evaluate(()=>globalThis.testProbeReached),true);
  assert.equal((await page.evaluate(arg=>window.localLS.saveSettings(arg),{...initial,profile:second.profile,password:second.password})).ok,true);
  await app.evaluate(()=>globalThis.releaseTestProbe());const stale=await pending;
  assert.equal(stale.ok,false,'Superseded connect must not attach the old server under the new profile');
  assert.equal(first.passwordAttempts,0,'Superseded probe must stop before authentication');assert.equal(second.passwordAttempts,0);
  const state=await page.evaluate(()=>window.localLS.state());assert.equal(state.data.connected,false);assert.equal(state.data.settings.profile.port,second.profile.port);
  await app.evaluate(({app})=>{const require=process.getBuiltinModule('node:module').createRequire(app.getAppPath()+'/package.json');require('./src/core.cjs').Server.prototype.probe=globalThis.testOriginalProbe;});
  assert.equal((await page.evaluate(()=>window.localLS.connect())).ok,true);assert.equal(second.passwordAttempts,1);assert.equal(first.passwordAttempts,0);
  await app.close();app=null;
  console.log('PASS: changing profile during delayed SSH probe cancels stale connect before authentication');
 }finally{await app?.close();await first.close();await second.close();fs.rmSync(temp,{recursive:true,force:true});}
})().catch(e=>{console.error(e.stack);process.exitCode=1;});
