/* Application-owned renderer. Book-authored scripts remain blocked by CSP. */
(() => {
  if (window.miuingConfigure) return;
  let config, shell, flow, restoring = false, timer, selectionTimer;
  let retainedAnchor = -1, publishedAnchor = -1, layoutRevision = 0;
  let hasPosition = false, pendingInitial = null;
  let ownedScroll = null;
  let resourcesSettled=false;
  const publisherTextStyles = new Map();
  let textIndex;
  const chineseTypography=new window.MIUingChineseTypography();
  const pagination=new window.MIUingPagination();
  const footnotes=new window.MIUingFootnotes(()=>config,()=>gestures.cancel());
  document.addEventListener('click', event => {
    if (event.defaultPrevented || !flow || typeof MIUing.openFragment !== 'function') return;
    const link = event.target.closest && event.target.closest('a[href]');
    if (!link || !flow.contains(link)) return;
    const href = link.getAttribute('href').trim();
    if (!href.startsWith('#')) return;

    // Android's base URL supplies the resource directory, so a bare fragment
    // must be resolved against this chapter rather than the WebView's history.
    event.preventDefault();
    let fragment;
    try { fragment = decodeURIComponent(href.slice(1)); } catch (_) { return; }
    const target = document.getElementById(fragment);
    if (target && flow.contains(target)) MIUing.openFragment(fragment);
  });
  window.miuingDismissFootnote=()=>footnotes.close();
  window.miuingShowFootnote=(html,original)=>footnotes.show(html,original);
  window.miuingFootnoteError=(message,original)=>footnotes.show(message,original,true);
  window.miuingPageMetrics=()=>shell?pagination.pages(shell,config):null;
  window.miuingCountPages=token=>{
    const requested=config;
    Promise.all([document.fonts?document.fonts.ready:Promise.resolve(),waitForImages()]).then(()=>requestAnimationFrame(()=>requestAnimationFrame(()=>{
      if(config!==requested || restoring) return;
      pagination.measure(shell,flow,config);
      const metrics=window.miuingPageMetrics();
      if(metrics && typeof MIUing.counted==='function') MIUing.counted(token,metrics.total);
    })));
  };
  function waitForImages() {
    return Promise.all(Array.from(flow.querySelectorAll('img')).map(img=>img.complete?Promise.resolve():new Promise(resolve=>{
      img.addEventListener('load',resolve,{once:true});img.addEventListener('error',resolve,{once:true});
    })));
  }
  window.miuingAnchor = () => {
    if (!shell) return -1;
    const box = shell.getBoundingClientRect(), step = Math.max(12, config.fontSize);
    for (let y = config.margin + 4; y < config.height - config.margin; y += step) {
      const caret = document.caretRangeFromPoint(box.left + config.margin + 3, box.top + y);
      if (caret && flow.contains(caret.startContainer) && caret.startContainer.nodeType === Node.TEXT_NODE)
        return textIndex.offsetOf(caret.startContainer, caret.startOffset);
    }
    return -1;
  };
  function extent() {
    return Math.max(0, config.scrolling ? shell.scrollHeight - shell.clientHeight : shell.scrollWidth - shell.clientWidth);
  }
  window.miuingProgress = () => extent() ? (config.scrolling ? shell.scrollTop : shell.scrollLeft) / extent() : 0;
  window.miuingLocation=()=>({position:window.miuingProgress(),anchor:retainedAnchor>=0?retainedAnchor:window.miuingAnchor()});
  function beginNavigation() {gestures.cancel();retainedAnchor=-1;layoutRevision++;restoring=false;hasPosition=true;pendingInitial=null;clearTimeout(timer);}
  function scrollReader(left,top) {
    shell.scrollTo(left,top);
    // Scroll events can arrive after restoration's last animation frame.
    // Match actual clamped coordinates so our own event keeps the text anchor.
    ownedScroll={left:shell.scrollLeft,top:shell.scrollTop};
  }
  function jumpFraction(fraction) {
    const at=Math.max(0,Math.min(1,fraction))*extent();
    const page=fraction>=1?extent():Math.min(extent(),Math.round(at/config.width)*config.width);
    scrollReader(config.scrolling ? 0 : page, config.scrolling ? at : 0);
  }
  window.miuingJump = fraction => {beginNavigation();jumpFraction(fraction);publish();};
  function locateRect(rect) {
    const box = shell.getBoundingClientRect();
    if (config.scrolling) scrollReader(shell.scrollLeft,shell.scrollTop + rect.top - box.top - config.margin);
    else scrollReader(Math.max(0, Math.floor((rect.left - box.left + shell.scrollLeft - config.margin + 1) / config.width) * config.width),shell.scrollTop);
  }
  function jumpAnchor(offset) {
    const range=textIndex.rangeAt(offset);
    if(!range) return;
    // At a column boundary a collapsed caret can belong to the preceding
    // line. Measure the actual character to select its visible page instead.
    const node=range.startContainer, start=range.startOffset;
    if(node.nodeType===Node.TEXT_NODE && start<node.length) range.setEnd(node,start+1);
    locateRect(range.getBoundingClientRect());
  }
  window.miuingJumpAnchor = offset => {beginNavigation();retainedAnchor=offset;jumpAnchor(offset);publish();};
  function publishPages() {
    if(config.scrolling) return;
    const metrics=window.miuingPageMetrics();
    if(!metrics) return;
    if(typeof MIUing.pages==='function') MIUing.pages(metrics.spread,metrics.spreads);
    if(resourcesSettled && typeof MIUing.pagination==='function') MIUing.pagination(config.paginationKey || '',metrics.first,metrics.last,metrics.total);
  }
  function publish() {
    if (restoring) return;
    publishedAnchor=retainedAnchor>=0?retainedAnchor:window.miuingAnchor();
    MIUing.progress(window.miuingProgress(), publishedAnchor);
    publishPages();
  }
  function textStyleOverrides(family, next) {
    // Restore exact publisher attributes before applying the new reader choice.
    for(const [element,style] of publisherTextStyles) {
      if(style===null) element.removeAttribute('style');else element.setAttribute('style',style);
    }
    publisherTextStyles.clear();
    if(!family && !next.bold && next.customLayout===false) return;
    const parents=new Set();
    for(const entry of textIndex.entries()) {
      if(!entry.node.data.trim() || /^[\s\uE000-\uF8FF\u2000-\u2BFF]+$/.test(entry.node.data)) continue;
      const first=entry.node.parentElement;
      if(first.closest('svg,math,pre,code,kbd,samp') || /symbol|icon|dingbat|wingding|fontawesome/i.test(getComputedStyle(first).fontFamily)) continue;
      for(let parent=first;parent && parent!==flow;parent=parent.parentElement) parents.add(parent);
    }
    // Gather first, then mutate: avoid interleaving style reads with writes.
    for(const parent of parents) {
      publisherTextStyles.set(parent,parent.getAttribute('style'));
      if(family) parent.style.setProperty('font-family',family,'important');
      if(next.bold) parent.style.setProperty('font-weight','700','important');
      if(next.customLayout!==false) {
        parent.style.setProperty('line-height',String(next.lineHeight),'important');
        parent.style.setProperty('letter-spacing',`${next.letterSpacing || 0}em`,'important');
        parent.style.setProperty('word-spacing',`${next.wordSpacing || 0}em`,'important');
        if(next.justify && parent.matches('p,li,blockquote,td,dd')) parent.style.setProperty('text-align','justify','important');
      }
    }
  }
  window.miuingConfigure = next => {
    const textChanged = textIndex && textIndex.flushChanges();
    if (config && !textChanged && JSON.stringify(config) === JSON.stringify(next)) return;
    footnotes.close();
    gestures.cancel();
    const previous = config && hasPosition ? { anchor: retainedAnchor>=0?retainedAnchor:window.miuingAnchor(), position: window.miuingProgress() } : null;
    if(!config) pendingInitial={anchor:next.anchor>=0?next.anchor:-1,fragment:next.fragment || '',position:next.position || 0,search:next.search || ''};
    const destination=previous || pendingInitial || {anchor:-1,position:0,fragment:''};
    retainedAnchor=destination.anchor>=0?destination.anchor:-1;
    const revision=++layoutRevision;clearTimeout(timer);
    config = next; window.miuingConfig = next;
    window.miuingWidth = next.width; window.miuingHeight = next.height;
    restoring = true;
    resourcesSettled=false;
    pagination.reset();
    if (!flow) {
      flow = document.createElement('main'); flow.id = 'miuing-content';
      while (document.body.firstChild) flow.appendChild(document.body.firstChild);
      textIndex = new window.MIUingTextIndex(flow);
      shell = document.createElement('div'); shell.id = 'miuing-scroll'; shell.appendChild(flow); document.body.appendChild(shell);
      shell.addEventListener('scroll', () => {
        const own=ownedScroll && Math.abs(shell.scrollLeft-ownedScroll.left)<1 && Math.abs(shell.scrollTop-ownedScroll.top)<1;
        ownedScroll=null;
        if(restoring || own) return;
        retainedAnchor=-1;clearTimeout(timer);timer=setTimeout(publish,180);
      });
    }
    let style = document.getElementById('miuing-style');
    if (!style) { style = document.createElement('style'); style.id = 'miuing-style'; document.head.appendChild(style); }
    const w = next.width, h = next.height, m = next.margin, available = w - 2 * m;
    const column = w >= 900 ? (w - 4 * m) / 2 : available;
    const families={serif:'serif',sans:'sans-serif',mono:'monospace'};
    const face=next.fontFace;
    let fontStyle=document.getElementById('miuing-font-style');
    if(!fontStyle) {fontStyle=document.createElement('style');fontStyle.id='miuing-font-style';document.head.appendChild(fontStyle);}
    const faceCss=face?(face.sources || [face]).map(source=>`@font-face{font-family:"${face.family}";src:url("${source.url}");font-style:normal;font-weight:400;${source.unicodeRange?`unicode-range:${source.unicodeRange};`:''}}`).join('\n'):'';
    if(fontStyle.textContent!==faceCss) fontStyle.textContent=faceCss;
    const family=next.fontFamily==='book'?null:face?`"${face.family}",serif`:(families[next.fontFamily] || (next.serif?'serif':'sans-serif'));
    const fontCss=family?`font-family:${family}!important;`:'';
    const layoutCss=next.customLayout===false?'':`line-height:${next.lineHeight}!important;letter-spacing:${next.letterSpacing}em!important;word-spacing:${next.wordSpacing || 0}em!important;`;
    style.textContent = `
      html,body{margin:0!important;padding:0!important;width:${w}px!important;height:${h}px!important;overflow:hidden!important;background:${next.paper}!important;color:${next.ink}!important}
      body{font-size:${next.fontSize}px!important;${layoutCss}${fontCss}${next.bold?'font-weight:700!important;':''}overflow-wrap:break-word}
      #miuing-scroll{position:relative;width:${w}px;height:${h}px;overflow-x:hidden;overflow-y:${next.scrolling?'auto':'hidden'};scrollbar-width:none}
      #miuing-content{box-sizing:content-box;width:${available}px;margin:${m}px;${next.scrolling?`max-width:760px;margin:${m}px auto;padding:0 ${m}px;`:`height:${h-2*m}px;column-width:${column}px;column-gap:${2*m}px;column-fill:auto;`}}
      p{font-size:inherit!important;${next.customLayout===false?'':'line-height:inherit!important;letter-spacing:inherit!important;word-spacing:inherit!important;'}color:inherit!important;${next.justify?'text-align:justify!important;':''}}img,svg{max-width:100%!important;max-height:${h-2*m}px!important;object-fit:contain}mark[data-miuing]{background:#e8ce6c;color:inherit}mark[data-miuing-search]{background:#ffb95b;color:#222}
    `;
    const searchHit=textIndex.highlights(next.notes || [], next.search || '',next.searchIndex || 0);
    chineseTypography.restore();textStyleOverrides(family,next);chineseTypography.apply(flow,next);
    for(const img of flow.querySelectorAll('img')) if(img.loading==='lazy') img.loading='eager';
    let imagesPendingOnLayout=false;
    let resourcesPendingOnLayout=false;
    // Lazy intrinsic images can finish after onPageFinished. Count only stable
    // resources globally, and restore the latest passage rather than the opening one.
    Promise.all([document.fonts?document.fonts.ready:Promise.resolve(),waitForImages()]).then(()=>{
      const refresh=()=>{
        if(config!==next) return;
        if(restoring) {requestAnimationFrame(refresh);return;}
        if(document.fonts && document.fonts.status==='loading') {document.fonts.ready.then(()=>requestAnimationFrame(refresh));return;}
        if(imagesPendingOnLayout) {gestures.cancel();layoutRevision++;}
        const latest=retainedAnchor>=0?retainedAnchor:publishedAnchor;
        pagination.measure(shell,flow,config);
        if(document.fonts && document.fonts.status==='loading') {document.fonts.ready.then(()=>requestAnimationFrame(refresh));return;}
        if(resourcesPendingOnLayout && latest>=0) jumpAnchor(latest);
        resourcesSettled=true;publishPages();
      };
      requestAnimationFrame(()=>requestAnimationFrame(refresh));
    });
    const finishLayout=()=>requestAnimationFrame(() => requestAnimationFrame(() => {
      if(revision!==layoutRevision) return;
      pagination.measure(shell,flow,config);
      imagesPendingOnLayout=Array.from(flow.querySelectorAll('img')).some(img=>!img.complete);
      resourcesPendingOnLayout=imagesPendingOnLayout || !!(document.fonts && document.fonts.status==='loading');
      if (previous && previous.anchor >= 0) jumpAnchor(previous.anchor);
      else if (!previous && destination.fragment && document.getElementById(destination.fragment)) {retainedAnchor=-1;locateRect(document.getElementById(destination.fragment).getBoundingClientRect());}
      else if (destination.anchor >= 0) jumpAnchor(destination.anchor);
      else if (destination.search) {
        const hit = searchHit;
        if (hit) { retainedAnchor = hit.start; jumpAnchor(hit.start); }
        else jumpFraction(destination.position);
      }
      else jumpFraction(destination.position);
      if(retainedAnchor<0) retainedAnchor=window.miuingAnchor();
      hasPosition=true;pendingInitial=null;
      // Keep one logical passage through consecutive layout changes. Page starts
      // differ across fonts; repeatedly capturing them would drift backward.
      requestAnimationFrame(()=>{
        if(revision!==layoutRevision) return;
        restoring=false;publish();
        if(typeof MIUing.searchMatch==='function') MIUing.searchMatch(searchHit?searchHit.index:0,searchHit?searchHit.total:0);
        if(typeof MIUing.ready==='function') MIUing.ready();
      });
    }));
    // A body-font choice must restore its passage using the final glyph metrics.
    // Existing revision checks let later navigation/configuration supersede it.
    if(face && document.fonts) document.fonts.load(`${next.fontSize}px "${face.family}"`,face.sample || ' ').catch(()=>[]).then(()=>{
      if(revision!==layoutRevision) {
        if(config===next && !restoring) {
          // Keep the newer navigation passage visible after glyphs reflow;
          // do not restore the older destination or write progress again.
          const latest=retainedAnchor>=0?retainedAnchor:publishedAnchor;
          pagination.measure(shell,flow,config);
          if(latest>=0) jumpAnchor(latest);
          publishPages();
        }
        return;
      }
      finishLayout();
    });
    else finishLayout();
  };
  document.addEventListener('selectionchange', () => {
    clearTimeout(selectionTimer);
    const selection = getSelection();
    if (!selection.rangeCount || selection.isCollapsed) {
      selectionTimer=setTimeout(()=>MIUing.selection('',-1,-1),200);return;
    }
    const range = selection.getRangeAt(0);
    if (!flow || !flow.contains(range.startContainer) || !flow.contains(range.endContainer)) return;
    MIUing.selection(selection.toString(), textIndex.offsetOf(range.startContainer, range.startOffset), textIndex.offsetOf(range.endContainer, range.endOffset));
  });
  window.miuingAdvance = direction => {
    if (!config || config.scrolling || getSelection().toString()) return;
    beginNavigation();
    const remaining = extent() - shell.scrollLeft;
    if (direction > 0 && remaining <= config.width + 1) scrollReader(extent(),shell.scrollTop);
    else if (direction < 0 && shell.scrollLeft <= config.width + 1) scrollReader(0,shell.scrollTop);
    else scrollReader(Math.max(0, Math.min(extent(), (Math.round(shell.scrollLeft / config.width) + direction) * config.width)),shell.scrollTop);
    publish();
  };
  function turn(direction) {
    if (direction > 0 && extent() - shell.scrollLeft <= 1) MIUing.chapter(1);
    else if (direction < 0 && shell.scrollLeft <= 1) MIUing.chapter(-1);
    else if (typeof MIUing.turn === 'function') MIUing.turn(direction);
    else window.miuingAdvance(direction);
  }
  const gestures=new window.MIUingReaderGestures({
    config:()=>config,revision:()=>layoutRevision,turn,advance:direction=>window.miuingAdvance(direction),
    target:direction=>{
      if(!config || restoring || config.scrolling || getSelection().toString()) return null;
      const max=extent(),at=shell.scrollLeft;
      if(direction>0 && max-at<=1) return config.hasNextChapter?{chapterDelta:1,position:0}:null;
      if(direction<0 && at<=1) return config.hasPreviousChapter?{chapterDelta:-1,position:1}:null;
      const next=direction>0 && max-at<=config.width+1?max:direction<0 && at<=config.width+1?0:Math.max(0,Math.min(max,(Math.round(at/config.width)+direction)*config.width));
      return {chapterDelta:0,position:max?next/max:0};
    }
  });
  window.miuingFinishDrag=(id,commit)=>gestures.finish(id,commit);
})();
