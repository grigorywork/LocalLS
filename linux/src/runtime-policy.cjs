'use strict';
function configureRuntime(app){
 const disabled=new Set(app.commandLine.getSwitchValue('disable-features').split(',').filter(Boolean));
 for(const feature of ['SkiaGraphite','SkiaGraphitePrecompilation','WebGPUService'])disabled.add(feature);
 app.commandLine.appendSwitch('disable-features',[...disabled].join(','));
 app.commandLine.appendSwitch('disable-skia-graphite');
}
module.exports={configureRuntime};
