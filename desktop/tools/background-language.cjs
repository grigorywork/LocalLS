'use strict';
const {_electron:electron}=require('playwright');
const fs=require('fs'),path=require('path'),os=require('os'),net=require('net'),assert=require('assert/strict');
(async()=>{
 const root=process.env.LOCALLS_TEST_APP,tmp=fs.mkdtempSync(path.join(os.tmpdir(),'localls-features-')),data=path.join(tmp,'data'),shared=path.join(tmp,'shared'),outside=path.join(tmp,'elsewhere');
 for(const dir of [data,shared,outside])fs.mkdirSync(dir);fs.writeFileSync(path.join(outside,'Фото пользователя.txt'),'keep name and bytes');
 const socket=net.createServer();await new Promise(r=>socket.listen(0,'127.0.0.1',r));const port=socket.address().port;await new Promise(r=>socket.close(r));
 fs.writeFileSync(path.join(data,'settings.json'),JSON.stringify({role:'server',appearanceVersion:2,language:'en'}));fs.writeFileSync(path.join(data,'server.json'),JSON.stringify({port,user:'localls',root:shared,devices:[]}));
 const clientSource=process.env.LOCALLS_CLIENT_SRC||path.resolve(__dirname,'../src');const pairing=require(path.join(clientSource,'pairing.cjs')),security=require(path.join(clientSource,'security.cjs')),{Server,Transfers}=require(path.join(clientSource,'core.cjs')),{PNG}=require('pngjs');let client;
 const out=process.env.LOCALLS_UI_OUTPUT;fs.mkdirSync(out,{recursive:true});let app;
 try{
  app=await electron.launch({args:['--disable-gpu','--user-data-dir='+data,root],env:{...process.env},timeout:30000});const page=await app.firstWindow();const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.waitForFunction(()=>window.localLS&&document.getElementById('hostAddress').textContent!=='—');
  await page.waitForFunction(()=>document.documentElement.lang==='en');assert.equal(await page.locator('#roleServer').textContent(),'Server');
  const initial=await page.evaluate(()=>window.localLS.serverState());assert.equal(initial.data.root,shared);
  await page.locator('#hostToggle').click();await page.waitForFunction(()=>document.getElementById('hostState').textContent==='Server running');
  const running=await page.evaluate(()=>window.localLS.serverState());assert.equal(running.data.running,true);const pin=running.data.fingerprint;
  const ids=await app.evaluate(({powerSaveBlocker})=>Array.from({length:128},(_,i)=>i).filter(i=>powerSaveBlocker.isStarted(i)));assert.ok(ids.length,'Server must inhibit idle suspension before any client traffic');
  await page.screenshot({path:path.join(out,'01-server-english.png')});
  const window=await app.browserWindow(page);await window.evaluate(w=>w.close());await page.waitForTimeout(Number(process.env.LOCALLS_IDLE_MS||35000));
  const hidden=await page.evaluate(()=>window.localLS.serverState());assert.equal(hidden.data.running,true);assert.equal(hidden.data.fingerprint,pin);
  assert.ok(await app.evaluate(({powerSaveBlocker},list)=>list.some(i=>powerSaveBlocker.isStarted(i)),ids),'Power blocker survives35 seconds without traffic and closing window');
  await window.evaluate(w=>{w.show();w.restore();w.focus();});
  const qr=await page.evaluate(async()=>{const s=await window.localLS.serverState();return window.localLS.serverQr({host:s.data.addresses[0]});});assert.equal(qr.ok,true);const png=PNG.sync.read(Buffer.from(qr.data.image.split(',')[1],'base64')),offer=pairing.decodePixels(png.data,png.width,png.height),identity=await security.generateIdentity();
  assert.equal(await pairing.loginAttempt(offer,identity,true),false);await page.locator('#pendingDeviceCard').waitFor({state:'visible'});assert.equal(await page.locator('#pendingDeviceCode').textContent(),pairing.syncCode(offer,identity.publicKey));
  await app.evaluate(({dialog})=>{dialog.showMessageBox=async()=>({response:1});});await page.locator('#approveHostDevice').click();client=new Server();await client.connect({...offer,folder:'/'},{method:'key',privateKey:identity.privateKey},offer.pin);
  const payload=Buffer.alloc(65536,29),source=path.join(tmp,'upload.bin');fs.writeFileSync(source,payload);const queue=new Transfers(client);queue.add([{direction:'upload',local:source,remote:'/after-hidden.bin',label:'after hidden',overwrite:false}]);const until=Date.now()+15000;while(queue.running&&Date.now()<until)await page.waitForTimeout(20);assert.equal(queue.running,false);assert.equal(queue.items[0].state,'completed');assert.deepEqual(fs.readFileSync(path.join(shared,'after-hidden.bin')),payload);client.disconnect();client=null;

  for(const language of ['ru','en']){
   await page.evaluate(async language=>{await window.localLS.saveLanguage(language);window.localLSI18n.setLanguage(language);const r=await window.localLS.state();applyState(r.data);page(currentPage);if(hosting)renderHosting(hosting);},language);
   for(const size of [{width:960,height:720},{width:1440,height:900}]){
    await window.evaluate((w,size)=>w.setSize(size.width,size.height),size);
    for(const light of [false,true]){
     await page.evaluate(async light=>{const state=await window.localLS.state();const next=await window.localLS.saveAppearance({...state.data.settings,theme:'red',light,glass:true,outline:'light'});applyState(next.data);},light);await page.waitForTimeout(180);
     const clips=await page.evaluate(()=>Array.from(document.querySelectorAll('button:not(.hidden),input,select')).filter(e=>e.getClientRects().length&&e.closest('.hidden')===null).filter(e=>{const r=e.getBoundingClientRect();return r.width<0||r.height<0||r.left<0||r.right>window.innerWidth+2;}).map(e=>e.id||e.textContent));assert.deepEqual(clips,[]);
     await page.screenshot({path:path.join(out,language+'-'+size.width+'-'+(light?'light':'dark')+'.png')});
    }
   }
  }
  await page.evaluate(async()=>{await window.localLS.saveLanguage('en');window.localLSI18n.setLanguage('en');const s=await window.localLS.state();applyState(s.data);});
  await page.locator('[data-page="files"]').first().click();
  await app.evaluate(({dialog},folder)=>{dialog.showOpenDialog=async()=>({canceled:false,filePaths:[folder]});},shared);
  await page.locator('#chooseLocal').click();await page.waitForFunction(p=>document.getElementById('localPath').textContent===p,shared);
  const browse=await page.evaluate(async p=>window.localLS.localList(p),outside);assert.equal(browse.ok,true,'Local folders outside initial root are allowed');assert.equal(browse.data.entries[0].name,'Фото пользователя.txt');assert.equal(fs.readFileSync(path.join(outside,'Фото пользователя.txt'),'utf8'),'keep name and bytes');
  for(const language of ['ru','en'])for(const [kind,caption] of [['pc',language==='en'?'PC server':'Сервер-ПК'],['phone',language==='en'?'Mobile server':'Сервер-мобильный'],['tablet',language==='en'?'Mobile server':'Сервер-мобильный'],['unknown',language==='en'?'Server':'Сервер']]){await page.evaluate(({language,kind})=>{window.localLSI18n.setLanguage(language);peerKind=kind;renderPeerKind();},{language,kind});assert.equal(await page.locator('#remoteDeviceTitle').textContent(),caption);}
  const actualPeer=await (async()=>{const s=new Server();try{await s.connect({...offer,folder:'/'},{method:'key',privateKey:identity.privateKey},offer.pin);return await s.stats();}finally{s.disconnect();}})();assert.equal(actualPeer.deviceKind,'pc');
  await page.evaluate(()=>window.localLS.serverStop());assert.ok(await app.evaluate(({powerSaveBlocker},list)=>list.every(i=>!powerSaveBlocker.isStarted(i)),ids));
  assert.deepEqual(errors,[]);console.log('PASS English/Russian appearance, visible destination, any local folder and server35s hidden idle power lease plus actual64KiB SFTP afterward; same SSH identity preserved');
 }finally{client?.disconnect();await app?.close();fs.rmSync(tmp,{recursive:true,force:true});}
})().catch(e=>{console.error(e.stack);process.exitCode=1});
