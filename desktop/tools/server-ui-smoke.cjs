'use strict';
const {_electron: electron} = require('playwright');
const assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path'), os = require('node:os'), crypto = require('node:crypto'), net = require('node:net');
const {PNG} = require('pngjs');
const pairing = require('../src/pairing.cjs'), security = require('../src/security.cjs');
const {Server, Transfers, call} = require('../src/core.cjs');
const root = path.resolve(process.env.LOCALLS_TEST_APP || path.join(__dirname, '..'));
const wait = async fn => { const until = Date.now() + 20000; while (Date.now() < until) { if (await fn()) return; await new Promise(r => setTimeout(r, 40)); } throw new Error('Server GUI condition timed out'); };
const freePort = async () => { const s = net.createServer(); await new Promise(r => s.listen(0, '127.0.0.1', r)); const p = s.address().port; await new Promise(r => s.close(r)); return p; };

(async () => {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'localls-server-gui-')), data = path.join(temp, 'data'), shared = path.join(temp, 'shared'), nextShared = path.join(temp, 'chosen');
  for (const dir of [data, shared, nextShared]) fs.mkdirSync(dir);
  const port = await freePort(), storageKey = crypto.randomBytes(32).toString('hex'), errors = [];
  fs.writeFileSync(path.join(data, 'server.json'), JSON.stringify({port, user: 'localls', root: shared, devices: []}));
  const out = path.resolve(process.env.LOCALLS_UI_OUTPUT || 'artifacts/interface-1.33-20261028'); fs.mkdirSync(out, {recursive: true});
  let app, page, client = new Server();
  const launch = async () => {
    app = await electron.launch({args: [...(process.platform === 'win32' ? [] : ['--no-sandbox']), '--disable-gpu', '--user-data-dir=' + data, root], env: {...process.env, DISPLAY: process.env.DISPLAY || ':99'}, timeout: 30000});
    page = await app.firstWindow(); page.on('pageerror', e => errors.push(e.message));
    await page.waitForFunction(() => document.getElementById('hostAddress').textContent !== '—');
    // The OS vault is substituted only in the test. Production uses Electron safeStorage / Windows DPAPI.
    await app.evaluate(({safeStorage, dialog}, args) => {
      const crypto = process.getBuiltinModule('node:crypto'), raw = Buffer.from(args.key, 'hex');
      safeStorage.isEncryptionAvailable = () => true; safeStorage.getSelectedStorageBackend = () => 'test_encrypted';
      safeStorage.encryptString = text => { const iv = crypto.randomBytes(12), cipher = crypto.createCipheriv('aes-256-gcm', raw, iv); return Buffer.concat([iv, cipher.update(text, 'utf8'), cipher.final(), cipher.getAuthTag()]); };
      safeStorage.decryptString = buffer => { const decipher = crypto.createDecipheriv('aes-256-gcm', raw, buffer.subarray(0, 12)); decipher.setAuthTag(buffer.subarray(-16)); return Buffer.concat([decipher.update(buffer.subarray(12, -16)), decipher.final()]).toString('utf8'); };
      dialog.showMessageBox = async () => ({response: 1});
      dialog.showOpenDialog = async () => ({canceled: false, filePaths: [args.folder]});
    }, {key: storageKey, folder: nextShared});
  };
  const screenshot = async name => { await page.waitForTimeout(180); await page.screenshot({path: path.join(out, name + '.png')}); };
  try {
    await launch();
    assert.equal(await page.evaluate(() => typeof window.require), 'undefined');
    assert.equal(await page.locator('#roleServer').getAttribute('aria-selected'), 'true');
    assert.equal(await page.locator('[data-drawer=vpn]').count(), 0); assert.equal(await page.locator('#shape').count(), 0);
    await screenshot('01-server-off-dark');
    await page.locator('#hostToggle').click(); await page.locator('#hostState').filter({hasText: 'Сервер работает'}).waitFor();
    const initial = await page.evaluate(() => window.localLS.serverState()); assert.equal(initial.ok, true); assert.equal(initial.data.port, port);
    assert.equal(await page.locator('#chooseSharedFolder').isDisabled(), true);
    const sealed = fs.readFileSync(path.join(data, 'server-host.bin')); assert.ok(!sealed.includes(Buffer.from('PRIVATE KEY')));
    await page.locator('#toggleHostQr').click(); await page.locator('#hostQrImage').waitFor({state: 'visible'});
    const encoded = await page.locator('#hostQrImage').getAttribute('src'), png = PNG.sync.read(Buffer.from(encoded.split(',')[1], 'base64'));
    const code = pairing.decodePixels(png.data, png.width, png.height); assert.equal(code.port, port); assert.equal(code.pin, initial.data.fingerprint);
    const identity = await security.generateIdentity();
    assert.equal(await pairing.loginAttempt(code, identity, true), false);
    await page.locator('#pendingDeviceCard').waitFor({state: 'visible'});
    assert.equal(await page.locator('#pendingDeviceCode').textContent(), pairing.syncCode(code, identity.publicKey));
    assert.equal(await pairing.loginAttempt(code, identity), false); // Being visible is not approval.
    await screenshot('02-server-request');
    await page.locator('#approveHostDevice').click(); await page.locator('#hostDeviceCount').filter({hasText: '1 из 7'}).waitFor();
    await client.connect({...code, folder: '/'}, {method: 'key', privateKey: identity.privateKey}, code.pin);
    await page.locator('.device-state.online').waitFor();
    const source = path.join(temp, 'from-phone.bin'), bytes = crypto.randomBytes(256 * 1024); fs.writeFileSync(source, bytes);
    const queue = new Transfers(client), ids = queue.add([{direction: 'upload', local: source, remote: '/from-phone.bin', label: 'from-phone.bin'}]);
    await wait(() => queue.items.find(j => j.id === ids[0]).state === 'completed');
    assert.deepEqual(fs.readFileSync(path.join(shared, 'from-phone.bin')), bytes);
    await page.locator('#hostRecent').filter({hasText: 'from-phone.bin'}).waitFor({timeout: 8000});
    const publicState = await page.evaluate(() => window.localLS.state()); assert.ok(!JSON.stringify(publicState).includes('privateKey')); assert.ok(!JSON.stringify(publicState).includes('PRIVATE KEY'));
    assert.equal(await page.locator('#hostDeviceCount').textContent(), '1 из 7'); await screenshot('03-server-on-dark');
    await page.locator('#toggleHostQr').click(); await page.locator('#hostQrImage').waitFor({state: 'visible'}); await screenshot('04-server-qr-on');
    await page.locator('#toggleHostQr').click(); assert.equal(await page.locator('#hostQrImage').isVisible(), false); assert.equal(await page.locator('#hostQrImage').getAttribute('src'), null);
    client.disconnect(); await page.locator('#hostToggle').click(); await page.locator('#hostState').filter({hasText: 'Сервер выключен'}).waitFor();
    assert.deepEqual(fs.readFileSync(path.join(data, 'server-host.bin')), sealed);
    await app.close(); app = null; await launch();
    assert.equal(await page.locator('#hostDeviceCount').textContent(), '1 из 7'); // Trusted devices also remain visible while off.
    await page.locator('#hostToggle').click(); await page.locator('#hostState').filter({hasText: 'Сервер работает'}).waitFor();
    const restored = await page.evaluate(() => window.localLS.serverState()); assert.equal(restored.data.fingerprint, code.pin);
    assert.equal(await pairing.loginAttempt(code, identity), true); assert.deepEqual(fs.readFileSync(path.join(data, 'server-host.bin')), sealed);
    await page.locator('.device-row button').click(); await page.locator('#hostDeviceCount').filter({hasText: '0 из 7'}).waitFor(); assert.equal(await pairing.loginAttempt(code, identity), false);
    await page.locator('#hostToggle').click(); await page.locator('#hostState').filter({hasText: 'Сервер выключен'}).waitFor();
    await page.locator('#chooseSharedFolder').click(); assert.equal((await page.evaluate(() => window.localLS.serverState())).data.root, fs.realpathSync(nextShared));
    await page.locator('[data-drawer=settings]').first().click(); await page.locator('#lightMode').check(); await page.locator('#glassMode').check(); await page.locator('#outlineMode').check(); await page.locator('#theme').selectOption('red'); await page.locator('#saveSettings').click(); await page.locator('#saveStatus').filter({hasText: 'Сохранено'}).waitFor(); await page.locator('#closeDrawer').click(); await screenshot('05-server-light-glass-red');
    await page.locator('[data-drawer=about]').first().click(); await page.locator('[data-panel=about]').filter({hasText: 'MSS9LN'}).waitFor(); assert.equal(await page.locator('#appVersion').textContent(), '1.33.0'); await screenshot('06-about'); await page.locator('#closeDrawer').click();
    assert.deepEqual(errors, []);
    console.log('PASS: real Electron server start/stop, public full-square QR, signed request, owner approval, actual SFTP, final receipt, hide/show, persistent encrypted host identity and devices, restart/revocation, root chooser, appearance and MSS9LN About. OS vault substituted; no physical Windows claim.');
  } catch (error) {
    if (page && !page.isClosed()) { await page.screenshot({path: path.join(out, 'server-ui-failure.png')}).catch(() => {}); console.error('Page errors:', JSON.stringify(errors)); console.error('Toast:', await page.locator('#toast').textContent().catch(() => '')); }
    throw error;
  } finally { client.disconnect(); await app?.close(); fs.rmSync(temp, {recursive: true, force: true}); }
})().catch(error => { console.error(error.stack); process.exitCode = 1; });
