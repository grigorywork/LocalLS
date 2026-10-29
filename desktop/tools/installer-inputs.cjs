'use strict';
const fs=require('node:fs'),path=require('node:path');
function writeInstallerInputs(appOutDir,projectDir){
 const files=[],dirs=[];
 function walk(dir){for(const e of fs.readdirSync(dir,{withFileTypes:true})){const full=path.join(dir,e.name),rel=path.relative(appOutDir,full).split(path.sep).join('\\');if(e.isDirectory()){dirs.push(rel);walk(full);}else files.push(rel);}}
 walk(appOutDir);const escape=s=>s.replaceAll('$','$$').replaceAll('"','$\\"');
 fs.writeFileSync(path.join(projectDir,'tools/package-path.nsh'),'!define APP_OUT_DIR "'+escape(path.resolve(appOutDir))+'"\n');
 const lines=files.map(p=>'Delete "$INSTDIR\\'+escape(p)+'"');lines.push(...dirs.sort((a,b)=>b.length-a.length).map(p=>'RMDir "$INSTDIR\\'+escape(p)+'"'));fs.writeFileSync(path.join(projectDir,'tools/uninstall-files.nsh'),lines.join('\n')+'\n');
}
module.exports={writeInstallerInputs};
