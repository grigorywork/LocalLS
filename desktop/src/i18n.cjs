'use strict';
const strings=require('./strings.json');let language='ru';const reverse=Object.fromEntries(Object.entries(strings).map(([k,v])=>[v,k]));function t(value){if(typeof value!=='string')return value;const map=language==='en'?strings:reverse;return Object.hasOwn(map,value)?map[value]:value;}function setLanguage(value){language=value==='en'?'en':'ru';}module.exports={t,setLanguage};
