const MAX_RESOURCE=16*1024*1024;
export async function sendFile(name,blob) {
  if(blob.size>MAX_RESOURCE) throw Error('单个章节或资源超过16MiB限制');
  if(!blob.size) {if(!MIUingImport.write(name,'',true)) throw Error('转换已取消');return;}
  for(let offset=0;offset<blob.size;offset+=49152) {
    const bytes=new Uint8Array(await blob.slice(offset,offset+49152).arrayBuffer());
    let str='';for(let i=0;i<bytes.length;i+=8192) str+=String.fromCharCode(...bytes.subarray(i,i+8192));
    if(!MIUingImport.write(name,btoa(str),offset+bytes.length===blob.size)) throw Error('转换已取消或超过大小限制');
  }
}
async function mime(blob,hint) {
  // MOBI resource loaders can label stylesheet Blobs as application/octet-stream.
  if(hint==='text/css') return hint;
  const bytes=new Uint8Array(await blob.slice(0,64).arrayBuffer());
  const head=String.fromCharCode(...bytes);
  if(bytes[0]===137 && head.slice(1,4)==='PNG') return 'image/png';
  if(bytes[0]===255 && bytes[1]===216) return 'image/jpeg';
  if(head.startsWith('GIF8')) return 'image/gif';
  if(head.startsWith('RIFF') && head.slice(8,12)==='WEBP') return 'image/webp';
  if(/<svg|<\?xml/.test(head) || blob.type==='image/svg+xml') return 'image/svg+xml';
  if(head.startsWith('wOF2')) return 'font/woff2';if(head.startsWith('wOFF')) return 'font/woff';
  if(head.startsWith('OTTO')) return 'font/otf';
  return blob.type || hint || 'application/octet-stream';
}
export class Resources {
  constructor() {this.items=[];this.urls=new Map();this.jobs=[];this.total=0;}
  async css(text) {
    const expression=/(@import\s+)?url\(\s*(?:"([^"]*)"|'([^']*)'|([^)]*))\s*\)/gi;
    const matches=Array.from(text.matchAll(expression));let result='',offset=0;
    for(const match of matches) {
      const url=(match[2]??match[3]??match[4]??'').trim();
      const local=url.startsWith('#')?url:await this.add(url,match[1]?'text/css':undefined);
      result+=text.slice(offset,match.index)+(match[1]||'')+(local?'url("'+local.replace(/"/g,'%22')+'")':'url("")');offset=match.index+match[0].length;
    }
    return (result+text.slice(offset)).replace(/@import\s+["'][^"']*["']\s*;?/gi,'');
  }
  async add(url,hint) {
    if(!/^(blob:|data:)/i.test(url||'')) return '';
    if(this.urls.has(url)) return this.urls.get(url);
    if(this.items.length>=7996) throw Error('图书资源数量过多');
    const response=await fetch(url);if(!response.ok) throw Error('无法读取嵌入资源');
    let blob=await response.blob();if(blob.size>MAX_RESOURCE) throw Error('嵌入资源超过16MiB限制');
    const type=await mime(blob,hint);const file='r'+this.items.length+(type==='text/css'?'.css':'.bin');
    this.urls.set(url,file);this.items.push({file,type});
    const job=(async()=>{
      if(type==='text/css') blob=new Blob([await this.css(await blob.text())],{type});
      this.total+=blob.size;if(this.total>256*1024*1024) throw Error('转换内容超过256MiB限制');
      await sendFile(file,blob);
    })();this.jobs.push(job);await job;return file;
  }
  async finish() {await Promise.all(this.jobs);}
}
