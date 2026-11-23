const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const path=require('node:path');
const {runInNewContext}=require('node:vm');
const esbuild=require('./import-converter/node_modules/esbuild');
test('generic MIME stylesheets localize their font URLs before the converter closes',async()=>{
  const written=new Map();
  const scope={module:{exports:{}},exports:{},Blob,URL,fetch,TextEncoder,TextDecoder,Uint8Array,btoa,
    MIUingImport:{write:(name,data)=>{written.set(name,(written.get(name)||'')+Buffer.from(data,'base64').toString());return true;}}};
  const source=readFileSync(path.join(__dirname,'import-converter/resources.js'),'utf8');
  runInNewContext(esbuild.transformSync(source,{format:'cjs',target:'node18'}).code,scope);
  const {Resources}=scope.module.exports;
  const font=URL.createObjectURL(new Blob(['font-data'],{type:'application/octet-stream'}));
  const sheet=URL.createObjectURL(new Blob([`@font-face{font-family:test;src:url("${font}")}`],{type:'application/octet-stream'}));
  try {
    const resources=new Resources();const file=await resources.add(sheet,'text/css');await resources.finish();
    assert.ok(file.endsWith('.css'),'Stylesheet MIME must follow its explicit link role');
    assert.equal(resources.items.find(x=>x.file===file).type,'text/css');
    assert.ok(!written.get(file).includes('blob:'),'A closed converter cannot serve its Blob URLs');
    assert.ok(written.get(file).includes('r1.bin'));assert.equal(written.get('r1.bin'),'font-data');
  } finally {URL.revokeObjectURL(font);URL.revokeObjectURL(sheet);}
});
