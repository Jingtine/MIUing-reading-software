/* App-owned note modal lives outside the text flow and inside its own style tree. */
window.MIUingFootnotes=class {
  constructor(config,cancelGesture) {
    this.config=config;this.cancelGesture=cancelGesture;this.host=null;this.focus=null;this.url='';
    document.addEventListener('click',event=>{
      const link=event.target.closest && event.target.closest('a[href]'),c=this.config();
      if(!link || !c || typeof MIUing.openFootnote!=='function') return;
      let url,source;
      try {source=new URL(c.documentUrl || document.baseURI);url=new URL(link.getAttribute('href'),source);} catch(_) {return;}
      if(url.origin!==source.origin || !url.hash) return;
      const tokens=(element,attribute,names)=>names.some(name=>(element.getAttribute(attribute) || '').split(/\s+/).includes(name));
      let target=null;
      if(url.pathname===source.pathname) {try {target=document.getElementById(decodeURIComponent(url.hash.slice(1)));} catch(_) {}}
      const reference=tokens(link,'epub:type',['noteref']) || tokens(link,'role',['doc-noteref']);
      const note=target && (tokens(target,'epub:type',['footnote','endnote']) || tokens(target,'role',['doc-footnote','doc-endnote']));
      if(!reference && !note) return;
      event.preventDefault();event.stopPropagation();this.open(url.href);MIUing.openFootnote(url.href);
    },true);
    document.addEventListener('keydown',event=>{
      if(this.host && event.key==='Escape') {event.preventDefault();event.stopImmediatePropagation();this.close();}
    },true);
  }
  open(url) {
    this.close();this.cancelGesture();this.url=url;this.focus=document.activeElement;
    const c=this.config(),font=getComputedStyle(document.body);
    this.host=document.createElement('div');this.host.id='miuing-footnote';
    this.host.style.cssText='position:fixed!important;top:0!important;right:0!important;bottom:0!important;left:0!important;display:block!important;z-index:2147483647!important;';
    const shadow=this.host.attachShadow({mode:'open'});
    shadow.innerHTML=`<style>
      :host{color:inherit}*{box-sizing:border-box}.backdrop{position:fixed;top:0;right:0;bottom:0;left:0;background:rgba(0,0,0,.24);display:flex;align-items:center;justify-content:center;padding:20px}
      .panel{width:100%;max-width:560px;max-height:80%;border-radius:18px;box-shadow:0 8px 28px #0003;display:flex;flex-direction:column;overflow:hidden}
      header{display:flex;align-items:center;justify-content:space-between;padding:8px 12px 8px 20px;border-bottom:1px solid #8883;font:600 16px system-ui,sans-serif}
      button{font:inherit;color:inherit;background:none;border:0;min-width:44px;min-height:44px;cursor:pointer}button:focus-visible{outline:2px solid currentColor;outline-offset:-3px}
      .content{overflow:auto;overscroll-behavior:contain;padding:18px 22px;line-height:1.65;white-space:normal;overflow-wrap:break-word;-webkit-user-select:text;user-select:text}
      .content p{margin:0 0 .7em}.content p:last-child{margin-bottom:0}.content pre{white-space:pre-wrap}.content h1,.content h2,.content h3{font-size:1.2em}
      footer{border-top:1px solid #8883;text-align:right;padding:0 12px;font:14px system-ui,sans-serif}[hidden]{display:none!important}
    </style><div class='backdrop'><section class='panel' role='dialog' aria-modal='true' aria-label='脚注'><header><span>脚注</span><button aria-label='关闭脚注'>✕</button></header><div class='content' tabindex='0'>正在加载…</div><footer hidden><button class='original'>前往原文</button></footer></section></div>`;
    const panel=shadow.querySelector('.panel'),content=shadow.querySelector('.content');
    panel.style.background=c.paper;panel.style.color=c.ink;content.style.fontFamily=font.fontFamily;content.style.fontSize=font.fontSize;content.style.fontWeight=font.fontWeight;
    shadow.querySelector('header button').onclick=()=>this.close();
    shadow.querySelector('.backdrop').onclick=event=>{if(event.target===event.currentTarget) this.close();};
    shadow.querySelector('.original').onclick=()=>{const href=this.url;this.close();if(typeof MIUing.footnoteNavigate==='function') MIUing.footnoteNavigate(href);};
    for(const type of ['click','touchstart','touchmove','touchend','touchcancel','pointerdown']) this.host.addEventListener(type,event=>event.stopPropagation());
    document.body.appendChild(this.host);shadow.querySelector('header button').focus({preventScroll:true});
    if(typeof MIUing.footnoteState==='function') MIUing.footnoteState(true);
  }
  show(html,canNavigate,error=false) {
    if(!this.host) return;
    const shadow=this.host.shadowRoot,content=shadow.querySelector('.content');
    if(error) content.textContent=html;else content.innerHTML=html;
    shadow.querySelector('footer').hidden=!canNavigate;
  }
  close() {
    if(!this.host) return;
    this.host.remove();this.host=null;
    if(this.focus && this.focus.isConnected && this.focus.focus) this.focus.focus({preventScroll:true});this.focus=null;
    if(typeof MIUing.footnoteState==='function') MIUing.footnoteState(false);
  }
};
