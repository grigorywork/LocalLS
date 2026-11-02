'use strict';
// Package the verified common Linux ASAR and official Electron runtime for Debian.
// Existing Fedora artifacts and user data are never modified.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const {execFileSync}=require('node:child_process'),asar=require('@electron/asar');
const root=path.resolve(__dirname,'..'),input=path.resolve(process.argv[2]||''),out=path.resolve(process.argv[3]||'');
const release=JSON.parse(fs.readFileSync(path.join(root,'release.json'))),pkg=JSON.parse(fs.readFileSync(path.join(root,'package.json')));
assert.equal(release.version,pkg.version);assert.equal(process.arch,'x64');assert.ok(fs.statSync(input).isFile());
assert.ok(!fs.existsSync(out),'Choose a new output directory');fs.mkdirSync(out,{recursive:true});
for(const dir of ['src','assets'])for(const name of fs.readdirSync(path.join(root,dir))){
 if(name==='platform.cjs'||name.endsWith('.ico'))continue;
 const rel=dir+'/'+name;if(fs.statSync(path.join(root,rel)).isFile())assert.deepEqual(asar.extractFile(input,rel),fs.readFileSync(path.join(root,rel)),rel);
}
assert.equal(JSON.parse(asar.extractFile(input,'package.json')).version,pkg.version);
const stage=path.join(out,'package'),app=path.join(stage,'opt/localls'),work=path.join(out,'asar');
fs.mkdirSync(path.dirname(app),{recursive:true});fs.cpSync(path.join(root,'node_modules/electron/dist'),app,{recursive:true});
fs.renameSync(path.join(app,'electron'),path.join(app,'localls'));fs.chmodSync(path.join(app,'localls'),0o755);fs.chmodSync(path.join(app,'chrome-sandbox'),0o4755);
fs.rmSync(path.join(app,'resources/default_app.asar'),{force:true});asar.extractAll(input,work);
const metadata={...release,platform:'debian'};fs.writeFileSync(path.join(work,'release.json'),JSON.stringify(metadata,null,2)+'\n');
// Preserve the common application identity and schema; only distro-facing labels differ.
for(const rel of ['src/core.cjs','src/main.cjs','src/index.html','src/renderer.js']){const p=path.join(work,rel);fs.writeFileSync(p,fs.readFileSync(p,'utf8').replaceAll('Fedora','Debian'));}
const packaged=JSON.parse(fs.readFileSync(path.join(work,'package.json')));packaged.description='LocalLS — Debian SSH/SFTP client and built-in QR server';fs.writeFileSync(path.join(work,'package.json'),JSON.stringify(packaged,null,2)+'\n');
(async()=>{
 await asar.createPackage(work,path.join(app,'resources/app.asar'));
 const put=(rel,text)=>{const p=path.join(stage,rel);fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,text);};
 put('DEBIAN/control',`Package: localls\nVersion: ${pkg.version}\nSection: net\nPriority: optional\nArchitecture: amd64\nMaintainer: MSS9LN <localls-archive@localhost>\nDepends: libc6 (>= 2.36), libgtk-3-0t64, libnss3, libasound2t64, libxss1, libxtst6, libsecret-1-0, libgbm1, libatspi2.0-0t64, libdbus-1-3, xdg-utils\nRecommends: gnome-keyring\nDescription: LocalLS SSH/SFTP client and built-in QR server\n File exchange with QR pairing and explicit device approval.\n System OpenSSH, firewall and autostart are not modified.\n`);
 put('usr/share/applications/localls.desktop',fs.readFileSync(path.join(root,'tools/localls.desktop'),'utf8'));
 const icon=path.join(stage,'usr/share/icons/hicolor/256x256/apps/localls.png');fs.mkdirSync(path.dirname(icon),{recursive:true});fs.copyFileSync(path.join(root,'assets/icon.png'),icon);
 fs.mkdirSync(path.join(stage,'usr/bin'),{recursive:true});fs.symlinkSync('/opt/localls/localls',path.join(stage,'usr/bin/localls'));
 put('usr/share/doc/localls/copyright','LocalLS\nCopyright MSS9LN\nLicense: Proprietary\n');
 function permissions(dir){fs.chmodSync(dir,0o755);for(const e of fs.readdirSync(dir,{withFileTypes:true})){const p=path.join(dir,e.name);if(e.isDirectory())permissions(p);else if(e.isFile())fs.chmodSync(p,fs.statSync(p).mode&0o111?0o755:0o644);}}
 permissions(stage);fs.chmodSync(path.join(app,'chrome-sandbox'),0o4755);
 let installed=0;function measure(dir){for(const e of fs.readdirSync(dir,{withFileTypes:true})){const p=path.join(dir,e.name);if(e.isDirectory())measure(p);else if(e.isFile())installed+=fs.statSync(p).size;}}measure(path.join(stage,'opt'));measure(path.join(stage,'usr'));
 fs.appendFileSync(path.join(stage,'DEBIAN/control'),'Installed-Size: '+Math.ceil(installed/1024)+'\n');
 const sums=[];function walk(dir){for(const entry of fs.readdirSync(dir,{withFileTypes:true})){const p=path.join(dir,entry.name);if(entry.isDirectory())walk(p);else if(entry.isFile())sums.push(crypto.createHash('md5').update(fs.readFileSync(p)).digest('hex')+'  '+path.relative(stage,p));}}
 walk(path.join(stage,'opt'));walk(path.join(stage,'usr'));put('DEBIAN/md5sums',sums.join('\n')+'\n');
 const deb=path.join(out,`LocalLS-${pkg.version}-Debian13-amd64.deb`);
 execFileSync('dpkg-deb',['--root-owner-group','-Zxz','-z6','--build',stage,deb],{stdio:'inherit'});
 console.log(deb);
})().catch(error=>{console.error(error);process.exitCode=1;});
