/* Application-owned renderer. Book-authored scripts remain blocked by CSP. */
(() => {
  if (window.miuingConfigure) return;
  let config, shell, flow, restoring = false, timer, selectionTimer, sx = 0, sy = 0, suppressTouchClick = false, touchAt = 0, selectedAtTouch = false;
  let retainedAnchor = -1, layoutRevision = 0;
  let hasPosition = false, pendingInitial = null;
  const publisherTextStyles = new Map();
  let textIndex;
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
  function beginNavigation() {retainedAnchor=-1;layoutRevision++;restoring=false;hasPosition=true;pendingInitial=null;clearTimeout(timer);}
  function jumpFraction(fraction) {
    const at=Math.max(0,Math.min(1,fraction))*extent();
    const page=fraction>=1?extent():Math.min(extent(),Math.round(at/config.width)*config.width);
    shell.scrollTo(config.scrolling ? 0 : page, config.scrolling ? at : 0);
  }
  window.miuingJump = fraction => {beginNavigation();jumpFraction(fraction);publish();};
  function locateRect(rect) {
    const box = shell.getBoundingClientRect();
    if (config.scrolling) shell.scrollTop += rect.top - box.top - config.margin;
    else shell.scrollLeft = Math.max(0, Math.floor((rect.left - box.left + shell.scrollLeft - config.margin + 1) / config.width) * config.width);
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
    if(config.scrolling || typeof MIUing.pages!=='function') return;
    const total=Math.ceil(extent()/config.width)+1;
    const current=extent()-shell.scrollLeft<=1?total:Math.min(total,Math.floor((shell.scrollLeft+1)/config.width)+1);
    MIUing.pages(current,total);
  }
  function publish() {
    if (restoring) return;
    MIUing.progress(window.miuingProgress(), retainedAnchor>=0?retainedAnchor:window.miuingAnchor());
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
    const previous = config && hasPosition ? { anchor: retainedAnchor>=0?retainedAnchor:window.miuingAnchor(), position: window.miuingProgress() } : null;
    if(!config) pendingInitial={anchor:next.anchor>=0?next.anchor:-1,fragment:next.fragment || '',position:next.position || 0,search:next.search || ''};
    const destination=previous || pendingInitial || {anchor:-1,position:0,fragment:''};
    retainedAnchor=destination.anchor>=0?destination.anchor:-1;
    const revision=++layoutRevision;clearTimeout(timer);
    config = next; window.miuingConfig = next;
    window.miuingWidth = next.width; window.miuingHeight = next.height;
    restoring = true;
    if (!flow) {
      flow = document.createElement('main'); flow.id = 'miuing-content';
      while (document.body.firstChild) flow.appendChild(document.body.firstChild);
      textIndex = new window.MIUingTextIndex(flow);
      shell = document.createElement('div'); shell.id = 'miuing-scroll'; shell.appendChild(flow); document.body.appendChild(shell);
      shell.addEventListener('scroll', () => {if(restoring) return;retainedAnchor=-1;clearTimeout(timer);timer=setTimeout(publish,180);});
    }
    let style = document.getElementById('miuing-style');
    if (!style) { style = document.createElement('style'); style.id = 'miuing-style'; document.head.appendChild(style); }
    const w = next.width, h = next.height, m = next.margin, available = w - 2 * m;
    const column = w >= 900 ? (w - 4 * m) / 2 : available;
    const families={serif:'serif',sans:'sans-serif',mono:'monospace'};
    const family=next.fontFamily==='book'?null:(families[next.fontFamily] || (next.serif?'serif':'sans-serif'));
    const fontCss=family?`font-family:${family}!important;`:'';
    const layoutCss=next.customLayout===false?'':`line-height:${next.lineHeight}!important;letter-spacing:${next.letterSpacing}em!important;word-spacing:${next.wordSpacing || 0}em!important;`;
    style.textContent = `
      html,body{margin:0!important;padding:0!important;width:${w}px!important;height:${h}px!important;overflow:hidden!important;background:${next.paper}!important;color:${next.ink}!important}
      body{font-size:${next.fontSize}px!important;${layoutCss}${fontCss}${next.bold?'font-weight:700!important;':''}overflow-wrap:break-word}
      #miuing-scroll{width:${w}px;height:${h}px;overflow-x:hidden;overflow-y:${next.scrolling?'auto':'hidden'};scrollbar-width:none}
      #miuing-content{box-sizing:content-box;width:${available}px;margin:${m}px;${next.scrolling?`max-width:760px;margin:${m}px auto;padding:0 ${m}px;`:`height:${h-2*m}px;column-width:${column}px;column-gap:${2*m}px;column-fill:auto;`}}
      p{font-size:inherit!important;${next.customLayout===false?'':'line-height:inherit!important;letter-spacing:inherit!important;word-spacing:inherit!important;'}color:inherit!important;${next.justify?'text-align:justify!important;':''}}img,svg{max-width:100%!important;max-height:${h-2*m}px!important;object-fit:contain}mark[data-miuing]{background:#e8ce6c;color:inherit}mark[data-miuing-search]{background:#ffb95b;color:#222}
    `;
    textIndex.highlights(next.notes || [], next.search || '');
    textStyleOverrides(family,next);
    requestAnimationFrame(() => requestAnimationFrame(() => {
      if(revision!==layoutRevision) return;
      if (previous && previous.anchor >= 0) jumpAnchor(previous.anchor);
      else if (!previous && destination.fragment && document.getElementById(destination.fragment)) {retainedAnchor=-1;locateRect(document.getElementById(destination.fragment).getBoundingClientRect());}
      else if (destination.anchor >= 0) jumpAnchor(destination.anchor);
      else if (destination.search) {
        const hit = textIndex.find(destination.search);
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
        // Publisher fonts may finish after the first layout. Refresh metrics
        // for this configuration without writing progress or undoing navigation.
        if(document.fonts) document.fonts.ready.then(()=>requestAnimationFrame(()=>{
          if(config===next && !restoring) publishPages();
        }));
      });
    }));
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
    if (direction > 0 && remaining <= config.width + 1) shell.scrollLeft = extent();
    else if (direction < 0 && shell.scrollLeft <= config.width + 1) shell.scrollLeft = 0;
    else shell.scrollLeft = Math.max(0, Math.min(extent(), (Math.round(shell.scrollLeft / config.width) + direction) * config.width));
    publish();
  };
  function turn(direction) {
    if (direction > 0 && extent() - shell.scrollLeft <= 1) MIUing.chapter(1);
    else if (direction < 0 && shell.scrollLeft <= 1) MIUing.chapter(-1);
    else if (typeof MIUing.turn === 'function') MIUing.turn(direction);
    else window.miuingAdvance(direction);
  }
  document.addEventListener('pointerdown', () => { suppressTouchClick=false; }, { passive:true });
  document.addEventListener('touchstart', e => {
    suppressTouchClick=false;
    sx = e.changedTouches[0].clientX; sy = e.changedTouches[0].clientY; touchAt=Date.now();
    selectedAtTouch=!!getSelection().toString();
  }, { passive:true });
  document.addEventListener('touchend', e => {
    if (!config || config.scrolling || e.target.closest('a')) return;
    if (selectedAtTouch || getSelection().toString()) {suppressTouchClick=true;return;}
    const dx = e.changedTouches[0].clientX - sx, dy = e.changedTouches[0].clientY - sy;
    if (Math.abs(dx)>55 && Math.abs(dx)>Math.abs(dy)) { suppressTouchClick=true;turn(dx<0?1:-1); e.preventDefault(); }
    else if (Date.now()-touchAt>600) suppressTouchClick=true;
  }, { passive:false });
  document.addEventListener('click', e => {
    // Keep suppressing this touch's click even if rendering delays its delivery.
    // A new pointer/touch resets the latch; detail=0 activates via keyboard/AT.
    if (!config || (suppressTouchClick && e.detail!==0) || e.target.closest('a') || getSelection().toString()) return;
    if (!config.scrolling && e.clientX<config.width*.22) turn(-1);
    else if (!config.scrolling && e.clientX>config.width*.78) turn(1);
    else MIUing.tap();
  });
})();
