import {Resources,sendFile} from './resources.js';
const EPUB='http://www.idpf.org/2007/ops';
const cleanText=value=>String(value??'').replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/g,'').trim();

export async function exportBook(book) {
  if(!book.sections?.length || book.sections.length>2000) throw Error('图书章节为空或超过2000节限制');
  const docs=[],resources=new Resources();let textSize=0;
  for(const section of book.sections) {
    const url=await section.load();const response=await fetch(url);const text=await response.text();
    // Reparse as HTML so malformed legacy MOBI markup remains inert and readable.
    const doc=new DOMParser().parseFromString(text,'text/html');
    doc.documentElement.setAttribute('xmlns:epub',EPUB);
    for(const el of doc.querySelectorAll('script,iframe,object,embed,base,form,meta[http-equiv],audio,video')) el.remove();
    for(const el of doc.querySelectorAll('*')) for(const attr of Array.from(el.attributes))
      if(/^on/i.test(attr.name) || ['srcdoc','srcset','action','formaction'].includes(attr.name)) el.removeAttribute(attr.name);
    textSize+=new TextEncoder().encode(doc.body.textContent||'').byteLength;
    if(textSize>32*1024*1024) throw Error('转换正文超过32MiB限制');
    if(section.linear==='no') {
      doc.body.setAttribute('role','doc-endnotes');
      for(const el of doc.querySelectorAll('section[id]')) el.setAttribute('role','doc-footnote');
    }
    docs.push(doc);
  }
  let anchorId=0;
  async function href(value,current) {
    if(value?.startsWith('#') && docs[current]?.getElementById(value.slice(1))) return 'c'+current+'.xhtml'+value;
    try {
      const target=await book.resolveHref(value);if(!target || !docs[target.index]) return '';
      const node=typeof target.anchor==='function'?target.anchor(docs[target.index]):null;
      if(node && !node.id) node.id='miuing-anchor-'+(++anchorId);
      return 'c'+target.index+'.xhtml'+(node?'#'+encodeURIComponent(node.id):'');
    } catch {return '';}
  }
  const toc=[];
  async function navigation(items,depth=0) {
    if(!items?.length) return;
    if(depth>32) throw Error('目录层级超过32层限制');
    for(const item of items||[]) {
      if(toc.length>=2000) throw Error('目录超过2000项限制');
      const link=await href(item.href,-1);
      if(link) toc.push({label:cleanText(item.label)||'章节',href:link,depth});
      if(item.subitems) await navigation(item.subitems,depth+1);
    }
  }
  await navigation(book.toc);
  // Resolve every link before serializing any chapter: later links can add IDs to earlier documents.
  for(let i=0;i<docs.length;i++) for(const a of docs[i].querySelectorAll('a[href]')) {
    const link=await href(a.getAttribute('href'),i);
    if(link) a.setAttribute('href',link);else a.removeAttribute('href');
    if((a.getAttributeNS(EPUB,'type')||a.getAttribute('epub:type')||'').split(/\s+/).includes('noteref')) a.setAttribute('role','doc-noteref');
  }
  const sections=[];
  for(let i=0;i<docs.length;i++) {
    const doc=docs[i];const first=toc.find(x=>x.href.split('#')[0]==='c'+i+'.xhtml');
    const title=cleanText(first?.label || book.sections[i].title || doc.querySelector('h1,h2')?.textContent || '第'+(i+1)+'节');
    let el=doc.head.querySelector('title');if(!el) {el=doc.createElement('title');doc.head.appendChild(el);}el.textContent=title;
    for(const style of doc.querySelectorAll('style')) style.textContent=await resources.css(style.textContent);
    for(const el of doc.querySelectorAll('[style]')) el.setAttribute('style',await resources.css(el.getAttribute('style')));
    for(const el of doc.querySelectorAll('link[href]')) {
      const local=el.rel==='stylesheet'?await resources.add(el.getAttribute('href'),'text/css'):'';
      if(local) el.setAttribute('href',local);else el.remove();
    }
    for(const el of doc.querySelectorAll('[src],image')) {
      const attr=el.hasAttribute('src')?'src':el.hasAttribute('href')?'href':'xlink:href';
      const local=await resources.add(el.getAttribute(attr));
      if(local) el.setAttribute(attr,local);else el.removeAttribute(attr);
    }
    const file='c'+i+'.xhtml';
    await sendFile(file,new Blob([cleanText(new XMLSerializer().serializeToString(doc.documentElement))],{type:'application/xhtml+xml'}));
    sections.push({file,title,linear:book.sections[i].linear!=='no'});
  }
  const coverBlob=await book.getCover?.();const cover=coverBlob?await resources.add(URL.createObjectURL(coverBlob)):'';
  await resources.finish();
  const author=book.metadata?.author;const names=Array.isArray(author)?author:[author];
  const metadata={title:cleanText(book.metadata?.title),author:names.filter(Boolean).map(x=>cleanText(typeof x==='string'?x:x.name)).filter(Boolean).join('、'),sections,toc,resources:resources.items,cover};
  MIUingImport.complete(JSON.stringify(metadata));
}
