/* Physical columns, measured from rendered content only when layout changes. */
window.MIUingPagination=class {
  constructor() {this.tail=null;this.total=0;this.columns=1;}
  reset() {if(this.tail) this.tail.remove();this.tail=null;this.total=0;}
  measure(shell,flow,config) {
    if(config.scrolling || config.width<=2*config.margin || config.height<=2*config.margin) {this.reset();return;}
    const w=config.width,m=config.margin,columns=w>=900?2:1,step=w/columns;
    const box=shell.getBoundingClientRect(),range=document.createRange();
    range.selectNodeContents(flow);
    let end=0;
    for(const rect of range.getClientRects()) {
      if(rect.width>.5 && rect.height>.5) end=Math.max(end,rect.right-box.left+shell.scrollLeft-m);
    }
    const total=Math.max(1,Math.ceil((end-.5)/step));
    if(!Number.isFinite(total) || total>1000000) {this.reset();return;}
    this.total=total;this.columns=columns;
    if(!this.tail) {
      this.tail=document.createElement('div');this.tail.setAttribute('aria-hidden','true');
      this.tail.style.cssText='position:absolute;top:0;width:1px;height:1px;visibility:hidden;pointer-events:none';
      shell.appendChild(this.tail);
    }
    // Pad the last spread, not the text flow: an odd last column must not
    // scroll back half a spread and repeat the preceding page.
    this.tail.style.left=`${Math.ceil(total/columns)*w-1}px`;
  }
  pages(shell,config) {
    if(!this.total || config.scrolling) return null;
    const spreads=Math.ceil(this.total/this.columns),spread=Math.max(1,Math.min(spreads,Math.round(shell.scrollLeft/config.width)+1));
    const first=Math.min(this.total,(spread-1)*this.columns+1);
    return {first,last:Math.min(this.total,first+this.columns-1),total:this.total,columns:this.columns,spread,spreads};
  }
};
