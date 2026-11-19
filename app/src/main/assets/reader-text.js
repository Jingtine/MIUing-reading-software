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

    highlights(notes) {
      this.flushChanges();
      const key = JSON.stringify(notes);
      if (key === this.notesKey) return;
      // These edits preserve the text. Invalidate the node index explicitly,
      // but don't treat our own wrapping as a new book-content revision.
      this.observer.disconnect();
      try {
        this.root.querySelectorAll('mark[data-miuing]').forEach(mark => mark.replaceWith(...mark.childNodes));
        this.root.normalize();
        this.invalidate();
        if (!notes.length) { this.notesKey = key; return; }
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
        ranges.sort((a, b) => a.start - b.start || a.end - b.end);
        const merged = [];
        for (const range of ranges) {
          const previous = merged[merged.length - 1];
          if (previous && range.start <= previous.end) previous.end = Math.max(previous.end, range.end);
          else merged.push({ start: range.start, end: range.end });
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
            mark.dataset.miuing = '1';
            mark.textContent = entry.node.data.slice(a, b);
            fragment.appendChild(mark);
            cursor = b;
          }
          if (cursor < entry.node.length) fragment.appendChild(document.createTextNode(entry.node.data.slice(cursor)));
          entry.node.replaceWith(fragment);
        }
        this.notesKey = key;
      } finally {
        this.index = null;
        this.offsets = null;
        this.observe();
      }
    }
  };
})();
