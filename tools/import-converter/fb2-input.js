// Localize binary references before Foliate serializes sections. Repeated images
// then use one short Blob URL instead of duplicating megabytes of base64 markup.
export function prepareFB2(doc,urls) {
  const xlink='http://www.w3.org/1999/xlink';
  const binaries=new Map(Array.from(doc.getElementsByTagName('binary'),el=>[el.id,el]));
  const cached=new Map();
  for(const image of doc.getElementsByTagName('image')) {
    const href=image.getAttributeNS(xlink,'href');if(!href?.startsWith('#')) continue;
    const id=href.slice(1),binary=binaries.get(id);if(!binary) continue;
    if(!cached.has(id)) {
      const encoded=binary.textContent.replace(/\s/g,'');
      if(encoded.length>Math.ceil(16*1024*1024/3)*4) throw Error('FB2图片超过16MiB限制');
      const raw=atob(encoded);if(raw.length>16*1024*1024) throw Error('FB2图片超过16MiB限制');
      const bytes=new Uint8Array(raw.length);for(let i=0;i<raw.length;i++) bytes[i]=raw.charCodeAt(i);
      const url=URL.createObjectURL(new Blob([bytes],{type:binary.getAttribute('content-type')||'application/octet-stream'}));
      urls.push(url);cached.set(id,url);
    }
    image.setAttributeNS(xlink,'l:href',cached.get(id));
  }
  for(const binary of binaries.values()) binary.remove();
  return new Blob([new XMLSerializer().serializeToString(doc.documentElement)],{type:'application/xml'});
}
