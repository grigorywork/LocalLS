'use strict';
const { contextBridge, ipcRenderer } = require('electron');
const actions = ['cancelAll','clearQueue','previewOpen','previewClose','trafficSnapshot','saveAppearance','setRole','retry','serverState','serverStart','serverStop','serverConfigure','chooseSharedFolder','openSharedFolder','serverQr','saveServerQr','approveDevice','rejectDevice','revokeDevice','state','importQrImage','generateIdentity','importIdentity','exportPublicKey','removeIdentity','installIdentity','saveSettings','connect','disconnect','remoteList','localList','chooseLocal','chooseUpload','download','upload','mkdir','rename','remove','stats','cancel','importProfile','exportProfile','forgetKey','tray'];
const api = {};
for (const action of actions) api[action] = args => ipcRenderer.invoke('localls:'+action,args);
api.on = (event, callback) => { if (!['transfers','connection','notice','qr','hosting','traffic'].includes(event)) throw new Error('Event denied'); const listener=(_,data)=>callback(data); ipcRenderer.on('localls:'+event,listener); return ()=>ipcRenderer.removeListener('localls:'+event,listener); };
contextBridge.exposeInMainWorld('localLS',Object.freeze(api));
