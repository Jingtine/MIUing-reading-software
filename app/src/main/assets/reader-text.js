/* App-owned text offsets and highlights; book scripts remain disabled. */
(() => {
  if (typeof window.MIUingTextIndex === 'function') return;
  window.MIUingTextIndex = class {
    constructor(root) {
      this.root = root;
      this.index = null;
      this.offsets = null;
      this.notesKey = null;
      this.observer = new MutationObserver(() => this.contentChanged());
      this.observe();
    }

    observe() {
      this.observer.observe(this.root, { subtree: true, childList: true, characterData: true });
    }

    invalidate() {
      this.index = null;
      this.offsets = null;
      this.notesKey = null;
    }

    contentChanged() {
      this.invalidate();
    }

    flushChanges() {
      // Native selection/navigation can run in the same task as a DOM edit,
      // before MutationObserver's asynchronous callback has been delivered.
      if (this.observer.takeRecords().length) this.contentChanged();
      return this.notesKey === null;
    }

    entries() {
      this.flushChanges();
      if (this.index) return this.index;
      const walker = document.createTreeWalker(this.root, NodeFilter.SHOW_TEXT);
      const entries = [], offsets = new WeakMap();
      let node, offset = 0;
      while ((node = walker.nextNode())) {
        if (node.parentElement.closest('script,style,noscript')) continue;
        entries.push({ node, offset });
        offsets.set(node, offset);
        offset += node.length;
      }
      this.index = entries;
      this.offsets = offsets;
      return entries;
    }

    offsetOf(node, offset) {
      this.entries();
      const start = this.offsets.get(node);
      if (start !== undefined) return start + offset;
      // Element range endpoints are uncommon; retain the original DOM-range
      // behavior without copying the chapter on ordinary text selections.
      const prefix = document.createRange();
      prefix.selectNodeContents(this.root);
      try {
        prefix.setEnd(node, offset);
        const content = prefix.cloneContents();
        content.querySelectorAll('script,style,noscript').forEach(element => element.remove());
        return content.textContent.length;
      } catch (_) { return -1; }
    }

    rangeAt(offset) {
      const entries = this.entries();
      let low = 0, high = entries.length;
      while (low < high) {
        const middle = (low + high) >>> 1;
        const entry = entries[middle];
        if (entry.offset + entry.node.length <= offset) low = middle + 1;
        else high = middle;
      }
      let entry = entries[low];
      if (!entry && entries.length) {
        const last = entries[entries.length - 1];
        if (offset === last.offset + last.node.length) entry = last;
      }
      if (!entry) return null;
      const range = document.createRange();
      range.setStart(entry.node, Math.max(0, offset - entry.offset));
      range.collapse(true);
      return range;
    }

    findAll(query, limit = 1000) {
      if(!query || !query.trim()) return {hits:[],more:false};
      return this.find(query,0,Math.max(1,Math.min(1001,limit)));
    }
    find(query, ordinal = 0, collectLimit = 0) {
      if (!query || !query.trim()) return null;
      // Escape each literal part, allowing equivalent HTML whitespace without
      // changing the UTF-16 offsets returned by the actual DOM text match.
      const pattern = query.toLowerCase().trim().split(/\s+/).map(part => part.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('\\s+');
      this.entries();
      const walker = document.createTreeWalker(this.root, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT);
      const groups = new WeakMap(), chunks = [], parts = [];
      const block = element => {
        if (!element || element === this.root) return this.root;
        if (groups.has(element)) return groups.get(element);
        const display = getComputedStyle(element).display;
        const parent = block(element.parentElement);
        const result = display === 'none' || parent === null ? null : /^(block|list-item|table|flex|grid|flow-root)/.test(display) ? element : parent;
        groups.set(element, result); return result;
      };
      let node, previous = null, length = 0, broken = false;
      while ((node = walker.nextNode())) {
        if (node.nodeType === Node.ELEMENT_NODE) { if (node.tagName === 'BR') broken = true; continue; }
        const offset = this.offsets.get(node), group = block(node.parentElement);
        if (offset === undefined || !group || !node.length) continue;
        if (parts.length && (broken || previous !== group)) { parts.push('\n'); length++; }
        chunks.push({ at: length, offset, text: node.data });
        parts.push(node.data); length += node.length; previous = group; broken = false;
      }
      const text = parts.join(''), folded = text.toLowerCase();
      const wanted=Number.isFinite(ordinal)?Math.max(0,Math.floor(ordinal)):0;
      const expression=new RegExp(pattern,'g');
      let found,match=null,last=null,total=0;
      const collected=[];
      while((found=expression.exec(folded))) {
        if(total===wanted) match=found;
        last=found;total++;
        if(collectLimit) {if(collected.length<collectLimit) collected.push(found);else break;}
      }
      match=match || last;
      if (!match) return collectLimit?{hits:[],more:false}:null;
      // Most lowercasing preserves UTF-16 length. Handle expansions (e.g. İ)
      // without letting search-string offsets leak into stored DOM anchors.
      let plainCursor=0,lowerCursor=0;
      const originalOffset = (offset, end) => {
        if (text.length === folded.length) return offset;
        if(offset<lowerCursor) {plainCursor=0;lowerCursor=0;}
        while(plainCursor<text.length) {
          const size=text.codePointAt(plainCursor)>0xffff?2:1;
          const next=lowerCursor+text.slice(plainCursor,plainCursor+size).toLowerCase().length;
          if(offset<next) return plainCursor+(end && offset>lowerCursor?size:0);
          plainCursor+=size;lowerCursor=next;
        }
        return plainCursor;
      };
      const domOffset = (offset, isEnd) => {
        let low = 0, high = chunks.length;
        while (low < high) {
          const middle = (low + high) >>> 1;
          const chunk = chunks[middle], limit = chunk.at + chunk.text.length;
          if (offset < limit || (isEnd && offset === limit)) high = middle;
          else low = middle + 1;
        }
        if (low < chunks.length) return chunks[low].offset + Math.max(0, offset - chunks[low].at);
        const last = chunks[chunks.length - 1]; return last.offset + last.text.length;
      };
      if(collectLimit) {
        // Per-hit context adapted from Foliate-js search.js (MIT, John Factotum).
        const clean=str=>str.replace(/\s+/g,' ');
        return {hits:collected.map((value,index)=>{
          const a=originalOffset(value.index,false),b=originalOffset(value.index+value[0].length,true);
          return {index,start:domOffset(a,false),pre:clean(Array.from(text.slice(Math.max(0,a-110),a)).slice(-50).join('')),
            match:clean(text.slice(a,b)).slice(0,256),post:clean(Array.from(text.slice(b,b+110)).slice(0,50).join(''))};
        }),more:total>collectLimit};
      }
      const start=originalOffset(match.index,false),end=originalOffset(match.index+match[0].length,true);
      return { start: domOffset(start, false), end: domOffset(end, true), index:Math.min(wanted,total-1), total };
    }

    highlights(notes, query = '', ordinal = 0) {
      this.flushChanges();
      const key = JSON.stringify([notes, query, ordinal]);
      if (key === this.notesKey) return this.searchHit || null;
      // These edits preserve the text. Invalidate the node index explicitly,
      // but don't treat our own wrapping as a new book-content revision.
      this.observer.disconnect();
      try {
        this.root.querySelectorAll('mark[data-miuing],mark[data-miuing-search]').forEach(mark => mark.replaceWith(...mark.childNodes));
        this.root.normalize();
        this.invalidate();
        if (!notes.length && !query) { this.notesKey = key;this.searchHit=null;return null; }
        const entries = this.entries();
        const last = entries[entries.length - 1];
        const length = last ? last.offset + last.node.length : 0;
        let plain;
        const ranges = [];
        for (const note of notes) {
          let start = note.start, end = note.end;
          if (!(start >= 0 && end > start)) {
            if (plain === undefined) plain = entries.map(entry => entry.node.data).join('');
            start = plain.indexOf(note.text);
            end = start + note.text.length;
          }
          end = Math.min(end, length);
          if (start >= 0 && end > start) ranges.push({ start, end });
        }
        const hit = this.find(query,ordinal);
        this.searchHit=hit;
        // Composite note union and the temporary search hit into disjoint
        // segments; overlaps retain both meanings without nested marks.
        const events = [];
        for (const range of ranges) {
          events.push({ at: range.start, note: 1, search: 0 }, { at: range.end, note: -1, search: 0 });
        }
        if (hit) events.push({ at: hit.start, note: 0, search: 1 }, { at: hit.end, note: 0, search: -1 });
        events.sort((a, b) => a.at - b.at);
        const merged = [];
        let at = 0, noteCount = 0, searchCount = 0;
        for (const event of events) {
          if (event.at > at && (noteCount || searchCount)) {
            const previous = merged[merged.length - 1];
            const note = noteCount > 0, search = searchCount > 0;
            if (previous && previous.end === at && previous.note === note && previous.search === search) previous.end = event.at;
            else merged.push({ start: at, end: event.at, note, search });
          }
          noteCount += event.note; searchCount += event.search; at = event.at;
        }
        let first = 0;
        for (const entry of entries) {
          if (!entry.node.length) continue;
          const limit = entry.offset + entry.node.length;
          while (first < merged.length && merged[first].end <= entry.offset) first++;
          if (first === merged.length) break;
          if (merged[first].start >= limit) continue;
          const fragment = document.createDocumentFragment();
          let cursor = 0;
          for (let i = first; i < merged.length && merged[i].start < limit; i++) {
            const a = Math.max(0, merged[i].start - entry.offset);
            const b = Math.min(entry.node.length, merged[i].end - entry.offset);
            if (a > cursor) fragment.appendChild(document.createTextNode(entry.node.data.slice(cursor, a)));
            const mark = document.createElement('mark');
            if (merged[i].note) mark.dataset.miuing = '1';
            if (merged[i].search) mark.dataset.miuingSearch = '1';
            mark.textContent = entry.node.data.slice(a, b);
            fragment.appendChild(mark);
            cursor = b;
          }
          if (cursor < entry.node.length) fragment.appendChild(document.createTextNode(entry.node.data.slice(cursor)));
          entry.node.replaceWith(fragment);
        }
        this.notesKey = key;
        return hit;
      } finally {
        this.index = null;
        this.offsets = null;
        this.observe();
      }
    }
  };
})();
