// Exact build-time patches leave the pinned upstream files untouched.
// Fail the build if an upstream update changes an insertion point.
exports.boundMobi=function(source) {
  function once(from,to) {
    if(source.split(from).length!==2) throw Error('MOBI bounds insertion point changed: '+from);
    source=source.replace(from,to);
  }
  const start=source.indexOf('const getStruct = '),end=source.indexOf('\n\nconst getDecoder',start);
  if(start<0 || end<0) throw Error('MOBI structure guard insertion point changed');
  const structure=source.slice(start,end);
  once(structure,structure.replace('const getStruct =','const getStructRaw =')+`
const getStruct = (def, buffer) => {
    const value=getStructRaw(def,buffer);
    for(const key of ['numEntries','numRecords','numHuffcdic','count','numCncx','numLigt'])
        if(value[key]>262144) throw Error('MOBI结构条目过多');
    if(value.length>16777216) throw Error('MOBI结构长度越界');
    return value;
}`);
  // Keep the pinned decoder's tolerance for partial/four-byte integers. Only
  // out-of-record reads can return zero progress and trap its caller in a loop.
  once('const getVarLen = (byteArray, i = 0) => {',`const getVarLen = (byteArray, i = 0) => {
    if(!Number.isInteger(i) || i<0 || i>=byteArray.length) throw Error('MOBI索引截断');`);
  once('const numTags = (tagx.length - 12) / 4',`const numTags = (tagx.length - 12) / 4;
    if(!Number.isInteger(numTags) || numTags<0 || numTags>4096 || tagx.length>tagxBuffer.byteLength) throw Error('MOBI标记表截断');`);
  once('for (let i = 0; i < valueCount * numValues; i++) {',`if(valueCount*numValues>array.length-pos) throw Error('MOBI索引值截断');
                    for (let i = 0; i < valueCount * numValues; i++) {`);
  once('let count = 0\n                    while (count < valueBytes)',`if(valueBytes>array.length-pos) throw Error('MOBI索引值截断');
                    let count = 0
                    while (count < valueBytes)`);
  once('const result = new a.constructor(a.length + b.length)',"if(a.length+b.length>33554432) throw Error('正文超过32MiB限制');\n    const result = new a.constructor(a.length + b.length)");
  once('const result = new a.constructor(a.length + b.length + c.length)',"if(a.length+b.length+c.length>33554432) throw Error('正文超过32MiB限制');\n    const result = new a.constructor(a.length + b.length + c.length)");
  once('const byte = array[i]\n        if (byte === 0)',"if(output.length>65526) throw Error('MOBI解压记录过大');\n        const byte = array[i]\n        if (byte === 0)");
  once('const n = Math.min(1 << cdic.codeLength, cdic.numEntries - dictionary.length)',"if(cdic.codeLength>18 || cdic.numEntries>262144) throw Error('MOBI字典过大');\n        const n = Math.min(1 << cdic.codeLength, cdic.numEntries - dictionary.length)");
  once('const decompress = byteArray => {',"const decompress = (byteArray, depth=0) => {\n        if(depth>32) throw Error('MOBI压缩字典递归过深');");
  once('if ((i += codeLength) > bitLength) break',"if(codeLength<1 || codeLength>32) throw Error('无效MOBI压缩字典');\n            if ((i += codeLength) > bitLength) break");
  once('result = decompress(result)','result = decompress(result, depth+1)');
  once('output = concatTypedArray(output, result)',"if(output.length+result.length>65536) throw Error('MOBI解压记录过大');\n            output = concatTypedArray(output, result)");
  once('const recordBuffers = []', 'const recordBuffers = []; let miuingTextBytes=0');
  once('const palmdoc = getStruct(PALMDOC_HEADER, buf)',"const palmdoc = getStruct(PALMDOC_HEADER, buf); if(palmdoc.encryption) throw Error('该文件受DRM保护，无法导入');");
  once('recordBuffers.push(buf)',"miuingTextBytes+=buf.byteLength; if(miuingTextBytes>33554432) throw Error('正文超过32MiB限制');\n            recordBuffers.push(buf)");
  return source;
};
