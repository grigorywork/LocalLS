'use strict';
// LocalLS uses Canvas2D/CSS and native Windows Acrylic, not WebGPU/Dawn.
// Keep the conventional GPU compositor and its software fallback available.
const unusedDxc=['dxcompiler.dll','dxil.dll'];
function configureRuntime(app){
 const disabled=new Set(app.commandLine.getSwitchValue('disable-features').split(',').filter(Boolean));
 for(const name of ['SkiaGraphite','SkiaGraphiteWinIntel','SkiaGraphitePrecompilation','WebGPUService'])disabled.add(name);
 app.commandLine.appendSwitch('disable-features',[...disabled].join(','));
 app.commandLine.appendSwitch('disable-skia-graphite');
 app.commandLine.appendSwitch('disable-skia-graphite-precompilation');
}
module.exports={unusedDxc,configureRuntime};
