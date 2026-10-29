'use strict';
// ssh2 cannot complete an RPC submitted after EOF. In particular its file streams
// try to CLOSE a handle while processing failed requests at channel shutdown.
const states=new WeakMap();
function channelState(channel){
 let state=states.get(channel);if(state)return state;
 state={pending:new Set(),error:null,protected:false};states.set(channel,state);
 const ended=()=>{if(state.error)return;state.error=Object.assign(new Error('NOT_CONNECTED'),{code:'ECONNRESET'});for(const finish of [...state.pending])finish(state.error);};
 channel.on?.('end',ended);channel.on?.('close',ended);channel.on?.('error',ended);
 if(channel.readable===false||channel.destroyed===true||channel.outgoing&&channel.outgoing.state!=='open')ended();
 return state;
}
function request(channel,operation,args){
 return new Promise((resolve,reject)=>{
  const state=channelState(channel);if(state.error)return reject(state.error);
  let settled=false;const finish=(error,result)=>{if(settled)return;settled=true;state.pending.delete(finish);error?reject(error):resolve(result);};
  state.pending.add(finish);try{operation(...args,finish);}catch(error){finish(error);}
 });
}
function call(channel,method,...args){return request(channel,(...values)=>channel[method](...values),args);}
function protect(channel){
 const state=channelState(channel);if(state.protected)return channel;state.protected=true;
 if(typeof channel.close==='function'){
  const close=channel.close.bind(channel);
  channel.close=(handle,callback)=>{request(channel,close,[handle]).then(value=>callback?.(null,value),error=>callback?.(error));};
 }
 return channel;
}
module.exports={call,protect};
