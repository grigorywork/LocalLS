'use strict';
// The application is packed by Electron Builder. Native NSIS has a bounded
// 15-minute compression budget instead of builder-util's four-minute limit.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{spawnSync}=require('node:child_process'),asar=require('@electron/asar');
const {writeInstallerInputs}=require('./installer-inputs.cjs');
function checkUnpacked(dir,root){
 const archive=path.join(dir,'resources/app.asar'),pkg=JSON.parse(fs.readFileSync(path.join(root,'package.json'))),packed=JSON.parse(asar.extractFile(archive,'package.json'));
 for(const key of ['name','version','author','main','dependencies'])assert.deepEqual(packed[key],pkg[key],'Stale packaged '+key);
 assert.deepEqual(asar.extractFile(archive,'release.json'),fs.readFileSync(path.join(root,'release.json')),'Stale/missing release metadata');
 function walk(rel){for(const e of fs.readdirSync(path.join(root,rel),{withFileTypes:true})){const file=path.join(rel,e.name);if(e.isDirectory())walk(file);else assert.deepEqual(asar.extractFile(archive,file.split(path.sep).join('/')),fs.readFileSync(path.join(root,file)),'Stale source '+file);}}
 walk('src');
}
async function build(){
 const root=path.resolve(__dirname,'..'),pkg=require('../package.json'),release=require('../release.json');assert.equal(release.version,pkg.version);
 let output,reuse;
 for(const arg of process.argv.slice(2)){if(arg.startsWith('--config.directories.output='))output=arg.slice('--config.directories.output='.length);else if(arg.startsWith('--reuse-unpacked='))reuse=arg.slice('--reuse-unpacked='.length);else throw new Error('Unsupported build option: '+arg);}
 output=path.resolve(root,output||path.join('artifacts','windows-'+release.displayVersion+'-'+new Date().toISOString().replace(/[:.]/g,'-')));
 if(fs.existsSync(output))throw new Error('Output must be new: '+output);
 if(reuse){reuse=path.resolve(root,reuse);checkUnpacked(reuse,root);fs.mkdirSync(output,{recursive:true});fs.symlinkSync(reuse,path.join(output,'win-unpacked'),'junction');}
 else{const cli=path.join(path.dirname(require.resolve('electron-builder/package.json')),'cli.js');const r=spawnSync(process.execPath,[cli,'--win','dir','--x64','--publish','never','--config.directories.output='+output],{cwd:root,stdio:'inherit',timeout:600000});if(r.error)throw r.error;if(r.status!==0)throw new Error('Electron Builder packaging failed');}
 const unpacked=path.join(output,'win-unpacked');checkUnpacked(unpacked,root);writeInstallerInputs(unpacked,root);
 const {getMakeNsisPath}=require('app-builder-lib/out/toolsets/windows'),{nsisEscapeString}=require('app-builder-lib/out/targets/nsis/nsisScriptGenerator');
 const compiler=await getMakeNsisPath(pkg.build.toolsets?.nsis,pkg.build.nsis.customNsisBinary),quote=s=>'"'+nsisEscapeString(s)+'"';
 const installer=path.join(output,pkg.build.nsis.artifactName),icon=path.join(root,pkg.build.win.icon);
 const args=['-WX','-INPUTCHARSET','UTF8','-DPROJECT_DIR='+nsisEscapeString(root),'-DVERSION='+pkg.version,'-DMUI_ICON='+nsisEscapeString(icon),'-DMUI_UNICON='+nsisEscapeString(icon),'-XUnicode true','-XVIProductVersion '+pkg.version+'.0','-XOutFile '+quote(installer)];
 for(const [key,value] of Object.entries({ProductName:pkg.build.productName,CompanyName:pkg.author,FileDescription:'LocalLS client and server setup',FileVersion:pkg.version,ProductVersion:pkg.version,LegalCopyright:pkg.author}))args.push('-XVIAddVersionKey /LANG=1033 '+quote(key)+' '+quote(value));
 args.push(path.join(root,'tools/installer.nsi'));
 const r=spawnSync(compiler.path,args,{cwd:root,env:{...process.env,...compiler.env},stdio:'inherit',timeout:900000});if(r.error)throw r.error;if(r.status!==0)throw new Error('NSIS compilation failed');console.log('BUILD SUCCESSFUL: '+installer);
}
module.exports={checkUnpacked};
if(require.main===module)build().catch(e=>{console.error(e.message);process.exitCode=1;});
