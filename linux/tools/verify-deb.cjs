'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),asar=require('@electron/asar');
const {execFileSync}=require('node:child_process'),root=path.resolve(__dirname,'..'),deb=path.resolve(process.argv[2]),app='/opt/localls',archive=app+'/resources/app.asar',version=require('../release.json').version;
const run=(cmd,args)=>execFileSync(cmd,args,{encoding:'utf8'}).trim();
assert.equal(run('dpkg-deb',['-f',deb,'Package']),'localls');assert.equal(run('dpkg-deb',['-f',deb,'Version']),version);assert.equal(run('dpkg-deb',['-f',deb,'Architecture']),'amd64');
assert.equal(run('dpkg-query',['-W','-f=${Status}','localls']),'install ok installed');assert.equal(run('dpkg',['-V','localls']),'');
const names=execFileSync('dpkg-deb',['--ctrl-tarfile',deb]);const controls=execFileSync('tar',['-tf','-'],{input:names,encoding:'utf8'});
assert.ok(!/(?:preinst|postinst|prerm|postrm|triggers)/.test(controls));assert.ok(controls.includes('md5sums'));
for(const dir of [app,app+'/resources'])assert.equal(fs.statSync(dir).mode&0o777,0o755,dir);assert.equal(fs.statSync(archive).mode&0o777,0o644);assert.ok(Number(run('dpkg-deb',['-f',deb,'Installed-Size']))>200000);
assert.equal(fs.statSync(app+'/chrome-sandbox').uid,0);assert.equal(fs.statSync(app+'/chrome-sandbox').mode&0o7777,0o4755);
const elf=fs.readFileSync(app+'/localls');assert.equal(elf.readUInt32BE(0),0x7f454c46);assert.equal(elf.readUInt16LE(18),62);assert.ok(!run('ldd',[app+'/localls']).includes('not found'));
execFileSync('desktop-file-validate',['/usr/share/applications/localls.desktop']);assert.ok(!fs.readFileSync('/usr/share/applications/localls.desktop','utf8').includes('--no-sandbox'));
for(const dir of ['src','assets'])for(const name of fs.readdirSync(path.join(root,dir))){if(name==='platform.cjs'||name.endsWith('.ico'))continue;const rel=dir+'/'+name,p=path.join(root,rel);if(!fs.statSync(p).isFile())continue;let expected=fs.readFileSync(p);if(['src/core.cjs','src/main.cjs','src/index.html','src/renderer.js'].includes(rel))expected=Buffer.from(expected.toString().replaceAll('Fedora','Debian'));assert.deepEqual(asar.extractFile(archive,rel),expected,rel);}
const metadata=JSON.parse(asar.extractFile(archive,'release.json'));assert.equal(metadata.platform,'debian');assert.equal(metadata.version,version);assert.equal(metadata.author,'MSS9LN');
const entries=asar.listPackage(archive);assert.ok(!entries.some(p=>p.includes('/tests/')||p.includes('/tools/')||p.endsWith('.node')||p.endsWith('vault.bin')));
console.log('PASS Debian13/amd64: installed DEB, checksums, exact transformed source, ELF/libs, root-owned4755 sandbox, desktop entry, no OS-service scripts or packaged test data. No publisher signature.');
