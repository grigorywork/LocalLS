'use strict';
// GNOME/KDE do not provide Electron's Windows Acrylic API. Matte panels use the shared CSS palette.
function supportsAcrylic(){return false;}
function windowOptions(settings){return {backgroundColor:settings.light?'#f3f4f6':'#121019'};}
function applyWindowMaterial(window,settings){if(window&&!window.isDestroyed())window.setBackgroundColor(windowOptions(settings).backgroundColor);return false;}
module.exports={supportsAcrylic,windowOptions,applyWindowMaterial};
