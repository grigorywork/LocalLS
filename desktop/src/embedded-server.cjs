'use strict';
const {EventEmitter} = require('node:events');
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const {Server: SSHServer, utils} = require('ssh2');
const {profile, fingerprint} = require('./core.cjs');
const {syncCode} = require('./pairing.cjs');
const {SharedFiles, serveFiles} = require('./server-files.cjs');
const {TrafficHistory} = require('./traffic-history.cjs');

function publicIdentity(value) {
  if (typeof value !== 'string' || value.length > 16000) throw new Error('KEY_INVALID');
  const parts = value.trim().split(/\s+/);
  if (!/^(ssh-rsa|ssh-ed25519|ecdsa-sha2-nistp(?:256|384|521))$/.test(parts[0])) throw new Error('KEY_INVALID');
  const key = utils.parseKey(parts.slice(0, 2).join(' '));
  if (!key || key instanceof Error || key.getPrivatePEM()) throw new Error('KEY_INVALID');
  if (key.type === 'ssh-rsa' && crypto.createPublicKey(key.getPublicPEM()).asymmetricKeyDetails.modulusLength < 3072) throw new Error('KEY_WEAK');
  return {key, publicKey: key.type + ' ' + key.getPublicSSH().toString('base64') + ' LocalLS', id: fingerprint(key.getPublicSSH())};
}
class EmbeddedServer extends EventEmitter {
  constructor({saveDevices = () => {}, telemetry} = {}) {
    super(); this.saveDevices = saveDevices; this.customTelemetry = telemetry; this.devices = []; this.clients = new Map(); this.received = 0; this.sent = 0; this.traffic = new TrafficHistory(); this.history = []; this.instance = null; this.invitation = null; this.pending = null;
  }
  restoreDevices(devices) {
    if (this.instance || !Array.isArray(devices) || devices.length > 7) throw new Error('SERVER_CONFIG');
    const normalized = devices.map(d => ({...d, ...publicIdentity(d.publicKey)}));
    if (new Set(normalized.map(d => d.id)).size !== normalized.length) throw new Error('SERVER_CONFIG');
    this.devices = normalized.map(({key, ...d}) => d);
  }
  async start({host = '0.0.0.0', port = 8022, user = 'localls', root, privateKey, devices = []}) {
    if (this.instance) throw new Error('SERVER_RUNNING');
    profile({host, port: port || 8022, user, folder: '.'});
    if (!Number.isInteger(port) || port < 0 || port > 65535 || devices.length > 7) throw new Error('SERVER_CONFIG');
    const key = utils.parseKey(privateKey); if (!key || key instanceof Error || !key.getPrivatePEM()) throw new Error('KEY_INVALID');
    const share = new SharedFiles(root), normalized = devices.map(d => ({...d, ...publicIdentity(d.publicKey)}));
    if (new Set(normalized.map(d => d.id)).size !== normalized.length) throw new Error('SERVER_CONFIG');
    this.devices = normalized.map(({key, ...d}) => d); this.share = share; this.user = user; this.pin = fingerprint(key.getPublicSSH());
    this.invitation = null; this.pending = null;
    const instance = new SSHServer({hostKeys: [privateKey], ident: 'LocalLS_1.33', highWaterMark: 65536, algorithms: {serverHostKey: ['rsa-sha2-512','rsa-sha2-256']}}, client => this.accept(client));
    this.instance = instance;
    instance.on('error', error => this.emit('notice', {code: error.code === 'EADDRINUSE' ? 'PORT_BUSY' : 'SERVER_ERROR'}));
    try { await new Promise((resolve, reject) => { instance.once('error', reject); instance.listen(port, host, () => { instance.removeListener('error', reject); resolve(); }); }); }
    catch (error) { this.instance = null; try { instance.close(); } catch {} throw error; }
    this.port = instance.address().port; this.startedAt = Date.now(); this.emit('update'); return this.snapshot();
  }
  drop(client) { try { client.end(); } finally { client._sock?.destroy(); } }
  accept(client) {
    const unauthenticated = [...this.clients].filter(([, meta]) => !meta.id);
    if (unauthenticated.length >= 36) this.drop(unauthenticated[0][0]);
    client.setNoDelay(true);
    const meta = {id: null, sessions: 0, attempts: 0}; this.clients.set(client, meta);
    const timer = setTimeout(() => { if (!meta.id) this.drop(client); }, 15000); timer.unref();
    client.on('error', () => {});
    client.on('close', () => { clearTimeout(timer); this.clients.delete(client); this.emit('update'); });
    client.on('authentication', ctx => {
      if (++meta.attempts > 20) { this.drop(client); return; }
      if (ctx.method !== 'publickey') return ctx.reject(['publickey']);
      try {
        if (!Buffer.isBuffer(ctx.key?.data) || ctx.key.data.length > 12000) return ctx.reject(['publickey']);
        const identity = publicIdentity(ctx.key.algo + ' ' + ctx.key.data.toString('base64'));
        const allowed = ctx.username === this.user && this.devices.some(d => d.id === identity.id);
        const offer = this.invitation;
        const pairing = offer && offer.expires > Date.now() && ctx.username === this.user + '#' + offer.nonce;
        if (!allowed && !pairing) return ctx.reject(['publickey']);
        if (!ctx.signature) return ctx.accept(); // PK_OK only; ssh2 still requires a signed proof.
        if (identity.key.verify(ctx.blob, ctx.signature, ctx.hashAlgo) !== true) return ctx.reject(['publickey']);
        if (allowed) {
          if ([...this.clients.values()].filter(c => c.id === identity.id).length >= 4) return ctx.reject(['publickey']);
          meta.id = identity.id; clearTimeout(timer); return ctx.accept();
        }
        if (!this.pending) { this.pending = {nonce: offer.nonce, publicKey: identity.publicKey, id: identity.id, code: syncCode(offer, identity.publicKey)}; this.emit('update'); }
        return ctx.reject(['publickey']); // Owner approval is always a separate local action.
      } catch { return ctx.reject(['publickey']); }
    });
    client.on('ready', () => {
      if (!meta.id || !this.devices.some(d => d.id === meta.id)) return this.drop(client);
      this.emit('update');
      client.on('session', (accept, reject) => {
        if (++meta.sessions > 4) { meta.sessions--; return reject(); }
        const session = accept(); session.on('close', () => meta.sessions--);
        session.on('exec', (_, reject) => { if (reject) reject(); }); session.on('shell', (_, reject) => { if (reject) reject(); });
        session.on('sftp', accept => serveFiles(accept(), this.share, (direction, count) => { this[direction] += count; this.traffic.record(count); this.emit('traffic', {direction, count}); }, entry => {
          const temporary = name => /^\.(?:localls|import)-.*\.part$/.test(name);
          if (entry.renamedFrom) {
            const received = this.history.findLast(h => h.device === meta.id && h.remote === entry.renamedFrom);
            if (received) Object.assign(received, {name: entry.name, remote: entry.remote, pending: temporary(entry.name)});
          } else {
            this.history.push({...entry, at: Date.now(), device: meta.id, pending: temporary(entry.name)}); this.history = this.history.slice(-100);
          }
          this.emit('update');
        }));
        session.on('subsystem', (accept, reject, info) => {
          if (!['localls-status-v1','localls-status-v2','localls-status-v3'].includes(info.name)) return reject();
          const channel = accept(), metrics = this.telemetry();
          const fields = [info.name, metrics.battery, metrics.free, metrics.total, metrics.uptime];
          if (info.name !== 'localls-status-v1') fields.push(metrics.charging);
          if (info.name === 'localls-status-v3') fields.push('pc');
          channel.write(fields.join('\n') + '\n'); channel.exit(0); channel.end();
        });
      });
    });
    client.on('tcpip', (_, reject) => { if (reject) reject(); });
    client.on('request', (accept, reject) => { if (reject) reject(); });
  }
  invite(host) {
    if (!this.instance) throw new Error('SERVER_STOPPED');
    profile({host, port: this.port, user: this.user});
    if (!this.invitation || this.invitation.expires <= Date.now() || this.invitation.host !== host) {
      this.invitation = {format: 'LocalLS-pairing', version: 1, type: 'server', host, port: this.port, user: this.user, pin: this.pin, nonce: crypto.randomUUID(), expires: Date.now() + 600000}; this.pending = null;
    }
    return {...this.invitation};
  }
  approve(nonce, publicKey, label) {
    const offer = this.invitation, pending = this.pending, identity = publicIdentity(publicKey);
    if (!this.instance || !offer || offer.expires <= Date.now() || offer.nonce !== nonce || !pending || pending.id !== identity.id || pending.nonce !== nonce) throw new Error('QR_EXPIRED');
    if (!this.devices.some(d => d.id === identity.id)) {
      if (this.devices.length >= 7) throw new Error('DEVICE_LIMIT');
      const next = [...this.devices, {id: identity.id, publicKey: identity.publicKey, label: String(label || 'Устройство ' + (this.devices.length + 1)).replace(/[\x00-\x1f]/g, '').slice(0, 64), addedAt: Date.now()}];
      this.saveDevices(next); this.devices = next;
    }
    this.invitation = null; this.pending = null; this.emit('update'); return this.snapshot();
  }
  revoke(id) {
    const next = this.devices.filter(d => d.id !== id); if (next.length === this.devices.length) throw new Error('NO_DEVICE');
    this.saveDevices(next); this.devices = next; this.invitation = null; this.pending = null;
    for (const [client, meta] of this.clients) if (meta.id === id) this.drop(client);
    this.emit('update'); return this.snapshot();
  }
  reject(nonce) { if (this.pending?.nonce !== nonce) throw new Error('QR_EXPIRED'); this.invitation = null; this.pending = null; this.emit('update'); }
  telemetry(root = this.share?.root) {
    const base = {battery: -1, charging: -1, free: -1, total: -1, uptime: Math.floor(os.uptime())};
    try { const st = fs.statfsSync(root); const free = st.bsize * st.bavail, total = st.bsize * st.blocks; if (Number.isSafeInteger(free) && Number.isSafeInteger(total) && free >= 0 && free <= total) Object.assign(base, {free, total}); } catch {}
    if (this.customTelemetry) Object.assign(base, this.customTelemetry());
    for (const k of ['battery','charging','free','total','uptime']) if (!Number.isSafeInteger(base[k]) || base[k] < -1) base[k] = -1;
    if (base.battery > 100) base.battery = -1; if (base.charging > 1) base.charging = -1;
    return base;
  }
  snapshot() {
    const valid = this.invitation && this.invitation.expires > Date.now();
    return {running: !!this.instance, port: this.port || 8022, user: this.user || 'localls', fingerprint: this.pin || '', root: this.share?.root || '', received: this.received, sent: this.sent, startedAt: this.startedAt || null,
      pending: valid && this.pending ? {...this.pending} : null, invitationNonce: valid ? this.invitation.nonce : null,
      devices: this.devices.map(d => ({id: d.id, label: d.label, addedAt: d.addedAt, online: [...this.clients.values()].some(c => c.id === d.id)})),
      metrics: this.share ? this.telemetry() : null, history: this.history.filter(h => !h.pending).slice(-5)};
  }
  async stop() {
    const instance = this.instance; this.instance = null; this.invitation = null; this.pending = null;
    for (const client of this.clients.keys()) this.drop(client); this.clients.clear();
    if (instance) await new Promise(resolve => instance.close(resolve)); this.emit('update');
  }
}
module.exports = {EmbeddedServer, publicIdentity};
