'use strict';
const {app,BrowserWindow,ipcMain,dialog,Tray,Menu,nativeImage,safeStorage,shell,powerSaveBlocker,powerMonitor,nativeTheme,protocol} = require('electron');
require('./runtime-policy.cjs').configureRuntime(app);
protocol.registerSchemesAsPrivileged([{scheme:'localls-preview',privileges:{standard:true,secure:true,supportFetchAPI:true,stream:true}}]);
const {FilePreview,previewConnection}=require('./file-preview.cjs');let previewStore;
const fs=require('node:fs'); const path=require('node:path'); const {execFile}=require('node:child_process');
const {profile,endpoint,name,remotePath,safeDownloadName,call,publicError,Server,Transfers}=require('./core.cjs');
const security=require('./security.cjs');
const pairing=require('./pairing.cjs');
const os=require('node:os'),QRCode=require('qrcode');
const {EmbeddedServer}=require('./embedded-server.cjs'),serverVault=require('./server-vault.cjs');
const release=require('../release.json');
const {windowOptions,applyWindowMaterial,supportsAcrylic}=require('./window-material.cjs');
let nativeGlass=false;
const {ActivityPower}=require('./activity-power.cjs'),{WindowsBattery}=require('./system-metrics.cjs');
const clientPower=new ActivityPower(powerSaveBlocker),serverPower=new ActivityPower(powerSaveBlocker);
const batteryReader=new WindowsBattery({changed:()=>{if(hostConfig)notify('hosting',hostView());}});
function localBattery(){let charging=-1;if(process.platform==='win32')try{charging=powerMonitor.isOnBatteryPower()?0:1;}catch{}return {battery:batteryReader.read(),charging};}
const themes=['violet','ocean','forest','amber','red','turquoise','silver','indigo'];
let hostConfig,hostConfigError='',hostStarting=false;
const hostServer=new EmbeddedServer({telemetry:localBattery,saveDevices:devices=>{const next={...hostConfig,devices};write('server.json',next);hostConfig=next;}});
let qrRevision=0,qrProgress=null;
const page=path.join(__dirname,'index.html'); const icon=path.join(__dirname,'../assets/icon.png');
let securityBusy=false,clientVaultError=false,planning=false,queueWriteWarning=false;
const keyOperations=new Set(['generateIdentity','importIdentity','removeIdentity','installIdentity','importQrImage']);
let window, tray, quitting=false, settings, secretMemory={}, localRoot, attachments=new Set(), connecting=false;
const server=new Server(); const transfers=new Transfers(server);
const defaultSettings={profile:{host:'localhost',port:8022,user:'localls',folder:'/',fingerprint:''},pins:{},theme:'violet',light:false,glass:false,outline:'theme',appearanceVersion:2,shape:'rounded',route:'local',vpnHost:'',agentPort:8787,remember:false,authMethod:'key',role:'server',agentPins:{},legacyAgentHTTP:false};
if(app.commandLine.hasSwitch('user-data-dir'))app.setPath('userData',app.commandLine.getSwitchValue('user-data-dir'));
function identityKey(){return authKey(profile(settings.profile));}
function credential(p){if(settings.authMethod==='key'){const key=secrets()[identityKey()];if(!key?.privateKey)throw new Error('KEY_MISSING');return {method:'key',privateKey:key.privateKey,passphrase:key.passphrase||''};}return {method:'password',password:secrets()[authKey(p)]?.password||''};}
function identityInfo(){const key=secrets()[identityKey()];if(!key?.privateKey)return {hasKey:false};try{return {hasKey:true,...security.inspectIdentity(key.privateKey,key.passphrase||'')};}catch{return {hasKey:false};}}
function authKey(p){return endpoint(p)+'@'+p.user;}
function file(n){return path.join(app.getPath('userData'),n);}
function read(n,fallback){try{return JSON.parse(fs.readFileSync(file(n),'utf8'));}catch{return fallback;}}
function atomicFile(target,data){fs.mkdirSync(path.dirname(target),{recursive:true});const temp=target+'.'+require('node:crypto').randomUUID()+'.tmp';try{fs.writeFileSync(temp,data,{mode:0o600,flag:'wx'});fs.renameSync(temp,target);}finally{fs.rmSync(temp,{force:true});}}
function write(n,v){atomicFile(file(n),JSON.stringify(v,null,2));}
function vaultAvailable(){return safeStorage.isEncryptionAvailable() && (process.platform==='win32' || safeStorage.getSelectedStorageBackend()!=='basic_text');}
function secrets(){
 if(Object.keys(secretMemory).length)return secretMemory;
 if(!fs.existsSync(file('vault.bin'))){clientVaultError=false;return secretMemory;}
 try{if(!vaultAvailable())throw new Error();const values=JSON.parse(safeStorage.decryptString(fs.readFileSync(file('vault.bin'))));if(!values||typeof values!=='object'||Array.isArray(values))throw new Error();secretMemory=values;clientVaultError=false;}
 catch{clientVaultError=true;}return secretMemory;
}
function assertReadableVault(){if(fs.existsSync(file('vault.bin'))){secrets();if(clientVaultError)throw new Error('CLIENT_VAULT_UNREADABLE');}}
function saveSecrets(value){assertReadableVault();secretMemory=value;if(settings.remember&&vaultAvailable())atomicFile(file('vault.bin'),safeStorage.encryptString(JSON.stringify(value)));else fs.rmSync(file('vault.bin'),{force:true});}
function notify(type,data){if(window&&!window.isDestroyed())window.webContents.send('localls:'+type,data);}
function applyAppearance(){nativeTheme.themeSource=settings.light?'light':'dark';nativeGlass=applyWindowMaterial(window,settings);}
function view(){return {nativeGlass,nativeGlassSupported:supportsAcrylic(),release:{version:release.version,displayVersion:release.displayVersion,author:release.author},hosting:hostView(),qrProgress,identity:identityInfo(),vaultUnreadable:clientVaultError,settings,localRoot,connected:!!server.sftp,remotePath:server.location,vaultAvailable:vaultAvailable(),hasPassword:!!secrets()[authKey(effectiveProfile())]?.password,transfers:transfers.snapshot(),networkBytes:transfers.networkBytes,queuePending:transfers.items.filter(j=>['running','queued'].includes(j.state)).length,platform:process.platform};}
function effectiveProfile(){return profile({...settings.profile,host:settings.route==='vpn'?settings.vpnHost:settings.profile.host});}
async function confirm(message,detail=''){return (await dialog.showMessageBox(window,{type:'question',buttons:['Отмена','Продолжить'],defaultId:0,cancelId:0,message,detail})).response===1;}
function busy(){if(transfers.running||planning)throw new Error('BUSY');}
function allowedLocal(p){const resolved=path.resolve(String(p));const rel=path.relative(localRoot,resolved);if(rel.startsWith('..'+path.sep)||rel==='..'||path.isAbsolute(rel))throw new Error('LOCAL_DENIED');const real=fs.realpathSync(resolved);const rr=path.relative(localRoot,real);if(rr==='..'||rr.startsWith('..'+path.sep)||path.isAbsolute(rr))throw new Error('LOCAL_DENIED');return real;}
async function localList(p){const folder=allowedLocal(p||localRoot);const entries=await fs.promises.readdir(folder,{withFileTypes:true});return {path:folder,root:localRoot,entries:await Promise.all(entries.map(async e=>{const full=path.join(folder,e.name);let st;try{st=await fs.promises.stat(full);}catch{}return {name:e.name,path:full,directory:e.isDirectory(),link:e.isSymbolicLink(),size:st?.size||0,modified:st?.mtimeMs||0};})).then(a=>a.sort((a,b)=>Number(b.directory)-Number(a.directory)||a.name.localeCompare(b.name)))};}
function register(action,fn){ipcMain.handle('localls:'+action,async(event,arg)=>{if(event.sender!==window?.webContents||event.senderFrame?.url!==require('node:url').pathToFileURL(page).href)throw new Error('IPC_DENIED');let ownsLock=false;try{if(securityBusy&&!['state','serverState','trafficSnapshot','saveAppearance','setRole','stats','remoteList','localList','cancel','tray'].includes(action))throw new Error('BUSY');if(['connect','disconnect','importProfile','forgetKey','generateIdentity','removeIdentity','importIdentity'].includes(action))qrRevision++;if(keyOperations.has(action)){if(transfers.running)throw new Error('BUSY');securityBusy=true;ownsLock=true;}return {ok:true,data:await fn(arg)};}catch(err){return {ok:false,error:err?.message==='BUSY'?'Дождитесь окончания передач или отмените их':publicError(err)};}finally{if(ownsLock)securityBusy=false;}});}
function qrState(value){qrProgress=value;notify('qr',value);}
function qrCurrent(code,revision){const p=effectiveProfile();return revision===qrRevision&&p.host===code.host&&p.port===code.port&&p.user===code.user&&settings.pins[endpoint(p)]===code.pin;}
async function resumeQr(code){
 const revision=++qrRevision,p=profile({...code,folder:'.',fingerprint:code.pin}),id=authKey(p),identity=secrets()[id];
 if(!identity?.privateKey)return qrState({status:'Ключ недоступен. Проверь системное защищённое хранилище.'});
 const original=security.inspectIdentity(identity.privateKey,identity.passphrase||'').fingerprint;
 qrState({status:'Сверь код на телефоне-сервере и один раз разреши вход.',code:pairing.syncCode(code,security.inspectIdentity(identity.privateKey,identity.passphrase||'').publicKey)});
 while(qrCurrent(code,revision)){
  try{
   const current=secrets()[id];if(!current?.privateKey||security.inspectIdentity(current.privateKey,current.passphrase||'').fingerprint!==original)return qrState({status:'Ключ клиента изменился. Прежний доступ не заменён.'});
   let approved=await pairing.loginAttempt(code,current);
   if(!approved&&Date.now()<code.expires)approved=await pairing.loginAttempt(code,current,true);
   if(!qrCurrent(code,revision))return;
   if(approved){
    const next={...settings};delete next.qrPending;write('settings.json',next);settings=next;
    await server.connect(p,{method:'key',privateKey:current.privateKey,passphrase:current.passphrase||''},code.pin);
    if(!qrCurrent(code,revision)){server.disconnect();return;}
    qrState({status:'Устройство привязано. Повторный QR не нужен.',connected:true});notify('connection',{connected:true});return;
   }
  }catch(error){if(!qrCurrent(code,revision))return;if(error.code==='HOST_KEY_CHANGED')return qrState({status:'Ключ сервера изменился. Подключение заблокировано.'});}
  if(Date.now()>=code.expires){qrState({status:'Адрес и ключ сохранены. Разрешённое устройство подключается обычной кнопкой. Для ещё не подтверждённого устройства нужен действующий QR.'});return;}
  await new Promise(resolve=>setTimeout(resolve,1200));
 }
}
register('importQrImage',async()=>{
 busy();if(connecting)throw new Error('BUSY');
 const selected=await dialog.showOpenDialog(window,{title:'Считать QR LocalLS из изображения',properties:['openFile'],filters:[{name:'Фото и скриншоты QR',extensions:['png','jpg','jpeg','webp','bmp']}]});
 if(selected.canceled)return null;const source=selected.filePaths[0],stat=fs.statSync(source);if(!stat.isFile()||stat.size>32*1024*1024)throw new Error('QR_IMAGE_INVALID');
 const code=pairing.imageCode(nativeImage.createFromBuffer(fs.readFileSync(source))),p=profile({...code,folder:'.',fingerprint:code.pin}),old=settings.pins[endpoint(p)];
 if(old&&old!==code.pin)throw Object.assign(new Error('CHANGED'),{code:'HOST_KEY_CHANGED'});
 if(!vaultAvailable())throw new Error('QR_VAULT_REQUIRED');
 if(!old&&!await confirm('Подключиться к серверу из QR?',p.host+':'+p.port+' · '+p.user+'\n'+code.pin+'\nСверь ключ на телефоне-сервере. Ключ компьютера сохраним в системном защищённом хранилище.'))return null;
 let loaded=secrets();if(!Object.keys(loaded).length&&fs.existsSync(file('vault.bin'))){try{loaded=JSON.parse(safeStorage.decryptString(fs.readFileSync(file('vault.bin'))));if(!loaded||typeof loaded!=='object'||Array.isArray(loaded))throw new Error();}catch{throw new Error('QR_VAULT_UNREADABLE');}}
 const values={...loaded},id=authKey(p);if(!Object.hasOwn(values[id]||{},'privateKey')){const identity=await security.generateIdentity();values[id]={...values[id],privateKey:identity.privateKey,passphrase:''};}else security.inspectIdentity(values[id].privateKey,values[id].passphrase||'');
 const next={...settings,profile:p,route:'local',authMethod:'key',remember:true,qrManaged:true,pins:{...settings.pins,[endpoint(p)]:code.pin},qrPending:code};
 if(code.expires<=Date.now())throw new Error('QR_EXPIRED');
 atomicFile(file('vault.bin'),safeStorage.encryptString(JSON.stringify(values)));write('settings.json',next);secretMemory=values;settings=next;server.disconnect();notify('connection',{connected:false});
 resumeQr(code).catch(()=>qrState({status:'Параметры сохранены. Проверь доступность сервера.'}));return view();
});

function hostAddresses(){
 const values=[];
 for(const entries of Object.values(os.networkInterfaces()))for(const address of entries||[])if(address.family==='IPv4'&&!address.internal&&!address.address.startsWith('169.254.'))values.push(address.address);
 return [...new Set(values)];
}
function hostView(){return {...hostServer.snapshot(),root:hostConfig?.root||'',port:hostConfig?.port||8022,user:hostConfig?.user||'localls',addresses:hostAddresses(),vaultAvailable:serverVault.available(safeStorage),configError:hostConfigError,metrics:hostConfig?hostServer.telemetry(hostConfig.root):null};}
register('serverState',hostView);
register('serverStart',async()=>{
 if(hostStarting)throw new Error('BUSY');if(hostConfigError)throw new Error(hostConfigError);hostStarting=true;
 try{
  if(hostConfig.root===path.join(app.getPath('userData'),'shared'))fs.mkdirSync(hostConfig.root,{recursive:true});
  const identity=await serverVault.hostIdentity(file('server-host.bin'),safeStorage);
  write('server.json',hostConfig);await hostServer.start({...hostConfig,privateKey:identity.privateKey});return hostView();
 }finally{hostStarting=false;}
});
register('serverStop',async()=>{
 if(hostStarting)throw new Error('BUSY');
 if(hostServer.snapshot().devices.some(d=>d.online)&&!await confirm('Остановить сервер компьютера?','Подключённые устройства и их передачи будут отключены. Ключи и разрешения сохранятся.'))return hostView();
 await hostServer.stop();return hostView();
});
register('serverConfigure',arg=>{
 if(hostServer.instance||hostStarting)throw new Error('SERVER_STOP_FIRST');if(hostConfigError)throw new Error(hostConfigError);
 const port=Number(arg?.port),user=String(arg?.user||'');profile({host:'localhost',port,user,folder:'/'});
 const next={...hostConfig,port,user};write('server.json',next);hostConfig=next;return hostView();
});
register('chooseSharedFolder',async()=>{
 if(hostServer.instance||hostStarting)throw new Error('SERVER_STOP_FIRST');if(hostConfigError)throw new Error(hostConfigError);
 const result=await dialog.showOpenDialog(window,{title:'Папка доступа Windows-сервера',properties:['openDirectory'],defaultPath:hostConfig.root});
 if(result.canceled)return hostView();const root=fs.realpathSync(result.filePaths[0]);const next={...hostConfig,root};write('server.json',next);hostConfig=next;return hostView();
});
register('openSharedFolder',async()=>{if(hostConfigError)throw new Error(hostConfigError);if(!fs.existsSync(hostConfig.root))fs.mkdirSync(hostConfig.root,{recursive:true});const error=await shell.openPath(hostConfig.root);if(error)throw new Error('SHARE_OPEN');return true;});
register('serverQr',async arg=>{
 const addresses=hostAddresses();const host=String(arg?.host||addresses[0]||'');if(!addresses.includes(host))throw new Error('SERVER_NO_NETWORK');
 const offer=hostServer.invite(host),image=await QRCode.toDataURL(JSON.stringify(offer),{width:720,margin:4,errorCorrectionLevel:'M'});return {image,expires:offer.expires,nonce:offer.nonce};
});
register('saveServerQr',async arg=>{
 const addresses=hostAddresses(),host=String(arg?.host||addresses[0]||'');if(!addresses.includes(host))throw new Error('SERVER_NO_NETWORK');
 const offer=hostServer.invite(host);const result=await dialog.showSaveDialog(window,{defaultPath:'LocalLS-Windows-QR.png',filters:[{name:'QR-код LocalLS',extensions:['png']}]});if(result.canceled)return false;
 const image=await QRCode.toBuffer(JSON.stringify(offer),{width:720,margin:4,errorCorrectionLevel:'M'});fs.writeFileSync(result.filePath,image);return true;
});
register('approveDevice',async arg=>{
 const pending=hostServer.snapshot().pending;if(!pending||pending.nonce!==arg?.nonce||pending.id!==arg?.id)throw new Error('QR_EXPIRED');
 if(!await confirm('Коды на устройствах совпадают?',pending.code+'\n\nКлюч устройства:\n'+pending.id+'\n\nСверь этот код с экраном подключаемого телефона.'))return hostView();
 hostServer.approve(pending.nonce,pending.publicKey);return hostView();
});
register('rejectDevice',arg=>{hostServer.reject(String(arg?.nonce||''));return hostView();});
register('revokeDevice',async id=>{
 const device=hostServer.snapshot().devices.find(d=>d.id===id);if(!device)throw new Error('NO_DEVICE');
 if(!await confirm('Отозвать доступ устройства?',device.label+'\n'+device.id+'\nТекущие соединения закроются. Для повторного входа потребуется новый QR.'))return hostView();
 hostServer.revoke(id);return hostView();
});
hostServer.on('update',()=>notify('hosting',hostView()));
hostServer.on('notice',()=>notify('notice',{message:'Сервер компьютера недоступен. Проверь порт и выбранную папку.'}));
hostServer.on('traffic',()=>serverPower.pulse());

register('saveAppearance',arg=>{if(!arg||!themes.includes(arg.theme)||typeof arg.light!=='boolean'||typeof arg.glass!=='boolean'||!['theme','light'].includes(arg.outline))throw new Error('BAD_APPEARANCE');const next={...settings,theme:arg.theme,light:arg.light,glass:arg.glass,outline:arg.outline,appearanceVersion:2};write('settings.json',next);settings=next;applyAppearance();return view();});
register('setRole',role=>{if(!['client','server'].includes(role))throw new Error('BAD_ROLE');const next={...settings,role};write('settings.json',next);settings=next;return true;});
function transferBinding(){const p=effectiveProfile();return authKey(p)+'|'+(settings.pins[endpoint(p)]||'');}
register('cancelAll',async()=>{
 const count=transfers.items.filter(j=>['queued','running'].includes(j.state)).length;
 if(!count)return 0;
 if(!await confirm('Отменить все передачи?',count+' заданий. Завершённые файлы сохранятся.'))return 0;
 return transfers.cancelAll();
});
register('clearQueue',async()=>{
 if(planning)throw new Error('BUSY');if(!transfers.items.length)return 0;
 if(!await confirm('Очистить весь список передач?','Незавершённые задания будут отменены, очередь и история очищены. Файлы на устройствах сохранятся.'))return null;
 return transfers.clearAll();
});
register('previewOpen',async arg=>{
 if(!previewStore||!arg||typeof arg.path!=='string'||arg.path.length>4096)throw new Error('PREVIEW_FAILED');
 const label=path.posix.basename(arg.path.replaceAll('\\','/'));
 if(arg.side==='local')return previewStore.open({requestId:arg.requestId,name:label,local:allowedLocal(arg.path)});
 if(arg.side!=='remote')throw new Error('PREVIEW_FAILED');
 server.requireSftp();const client=server.client;
 const p=effectiveProfile(),auth=credential(p),pin=settings.pins[endpoint(p)];
 return previewStore.open({requestId:arg.requestId,name:label,remote:remotePath(arg.path),connect:signal=>previewConnection(p,auth,pin,signal),isCurrent:()=>server.client===client});
});
register('previewClose',arg=>previewStore?.close(arg?.requestId)||false);
register('retry',id=>{server.requireSftp();const job=transfers.items.find(j=>j.id===id);if(!job||!['failed','cancelled'].includes(job.state)||!job.binding||job.binding!==transferBinding())throw new Error('RETRY_PROFILE');const existing=transfers.items.find(j=>j.retryOf===id&&['running','queued'].includes(j.state));if(existing)return [existing.id];const {controller,id:oldId,state:oldState,bytes:oldBytes,speed:oldSpeed,error:oldError,finished:oldFinished,started:oldStarted,...spec}=job;return transfers.add([{...spec,retryOf:id}]);});

register('trafficSnapshot',()=>({client:transfers.traffic.snapshot(),server:hostServer.traffic.snapshot(),pending:transfers.items.filter(j=>['running','queued'].includes(j.state)).length}));
register('state',view);
register('saveSettings',async arg=>{busy();assertReadableVault();const previous=effectiveProfile();const previousAuth=settings.authMethod;const p=profile(arg.profile);if(typeof arg.password==='string'&&arg.password.length>4096)throw new Error('PASSWORD_LONG');if(typeof arg.agentToken==='string'&&arg.agentToken.length>4096)throw new Error('TOKEN_LONG');const theme=themes.includes(arg.theme)?arg.theme:arg.theme==='dark'?'violet':'violet';const shape='rounded';const route=arg.route==='vpn'?'vpn':'local';const vpnHost=String(arg.vpnHost||'').trim();if(vpnHost)profile({...p,host:vpnHost});const port=Number(arg.agentPort||8787);if(!Number.isInteger(port)||port<1||port>65535)throw new Error('INVALID_PORT');settings={...settings,profile:p,theme,shape,light:arg.light===true,glass:arg.glass===true,outline:arg.outline==='light'?'light':'theme',appearanceVersion:2,route,vpnHost,agentPort:port,remember:!!arg.remember&&vaultAvailable(),authMethod:arg.authMethod==='key'?'key':'password',legacyAgentHTTP:arg.legacyAgentHTTP===true};const next=effectiveProfile();if(previousAuth!==settings.authMethod||endpoint(previous)!==endpoint(next)||previous.user!==next.user||previous.folder!==next.folder||typeof arg.password==='string'&&arg.password.length){qrRevision++;server.disconnect();notify('connection',{connected:false});}const key=authKey(next);const values={...secrets()};if(typeof arg.password==='string'&&arg.password.length){values[key]={...values[key],password:arg.password};}if(typeof arg.agentToken==='string'&&arg.agentToken.length){values[key]={...values[key],agentToken:arg.agentToken};}saveSecrets(values);write('settings.json',settings);applyAppearance();return view();});
function requireCurrentConnection(revision){if(qrRevision!==revision)throw new Error('CONNECTION_CHANGED');}
register('connect',async()=>{busy();if(connecting)throw new Error('BUSY');connecting=true;try{const revision=qrRevision,p=effectiveProfile();let pin=settings.pins[endpoint(p)];const observed=await server.probe(p);requireCurrentConnection(revision);if(pin&&pin!==observed)throw Object.assign(new Error('CHANGED'),{code:'HOST_KEY_CHANGED'});if(!pin){if(!await confirm('Доверять этому SSH-серверу?',`${p.host}:${p.port}\n${observed}\nСверьте отпечаток с сервером по доверенному каналу.`))return {cancelled:true};requireCurrentConnection(revision);pin=observed;settings.pins[endpoint(p)]=pin;write('settings.json',settings);}const result=await server.connect(p,credential(p),pin);requireCurrentConnection(revision);if(settings.qrManaged&&settings.qrPending&&settings.qrPending.host===p.host&&settings.qrPending.port===p.port&&settings.qrPending.user===p.user&&settings.qrPending.pin===pin){delete settings.qrPending;write('settings.json',settings);qrState({status:'Устройство привязано. Повторный QR не нужен.',connected:true});}notify('connection',{connected:true});return result;}finally{connecting=false;}});
register('disconnect',()=>{busy();server.disconnect();return true;});
register('remoteList',p=>server.list(remotePath(p)));
register('stats',()=>server.stats());
register('localList',p=>localList(p));
register('chooseLocal',async()=>{const r=await dialog.showOpenDialog(window,{properties:['openDirectory'],defaultPath:localRoot});if(r.canceled)return null;localRoot=fs.realpathSync(r.filePaths[0]);return localList(localRoot);});
register('chooseUpload',async()=>{const r=await dialog.showOpenDialog(window,{properties:['openFile','multiSelections']});if(r.canceled)return [];for(const p of r.filePaths)attachments.add(p);return r.filePaths.map(p=>({path:p,name:path.basename(p),size:fs.statSync(p).size}));});
register('download',async arg=>{
 if(planning)throw new Error('BUSY');const files=arg?.files;if(!Array.isArray(files)||!files.length||files.length>1000)throw new Error('BAD_FILES');const sftp=server.requireSftp();planning=true;
 try{const r=await dialog.showOpenDialog(window,{title:'Папка для скачивания',properties:['openDirectory','createDirectory'],defaultPath:localRoot});if(r.canceled)return [];
 const jobs=await require('./transfer-plan.cjs').planDownload({files,folder:fs.realpathSync(r.filePaths[0]),sftp,binding:transferBinding()});if(server.sftp!==sftp)throw new Error('NOT_CONNECTED');return transfers.add(jobs);}finally{planning=false;}
});
register('upload',async arg=>{
 if(planning)throw new Error('BUSY');const files=arg?.files;if(!Array.isArray(files)||!files.length||files.length>1000)throw new Error('BAD_FILES');const sftp=server.requireSftp();planning=true;
 try{const jobs=await require('./transfer-plan.cjs').planUpload({files,folder:arg.folder,sftp,binding:transferBinding(),allowed:p=>attachments.has(p)?fs.realpathSync(p):allowedLocal(p),confirmOverwrite:label=>confirm('Заменить файл на сервере?',label)});if(server.sftp!==sftp)throw new Error('NOT_CONNECTED');return transfers.add(jobs);}finally{planning=false;}
});
register('mkdir',async arg=>{await call(server.requireSftp(),'mkdir',path.posix.join(remotePath(arg.folder),name(arg.name)));return true;});
register('rename',async arg=>{busy();const old=remotePath(arg.path),target=path.posix.join(path.posix.dirname(old),name(arg.name));const s=server.requireSftp();try{await call(s,'lstat',target);throw new Error('EXISTS');}catch(e){if(e.code!==2)throw e;}if(!await confirm('Переименовать?',`${path.posix.basename(old)} → ${arg.name}`))return false;await call(s,'rename',old,target);return true;});
register('remove',async arg=>{busy();const p=remotePath(arg.path);const s=server.requireSftp();const st=await call(s,'lstat',p);if(!await confirm('Удалить с сервера?',path.posix.basename(p)+'\nПапки удаляются только пустыми.'))return false;await call(s,st.isDirectory()?'rmdir':'unlink',p);return true;});
register('cancel',id=>{transfers.cancel(id);return true;});
register('forgetKey',async()=>{busy();const p=effectiveProfile();if(!await confirm('Забыть ключ SSH?',`${p.host}:${p.port}\nПри следующем подключении потребуется сверить новый ключ.`))return false;server.disconnect();delete settings.pins[endpoint(p)];write('settings.json',settings);return true;});
register('importProfile',async()=>{busy();const r=await dialog.showOpenDialog(window,{filters:[{name:'Профиль LocalLS',extensions:['json']}]});if(r.canceled)return null;const f=r.filePaths[0];if(fs.statSync(f).size>64000)throw new Error('PROFILE_LARGE');const input=JSON.parse(fs.readFileSync(f,'utf8'));if(input.format!=='localls-server-v1')throw new Error('PROFILE_FORMAT');const p=profile(input);const old=settings.pins[endpoint(p)];if(old&&p.fingerprint&&old!==p.fingerprint)throw Object.assign(new Error('CHANGED'),{code:'HOST_KEY_CHANGED'});if(p.fingerprint&&!old){if(!await confirm('Доверять ключу из импортируемого профиля?',`${p.host}:${p.port}\n${p.fingerprint}\nИмпортируйте профиль только из доверенного источника.`))return null;settings.pins[endpoint(p)]=p.fingerprint;}server.disconnect();settings.profile=p;settings.route='local';write('settings.json',settings);return view();});
register('exportProfile',async()=>{const p=effectiveProfile();p.fingerprint=settings.pins[endpoint(p)]||'';const r=await dialog.showSaveDialog(window,{defaultPath:'LocalLS-server.json',filters:[{name:'Профиль LocalLS',extensions:['json']}]});if(r.canceled)return false;fs.writeFileSync(r.filePath,JSON.stringify(p,null,2));return true;});
register('generateIdentity',async()=>{busy();if(!await confirm('Создать SSH-ключ RSA 3072?',(identityInfo().hasKey?'Текущий локальный ключ будет заменён. ':'')+(vaultAvailable()?'Для постоянного хранения включите «Сохранить секреты».':'Связка ключей недоступна: новый ключ останется только в памяти до выхода.')))return null;const key=await security.generateIdentity();saveSecrets({...secrets(),[identityKey()]:{...secrets()[identityKey()],privateKey:key.privateKey,passphrase:''}});return view();});
register('importIdentity',async arg=>{busy();if(typeof arg?.passphrase!=='string'||arg.passphrase.length>4096)throw new Error('KEY_INVALID');const r=await dialog.showOpenDialog(window,{title:'Импорт приватного SSH-ключа',properties:['openFile']});if(r.canceled)return null;const st=fs.statSync(r.filePaths[0]);if(!st.isFile()||st.size>128*1024)throw new Error('KEY_INVALID');const privateKey=fs.readFileSync(r.filePaths[0],'utf8');const info=security.inspectIdentity(privateKey,arg.passphrase);if(!await confirm('Импортировать этот SSH-ключ?',info.fingerprint+'\n'+(identityInfo().hasKey?'Текущий локальный ключ будет заменён. ':'')))return null;saveSecrets({...secrets(),[identityKey()]:{...secrets()[identityKey()],privateKey,passphrase:arg.passphrase}});return view();});
register('exportPublicKey',async()=>{const info=identityInfo();if(!info.hasKey)throw new Error('KEY_MISSING');const r=await dialog.showSaveDialog(window,{defaultPath:'LocalLS-id_rsa.pub',filters:[{name:'Публичный SSH-ключ',extensions:['pub']}]});if(r.canceled)return false;fs.writeFileSync(r.filePath,info.publicKey+'\n',{mode:0o600});return true;});
register('removeIdentity',async()=>{busy();if(!await confirm('Удалить локальный SSH-ключ?','Ключ на сервере останется. Для повторного входа понадобится другой разрешённый ключ или пароль; способ входа автоматически не меняется.'))return null;const values={...secrets()},key={...values[identityKey()]};delete key.privateKey;delete key.passphrase;values[identityKey()]=key;saveSecrets(values);server.disconnect();notify('connection',{connected:false});return view();});
register('installIdentity',async arg=>{busy();const info=identityInfo();if(!info.hasKey)throw new Error('KEY_MISSING');if(typeof arg?.password!=='string'||!arg.password||arg.password.length>4096)throw new Error('KEY_BOOTSTRAP_REQUIRED');const p=effectiveProfile(),observed=await server.probe(p),pin=settings.pins[endpoint(p)];if(!pin)throw new Error('NO_KEY');if(pin!==observed)throw Object.assign(new Error('CHANGED'),{code:'HOST_KEY_CHANGED'});if(!await confirm('Добавить публичный ключ на этот сервер?',p.user+'@'+p.host+':'+p.port+'\n'+info.fingerprint+'\nСуществующие authorized_keys сохраняются. Затем проверим вход только по ключу. Парольный вход сервера не отключается.'))return null;const check=new Server();try{await check.connect(p,{method:'password',password:arg.password},pin);await security.installPublicKey(check.sftp,info.publicKey);check.disconnect();const key=secrets()[identityKey()];await check.connect(p,{method:'key',privateKey:key.privateKey,passphrase:key.passphrase||''},pin);settings.authMethod='key';const values={...secrets()};for(const id of new Set([authKey(p),identityKey()]))if(values[id]){values[id]={...values[id]};delete values[id].password;}saveSecrets(values);write('settings.json',settings);server.disconnect();notify('connection',{connected:false});return view();}finally{check.disconnect();}});
register('tray',()=>{window.hide();return true;});
function journalJobs(items){return items.map(({controller,...job})=>job);}
transfers.beforeEnqueue=items=>{try{write('history.json',journalJobs(items));queueWriteWarning=false;}catch{throw new Error('QUEUE_STORE');}};
transfers.on('update',data=>{if(settings)try{write('history.json',journalJobs(transfers.items));queueWriteWarning=false;}catch{if(!queueWriteWarning)notify('notice',{message:'Не удалось обновить журнал очереди. Освободи место в папке приложения; исходные файлы сохранены.'});queueWriteWarning=true;}notify('transfers',data);});
transfers.on('traffic',data=>notify('traffic',data));
transfers.on('active',active=>{if(active)clientPower.acquire();else clientPower.release();});
server.on('closed',()=>{previewStore?.close();notify('connection',{connected:false});});
if(!app.requestSingleInstanceLock())app.quit();else{
app.on('second-instance',()=>{window?.show();window?.focus();});
app.whenReady().then(()=>{try{hostConfig=serverVault.readConfig(file('server.json'),{port:8022,user:'localls',root:path.join(app.getPath('userData'),'shared'),devices:[]});hostServer.restoreDevices(hostConfig.devices);}catch(error){hostConfigError=error.message;}const stored=read('settings.json',{});settings={...defaultSettings,...stored};if(!Object.hasOwn(stored,'appearanceVersion')){settings.light=stored.theme?stored.theme!=='dark':false;settings.theme=stored.theme==='dark'?'violet':stored.theme||'violet';settings.appearanceVersion=2;}if(!Object.hasOwn(stored,'role'))settings.role=stored.profile?'client':'server';try{settings.profile=profile(settings.profile);effectiveProfile();}catch{settings={...defaultSettings};}localRoot=fs.realpathSync(fs.existsSync(app.getPath('downloads'))?app.getPath('downloads'):app.getPath('home'));transfers.items=read('history.json',[]).filter(j=>['completed','failed','cancelled','running','queued'].includes(j.state)).slice(-1200).map(j=>['running','queued'].includes(j.state)?{...j,state:'failed',speed:0,error:'Не завершено. Подключись к тому же серверу и нажми «Повторить».',recovered:true}:j);
nativeTheme.themeSource=settings.light?'light':'dark';
try{previewStore=new FilePreview(file('preview-cache'));}catch{}
protocol.handle('localls-preview',request=>previewStore?.response(request)||new Response(null,{status:404}));
window=new BrowserWindow({width:1280,height:840,minWidth:960,minHeight:660,title:'LocalLS',icon,...windowOptions(settings),autoHideMenuBar:true,webPreferences:{preload:path.join(__dirname,'preload.cjs'),contextIsolation:true,nodeIntegration:false,sandbox:true,webgl:false,plugins:true}});
applyAppearance();
window.webContents.setWindowOpenHandler(()=>({action:'deny'}));window.webContents.on('will-navigate',(e,url)=>{if(url!==require('node:url').pathToFileURL(page).href)e.preventDefault();});window.webContents.session.setPermissionRequestHandler((_,__,cb)=>cb(false));
window.loadFile(page);if(settings.qrPending&&vaultAvailable()){try{const code=pairing.decode(JSON.stringify(settings.qrPending));resumeQr(code).catch(()=>{});}catch{qrProgress={status:'Параметры и ключ сохранены. Для разрешённого устройства новый QR не нужен.'};}}window.on('close',e=>{if(!quitting){e.preventDefault();window.hide();}});window.on('minimize',()=>window.hide());
const image=nativeImage.createFromPath(icon);tray=new Tray(image.resize({width:24,height:24}));tray.setToolTip('LocalLS — SSH / SFTP');tray.setContextMenu(Menu.buildFromTemplate([{label:'Открыть LocalLS',click:()=>{window.show();window.focus();}},{type:'separator'},{label:'Выйти',click:async()=>{if((transfers.running||hostServer.snapshot().devices.some(d=>d.online))&&!await confirm('Завершить работу LocalLS?','Передачи и подключения к серверу компьютера будут остановлены.'))return;quitting=true;await hostServer.stop();for(const j of transfers.items)transfers.cancel(j.id);server.disconnect();app.quit();}}]));tray.on('double-click',()=>{window.show();window.focus();});
});
app.on('window-all-closed',()=>{});app.on('before-quit',()=>{qrRevision++;quitting=true;server.disconnect();hostServer.stop().catch(()=>{});clientPower.release();serverPower.release();previewStore?.destroy().catch(()=>{});});}
