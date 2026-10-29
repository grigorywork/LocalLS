'use strict';
// Keep payload accounting in the main process. UI polling and history pruning
// must never invent speed spikes or lose a completed small transfer.
class TrafficHistory {
  constructor(now = () => performance.now()) { this.now = now; this.started = now(); this.bytes = 0; this.buckets = new Map(); }
  record(n) {
    if (!Number.isSafeInteger(n) || n < 0 || !Number.isSafeInteger(this.bytes + n)) throw new Error('BAD_TRAFFIC');
    if (!n) return;
    const second = Math.floor(this.now() / 1000); this.bytes += n; this.buckets.set(second, (this.buckets.get(second) || 0) + n);
    for (const key of this.buckets.keys()) if (key < second - 60) this.buckets.delete(key);
  }
  snapshot() {
    const now = this.now(), second = Math.floor(now / 1000), observed = (now - Math.max(this.started, (second - 1) * 1000)) / 1000;
    const speed = observed >= .25 ? ((this.buckets.get(second) || 0) + (this.buckets.get(second - 1) || 0)) / observed : 0;
    const points = Array.from({length: 60}, (_, i) => this.buckets.get(second - 59 + i) || 0); points[59] = speed;
    return {bytes: this.bytes, speed, points};
  }
}
module.exports = {TrafficHistory};
