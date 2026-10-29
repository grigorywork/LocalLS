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
      FileDescription: 'LocalLS — SSH / SFTP client and server', ProductName: 'LocalLS', CompanyName: 'MSS9LN',
      OriginalFilename: 'LocalLS.exe', InternalName: 'LocalLS', LegalCopyright: 'MSS9LN'
    });
    vi.outputToResourceEntries(res.entries);
  }
  res.outputResource(exe);
  fs.writeFileSync(target, Buffer.from(exe.generate()));
  require('./installer-inputs.cjs').writeInstallerInputs(context.appOutDir,context.packager.projectDir);
};
