'use strict';
const {_electron:electron}=require('playwright');
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),assert=require('node:assert/strict');
(async()=>{
 const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'localls-root-race-')),data=path.join(tmp,'data'),oldRoot=path.join(tmp,'old'),newRoot=path.join(tmp,'new');
 for(const dir of [data,oldRoot,newRoot])fs.mkdirSync(dir);
 fs.writeFileSync(path.join(oldRoot,'old.txt'),'keep old');fs.writeFileSync(path.join(newRoot,'new.txt'),'keep new');
 fs.writeFileSync(path.join(data,'settings.json'),JSON.stringify({role:'client',appearanceVersion:2}));let app;
 try {
  app=await electron.launch({args:['--disable-gpu','--user-data-dir='+data,process.env.LOCALLS_TEST_APP],env:{...process.env},timeout:30000});const page=await app.firstWindow();
  await page.waitForFunction(()=>window.localLS&&document.getElementById('chooseLocal')&&document.getElementById('hostMetric').textContent!=='—');
  await page.locator('[data-page=files]').first().click();await page.waitForFunction(()=>document.getElementById('localPath').textContent!==''&&document.getElementById('localPath').textContent!=='—');
  await app.evaluate(({dialog},root)=>{dialog.showOpenDialog=async()=>({canceled:false,filePaths:[root]});},oldRoot);
  await page.locator('#chooseLocal').click();await page.waitForFunction(root=>document.getElementById('localPath').textContent===root,oldRoot);
  await app.evaluate(({dialog},args)=>{
   const fs=process.getBuiltinModule('node:fs'),original=fs.promises.readdir;
   globalThis.qaOriginalRead=original;globalThis.qaReadReached=false;globalThis.qaReadFinished=false;
   fs.promises.readdir=async function(folder,...rest){if(folder===args.old){globalThis.qaReadReached=true;await new Promise(resolve=>{globalThis.qaReleaseRead=resolve;});const result=await original.call(this,folder,...rest);globalThis.qaReadFinished=true;return result;}return original.call(this,folder,...rest);};
   dialog.showOpenDialog=async()=>({canceled:false,filePaths:[args.next]});
  },{old:oldRoot,next:newRoot});
  await page.locator('#refreshFiles').click();
  for(let n=0;n<100&&!await app.evaluate(()=>globalThis.qaReadReached);n++)await page.waitForTimeout(20);
  assert.equal(await app.evaluate(()=>globalThis.qaReadReached),true,'Old read barrier reached');
  await page.locator('#chooseLocal').click();await page.waitForFunction(root=>document.getElementById('localPath').textContent===root,newRoot);
  await app.evaluate(()=>globalThis.qaReleaseRead());
  for(let n=0;n<100&&!await app.evaluate(()=>globalThis.qaReadFinished);n++)await page.waitForTimeout(20);
  assert.equal(await app.evaluate(()=>globalThis.qaReadFinished),true);await page.waitForTimeout(250);
  assert.equal(await page.locator('#localPath').textContent(),newRoot,'Late old-folder listing must not replace newly chosen local root');
  assert.equal(await page.locator('#localEntries .file-row').getAttribute('title'),'new.txt');
  await page.locator('#refreshFiles').click();await page.waitForTimeout(150);assert.equal(await page.locator('#localPath').textContent(),newRoot);
  await app.evaluate(({dialog})=>{dialog.showOpenDialog=async()=>({canceled:true,filePaths:[]});});await page.locator('#chooseLocal').click();await page.waitForTimeout(100);assert.equal(await page.locator('#localPath').textContent(),newRoot,'Cancel preserves the current root');
  const driveParents=await page.evaluate(()=>{const platform=state.platform,root=localRoot;state.platform='win32';localRoot='C:\\';try{return [parentLocal('C:\\Folder'),parentLocal('D:\\Folder'),parentLocal('C:\\Folder\\Child'),parentLocal('C:\\')];}finally{state.platform=platform;localRoot=root;}});
  assert.deepEqual(driveParents,['C:\\','D:\\','C:\\Folder','C:\\'],'Windows parents retain absolute drive roots');
  await app.evaluate(({dialog},target)=>{const fs=process.getBuiltinModule('node:fs');fs.promises.readdir=globalThis.qaOriginalRead;dialog.showOpenDialog=()=>new Promise(resolve=>{globalThis.qaReleasePicker=()=>resolve({canceled:false,filePaths:[target]});});},oldRoot);
  await page.locator('#chooseLocal').click();
  for(let n=0;n<100&&!await app.evaluate(()=>!!globalThis.qaReleasePicker);n++)await page.waitForTimeout(20);
  assert.equal(await app.evaluate(()=>!!globalThis.qaReleasePicker),true,'Picker barrier reached');
  await page.evaluate(()=>loadLocal(localFolder));
  assert.equal(await page.locator('#localPath').textContent(),newRoot);
  await app.evaluate(()=>globalThis.qaReleasePicker());
  await page.waitForFunction(root=>document.getElementById('localPath').textContent===root,oldRoot);
  assert.equal(await page.locator('#localEntries .file-row').getAttribute('title'),'old.txt','An intermediate refresh must not discard the chosen root');
  assert.equal(fs.readFileSync(path.join(oldRoot,'old.txt'),'utf8'),'keep old');assert.equal(fs.readFileSync(path.join(newRoot,'new.txt'),'utf8'),'keep new');
  console.log('PASS choosing a new local root invalidates a delayed old listing; files unchanged');
 }finally{await app?.close();fs.rmSync(tmp,{recursive:true,force:true});}
})().catch(error=>{console.error(error.stack);process.exitCode=1;});
