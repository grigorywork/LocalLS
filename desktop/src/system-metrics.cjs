'use strict';
const path = require('node:path');
const {execFile} = require('node:child_process');
function batteryPercent(text) {
  try {
    let value = JSON.parse(text); if (Array.isArray(value)) { if (value.length !== 1) return -1; value = value[0]; }
    const n = value?.EstimatedChargeRemaining; return Number.isInteger(n) && n >= 0 && n <= 100 ? n : -1;
  } catch { return -1; }
}
class WindowsBattery {
  constructor({platform = process.platform, systemRoot = process.env.SystemRoot || 'C:\\Windows', run = execFile, now = Date.now, changed = () => {}} = {}) {
    this.platform = platform; this.systemRoot = systemRoot; this.run = run; this.now = now; this.changed = changed; this.value = -1; this.next = 0; this.pending = false;
  }
  read() {
    if (this.platform !== 'win32' || this.pending || this.now() < this.next) return this.value;
    this.pending = true; this.next = this.now() + 30000;
    const binary = path.win32.join(this.systemRoot, 'System32', 'WindowsPowerShell', 'v1.0', 'powershell.exe');
    const command = 'Get-CimInstance -ClassName Win32_Battery | Select-Object -First 1 EstimatedChargeRemaining | ConvertTo-Json -Compress';
    this.run(binary, ['-NoLogo','-NoProfile','-NonInteractive','-Command',command], {windowsHide: true, timeout: 2500, maxBuffer: 8192, encoding: 'utf8'}, (error, output) => {
      this.pending = false; this.value = error ? -1 : batteryPercent(output); try { this.changed(); } catch {}
    });
    return this.value;
  }
}
module.exports = {batteryPercent, WindowsBattery};
