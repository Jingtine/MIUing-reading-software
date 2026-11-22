/* Optional paragraph styling; never insert characters or rewrite publisher files. */
window.MIUingChineseTypography=class {
  constructor() {this.original=new Map();}
  restore() {
    for(const [element,style] of this.original) {
      if(style===null) element.removeAttribute('style');else element.setAttribute('style',style);
    }
    this.original.clear();
  }
  apply(flow,config) {
    if(!config.customLayout || !config.chineseLayout) return;
    const han=text=>{
      for(const char of text) {
        const cp=char.codePointAt(0);
        if((cp>=0x3400 && cp<=0x9fff) || (cp>=0xf900 && cp<=0xfaff) || (cp>=0x20000 && cp<=0x323af)) return true;
      }
      return false;
    };
    const paragraphs=[];
    for(const p of flow.querySelectorAll('p')) {
      if(p.closest('pre,code,kbd,samp,svg,math,table,figure,figcaption,li,blockquote') || !han(p.textContent)) continue;
      const align=getComputedStyle(p).textAlign;
      if(align==='center' || align==='right' || align==='end') continue;
      paragraphs.push(p);
    }
    const indent=Number.isFinite(config.firstLineIndent)?Math.max(0,Math.min(3,config.firstLineIndent)):2;
    const spacing=Number.isFinite(config.paragraphSpacing)?Math.max(0,Math.min(1.5,config.paragraphSpacing)):.5;
    for(const p of paragraphs) {
      this.original.set(p,p.getAttribute('style'));
      for(const [key,value] of [['text-indent',`${indent}em`],['margin-top','0'],['margin-bottom',`${spacing}em`],['line-break','strict'],['word-break','normal']])
        p.style.setProperty(key,value,'important');
    }
  }
};
