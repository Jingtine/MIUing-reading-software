import './compat.js';
import {MOBI,isMOBI} from 'miuing-mobi';
import {makeFB2} from '../../third_party/foliate-js/fb2.js';
import {unzlibSync} from '../../third_party/foliate-js/fflate.js';
import {exportBook} from './publication.js';
import {prepareFB2} from './fb2-input.js';

const MAX_SOURCE=64*1024*1024;
async function preflight(file) {
  if(!await isMOBI(file)) throw Error('文件不是MOBI/KF8电子书；暂不支持KFX或Print Replica');
  const header=new DataView(await file.slice(0,78).arrayBuffer());const count=header.getUint16(76);
  if(count<2) throw Error('MOBI缺少正文记录');
  const bytes=await file.slice(78,78+count*8).arrayBuffer();
  if(bytes.byteLength!==count*8) throw Error('MOBI记录表损坏');
  const table=new DataView(bytes);let previous=78+count*8;
  for(let i=0;i<count;i++) {const offset=table.getUint32(i*8);if(offset<previous || offset>file.size) throw Error('MOBI记录越界');previous=offset;}
  const start=table.getUint32(0),end=table.getUint32(8);
  const first=await file.slice(start,end).arrayBuffer();if(first.byteLength<264) throw Error('MOBI头部损坏');
  const record=new DataView(first);
  if(record.getUint16(12)!==0) throw Error('该文件受DRM保护，无法导入；请使用未加密版本');
  if(record.getUint32(4)>32*1024*1024) throw Error('正文超过32MiB限制');
}
async function run() {
  const response=await fetch('/source/book');if(!response.ok) throw Error('无法读取导入文件');
  const file=await response.blob();if(file.size>MAX_SOURCE) throw Error('此格式目前支持64MiB以内的文件');
  let book;const temporaryUrls=[];
  try {
    if(MIUingImport.format()==='fb2') {
      const bytes=await file.arrayBuffer();const head=new Uint8Array(bytes,0,Math.min(256,bytes.byteLength));
      const declaration=String.fromCharCode(...head).match(/<\?xml[^>]*encoding\s*=\s*["']([^"']+)/i);
      const encoding=head[0]===255&&head[1]===254?'utf-16le':head[0]===254&&head[1]===255?'utf-16be':declaration?.[1]||'utf-8';
      const xml=new TextDecoder(encoding).decode(bytes);
      if(/<!DOCTYPE|<!ENTITY/i.test(xml)) throw Error('不支持包含外部实体声明的FB2');
      const parsed=new DOMParser().parseFromString(xml,'application/xml');
      if(parsed.querySelector('parsererror') || parsed.documentElement.localName!=='FictionBook') throw Error('FB2文件结构损坏');
      book=await makeFB2(prepareFB2(parsed,temporaryUrls));
    } else {
      await preflight(file);
      const mobi=new MOBI({unzlib:bytes=>{
        const out=unzlibSync(bytes,{out:new Uint8Array(16*1024*1024+1)});
        if(out.length>16*1024*1024) throw Error('嵌入字体超过16MiB限制');return out;
      }});
      const loadText=mobi.loadText.bind(mobi);
      mobi.loadText=async index=>{
        const text=await loadText(index);
        if(index===0 && String.fromCharCode(...text.subarray(0,4))==='%MOP') throw Error('暂不支持Kindle Print Replica，请使用PDF或可重排电子书');
        return text;
      };
      book=await mobi.open(file);
    }
    await exportBook(book);
  } finally {book?.destroy?.();for(const url of temporaryUrls) URL.revokeObjectURL(url);}
}
run().catch(error=>MIUingImport.fail(error?.message || '图书结构损坏或格式变体暂不受支持'));
