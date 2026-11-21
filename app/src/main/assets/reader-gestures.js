/* Input ownership is separate from publication layout and persistence. */
window.MIUingReaderGestures = class {
  constructor(actions) {
    this.actions=actions;this.sequence=0;this.pending=null;this.touch=null;this.suppress=false;this.chapterPending=false;
    document.addEventListener('pointerdown',()=>{this.suppress=false;},{passive:true});
    document.addEventListener('touchstart',e=>this.start(e),{passive:true});
    document.addEventListener('touchmove',e=>this.move(e),{passive:false});
    document.addEventListener('touchend',e=>this.end(e),{passive:false});
    document.addEventListener('touchcancel',()=>{this.cancel();this.touch=null;this.suppress=true;},{passive:true});
    document.addEventListener('click',e=>{
      if(this.chapterPending || (this.suppress && e.detail!==0) || this.excluded(e)) return;
      if(this.pending) {const p=this.pending;this.finish(p.id,p.ended && p.commit);MIUing.dragAbort(p.id);}
      if(this.chapterPending) return;
      const c=actions.config();if(!c) return;
      if(!c.scrolling && e.clientX<c.width*.22) actions.turn(-1);
      else if(!c.scrolling && e.clientX>c.width*.78) actions.turn(1);
      else MIUing.tap();
    });
  }
  excluded(e) {return !!getSelection().toString() || !!(e.target.closest && e.target.closest('a'));}
  cancel() {
    const p=this.pending;this.pending=null;
    if(p) MIUing.dragAbort(p.id);
    if(this.touch) this.touch.blocked=true;
  }
  finish(id,commit) {
    const p=this.pending;
    if(!p || p.id!==id) return 0;
    this.pending=null;
    if(this.touch) this.touch.blocked=true;
    if(!commit || p.revision!==this.actions.revision()) return 0;
    if(p.target.chapterDelta) {this.chapterPending=true;MIUing.chapter(p.target.chapterDelta);return 2;}
    this.actions.advance(p.direction);return 1;
  }
  start(e) {
    this.suppress=false;
    if(this.chapterPending) {this.touch=null;this.suppress=true;return;}
    if(this.pending) {
      const p=this.pending;
      this.finish(p.id,p.ended && p.commit);MIUing.dragAbort(p.id);
      if(this.chapterPending) {this.touch=null;this.suppress=true;return;}
    }
    const t=e.changedTouches[0],now=Date.now();
    this.touch={x:t.clientX,y:t.clientY,at:now,blocked:this.excluded(e) || (e.touches && e.touches.length!==1),direction:0,progress:0,samples:[{x:t.clientX,at:now}]};
  }
  move(e) {
    const t=this.touch,c=this.actions.config();if(!t || !c || c.scrolling) return;
    if((e.touches && e.touches.length!==1) || this.excluded(e)) {this.cancel();this.suppress=true;return;}
    if(t.blocked) return;
    const point=e.changedTouches[0],dx=point.clientX-t.x,dy=point.clientY-t.y,now=Date.now();
    if(!t.direction) {
      if(Math.abs(dy)>12 && Math.abs(dy)>=Math.abs(dx)) {t.blocked=true;return;}
      if(Math.abs(dx)<12 || Math.abs(dx)<=Math.abs(dy)) return;
      if(now-t.at>600) {t.blocked=true;return;}
      if(!c.dragEnabled || c.pageTurn!=='curl' || typeof MIUing.dragBegin!=='function') return;
      t.direction=dx<0?1:-1;
      const target=this.actions.target(t.direction);
      this.suppress=true;
      if(!target) {t.blocked=true;e.preventDefault();return;}
      const id=++this.sequence;
      this.pending={id,direction:t.direction,target,revision:this.actions.revision(),ended:false,commit:false};
      MIUing.dragBegin(id,t.direction,target.chapterDelta,target.position);
    }
    t.progress=Math.max(0,Math.min(1,-dx*t.direction/c.width));
    t.samples.push({x:point.clientX,at:now});
    t.samples=t.samples.filter(s=>now-s.at<=100).slice(-6);
    if(this.pending) MIUing.dragMove(this.pending.id,t.progress);
    e.preventDefault();
  }
  end(e) {
    const t=this.touch,c=this.actions.config();this.touch=null;
    if(!t || !c || c.scrolling) return;
    if(t.blocked || this.excluded(e)) {this.cancel();this.suppress=true;return;}
    if(t.direction && this.pending) {
      const now=Date.now(),first=t.samples.find(s=>now-s.at<=100),last=e.changedTouches[0];
      const velocity=first && now>first.at ? -(last.clientX-first.x)*t.direction/(now-first.at) : 0;
      const p=this.pending;p.ended=true;p.commit=t.progress>=.28 || (t.progress>=.08 && velocity>=.6);
      MIUing.dragEnd(p.id,p.commit);this.suppress=true;e.preventDefault();return;
    }
    const dx=e.changedTouches[0].clientX-t.x,dy=e.changedTouches[0].clientY-t.y;
    if(Math.abs(dx)>55 && Math.abs(dx)>Math.abs(dy)) {this.suppress=true;this.actions.turn(dx<0?1:-1);e.preventDefault();}
    else if(Date.now()-t.at>600) this.suppress=true;
  }
};
