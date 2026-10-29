'use strict';
// Electron power blocker IDs include zero. Never use truthiness to own/release one.
class ActivityPower {
  constructor(api, schedule = setTimeout, cancel = clearTimeout) { this.api = api; this.id = null; this.timer = null; this.schedule = schedule; this.cancel = cancel; }
  acquire() { if (this.id === null) this.id = this.api.start('prevent-app-suspension'); }
  release() { if (this.timer !== null) { this.cancel(this.timer); this.timer = null; } if (this.id !== null) { this.api.stop(this.id); this.id = null; } }
  pulse(ms = 30000) { this.acquire(); if (this.timer !== null) this.cancel(this.timer); this.timer = this.schedule(() => this.release(), ms); this.timer?.unref?.(); }
}
module.exports = {ActivityPower};
