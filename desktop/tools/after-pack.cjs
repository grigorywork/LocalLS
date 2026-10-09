'use strict';
const fs = require('node:fs');
const path = require('node:path');
const clientVersion = require('../package.json').version.split('.').map(Number);
module.exports = async context => {
  if (context.electronPlatformName !== 'win32') return;
  const PE = await import('pe-library');
  const R = await import('resedit');
  const target = path.join(context.appOutDir, 'LocalLS.exe');
  const exe = PE.NtExecutable.from(fs.readFileSync(target), { ignoreCert: true });
  const res = PE.NtExecutableResource.from(exe);
  const ico = R.Data.IconFile.from(fs.readFileSync(path.join(context.packager.projectDir, 'assets/icon.ico')));
  for (const group of R.Resource.IconGroupEntry.fromEntries(res.entries)) {
    R.Resource.IconGroupEntry.replaceIconsForResource(res.entries, group.id, group.lang, ico.icons.map(i => i.data));
  }
  for (const vi of R.Resource.VersionInfo.fromEntries(res.entries)) {
    vi.setFileVersion(...clientVersion, 0, 1033);
    vi.setProductVersion(...clientVersion, 0, 1033);
    vi.setStringValues({ lang: 1033, codepage: 1200 }, {
      FileDescription: 'LocalLS — SSH / SFTP', ProductName: 'LocalLS', CompanyName: 'LocalLS',
      OriginalFilename: 'LocalLS.exe', InternalName: 'LocalLS', LegalCopyright: 'LocalLS'
    });
    vi.outputToResourceEntries(res.entries);
  }
  res.outputResource(exe);
  fs.writeFileSync(target, Buffer.from(exe.generate()));
  const files = [], dirs = [];
  function walk(dir) {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, e.name);
      const rel = path.relative(context.appOutDir, full).split(path.sep).join('\\');
      if (e.isDirectory()) { dirs.push(rel); walk(full); } else files.push(rel);
    }
  }
  walk(context.appOutDir);
  const escape = value => value.replaceAll('$', '$$').replaceAll('"', '$\\"');
  const lines = files.map(p => `Delete "$INSTDIR\\${escape(p)}"`);
  lines.push(...dirs.sort((a, b) => b.length - a.length).map(p => `RMDir "$INSTDIR\\${escape(p)}"`));
  fs.writeFileSync(path.join(context.packager.projectDir, 'tools/uninstall-files.nsh'), lines.join('\n') + '\n');
};
