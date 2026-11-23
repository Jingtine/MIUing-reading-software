const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {runInNewContext}=require('node:vm');
const esbuild=require('./import-converter/node_modules/esbuild');
const {boundMobi}=require('./import-converter/bounds.cjs');
const source=boundMobi(readFileSync(require('node:path').join(__dirname,'../third_party/foliate-js/mobi.js'),'utf8'))+'\nexport {getVarLen};';
const code=esbuild.transformSync(source,{format:'cjs',target:'node18'}).code;
const scope={module:{exports:{}},exports:{},TextDecoder,TextEncoder,Uint8Array,DataView,console};runInNewContext(code,scope);
const {getVarLen}=scope.module.exports;
test('truncated MOBI variable-length indexes fail instead of returning zero progress',()=>{
  assert.throws(()=>getVarLen(new Uint8Array(),0),/截断|无效/);
  assert.throws(()=>getVarLen(new Uint8Array([1]),1),/截断|无效/);
  assert.throws(()=>getVarLen(new Uint8Array([1,2]),2),/截断|无效/);
});
test('valid MOBI index integers preserve their consumed byte count',()=>{
  const result=getVarLen(new Uint8Array([1,0x82]),0);
  assert.equal(result.value,130);assert.equal(result.length,2);
  const full=getVarLen(new Uint8Array([1,2,3,4]),0);
  assert.equal(full.length,4);
  assert.equal(getVarLen(new Uint8Array([1,2]),0).length,2);
});
