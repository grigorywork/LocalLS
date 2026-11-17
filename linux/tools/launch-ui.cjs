
'use strict';
const path=require('node:path'),fs=require('node:fs'),assert=require('node:assert/strict'),e=require('playwright')._electron,launch=e.launch.bind(e);
const output=process.env.LOCALLS_RUN_OUTPUT||'.';
const sandboxed=process.env.LOCALLS_UI_NO_SANDBOX!=='1';
e.launch=async options=>{const args=options.args.filter(x=>x!=='--no-sandbox');if(!sandboxed)args.push('--no-sandbox');const app=await launch({...options,executablePath:process.env.LOCALLS_TEST_EXECUTABLE,args});app.process().stderr.on('data',b=>fs.appendFileSync(path.join(output,'electron-stderr.log'),b));await app.evaluate(({BrowserWindow},out)=>{const fs=process.getBuiltinModule('node:fs'),path=process.getBuiltinModule('node:path');for(const w of BrowserWindow.getAllWindows())w.webContents.on('render-process-gone',(_e,details)=>fs.appendFileSync(path.join(out,'render-crashes.jsonl'),JSON.stringify(details)+'\n'));},output);assert.equal(await app.evaluate(({app})=>app.getAppPath()),process.env.LOCALLS_TEST_APP,'Must test installed RPM');assert.equal(await app.evaluate(({app})=>app.commandLine.hasSwitch('no-sandbox')),!sandboxed);return app;};
