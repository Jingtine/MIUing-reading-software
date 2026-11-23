// The import adapter also runs on Android 8's original Chromium 69.
if (!Object.fromEntries) Object.fromEntries = entries => {
  const result={}; for(const [key,value] of entries) Object.defineProperty(result,key,{value,writable:true,enumerable:true,configurable:true}); return result;
};
if (!String.prototype.matchAll) String.prototype.matchAll = function*(regex) {
  const re=new RegExp(regex.source,regex.flags.includes('g')?regex.flags:regex.flags+'g');
  re.lastIndex=regex.lastIndex;let m;
  while((m=re.exec(String(this)))) {yield m;if(m[0]==='') re.lastIndex++;}
};
if (!String.prototype.replaceAll) String.prototype.replaceAll = function(search,replacement) {
  if(search instanceof RegExp) {
    if(!search.global) throw new TypeError('replaceAll requires a global expression');
    return this.replace(search,replacement);
  }
  return this.replace(new RegExp(String(search).replace(/[.*+?^${}()|[\]\\]/g,'\\$&'),'g'),replacement);
};
if (!Blob.prototype.arrayBuffer) Blob.prototype.arrayBuffer=function() {
  return new Promise((resolve,reject)=>{const r=new FileReader();r.onload=()=>resolve(r.result);r.onerror=()=>reject(r.error);r.readAsArrayBuffer(this);});
};
if (!Blob.prototype.text) Blob.prototype.text=async function() {return new TextDecoder().decode(await this.arrayBuffer());};
