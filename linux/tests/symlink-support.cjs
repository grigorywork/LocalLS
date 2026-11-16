'use strict';
// Windows only grants SeCreateSymbolicLinkPrivilege to elevated/developer-mode users.
// Tests that need a real symlink are skipped instead of failing on that environment.
const fs = require('node:fs'), os = require('node:os'), path = require('node:path');
module.exports = (() => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'localls-symlink-'));
  try { fs.symlinkSync(path.join(dir, 'target'), path.join(dir, 'link')); return true; }
  catch { return false; }
  finally { fs.rmSync(dir, {recursive: true, force: true}); }
})();
