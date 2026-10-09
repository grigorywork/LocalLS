'use strict';
// The signed-off report includes real runtime evidence and is maintained after tests.
const fs=require('node:fs'),p=require('../package.json');const v=JSON.parse(fs.readFileSync('artifacts/VERIFICATION.json','utf8'));if(v.version!==p.version)throw new Error('Stale artifact verification');const report=fs.readFileSync('BUILD_REPORT.md','utf8');if(!report.includes(v.installer.sha256)||!report.includes(p.version))throw new Error('Report does not match the actual installer');console.log('PASS: BUILD_REPORT matches verified installer version/hash');
