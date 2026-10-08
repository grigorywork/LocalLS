'use strict';
const { contextBridge, ipcRenderer } = require('electron');
const actions = ['state','saveSettings','connect','disconnect','remoteList','localList','chooseLocal','chooseUpload','download','upload','mkdir','rename','remove','stats','cancel','importProfile','exportProfile','forgetKey','agent','vpnStatus','vpnToggle','openTailscale','tray','quit','systemSettings','localServerStatus','localServerAction','localServerCheck','localServerExport'];
const api = {};
for (const action of actions) api[action] = args => ipcRenderer.invoke('localls:'+action,args);
api.on = (event, callback) => { if (!['transfers','connection','notice'].includes(event)) throw new Error('Event denied'); const listener=(_,data)=>callback(data); ipcRenderer.on('localls:'+event,listener); return ()=>ipcRenderer.removeListener('localls:'+event,listener); };
contextBridge.exposeInMainWorld('localLS',Object.freeze(api));
