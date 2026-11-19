# Text engine optimization plan

Use executing-plans inline, test-driven-development and verification-before-completion. Continue in the authorized feature checkout and publish verified progress.

Goal: reduce repeated chapter traversal and highlight DOM work for EPUB/TXT while preserving UTF-16 anchors, publisher styles, selection and offline behavior.

Architecture: a cohesive reader-text.js module owns a lazy text-node/offset index and batched highlights. MutationObserver records invalidate the index for text/child changes, including synchronous changes before observer delivery. Reader.js keeps layout/navigation. TextReader loads the application scripts once per document; later updates send configuration only. No new dependency or generic cache framework.

Spec: existing MIUing design, user priority text reading/Android16/maintainable code. Baseline b0cd564 clean. Original layout/menus and PDF features remain unchanged.

Review focus: same-task text mutation must not use stale offsets; highlight splitting and removal must invalidate old nodes; element endpoints/end-of-document/empty text must preserve existing behavior; overlapping/legacy notes must preserve text and visible coverage; style-only changes must not invalidate text indices or destroy active selection. Avoid wall-clock assertions and unmeasured FPS claims.

- [x] Add real WebView regressions and operation-count measurements: repeated anchor lookups; same-task child/character changes and highlight selection; many/overlapping notes and unchanged-note typography updates. Run RED against existing renderer.
- [x] Implement indexed lookup, synchronous invalidation, batched highlight ranges and unchanged-note fast path; wire one application-script loader. Keep existing public renderer APIs. Run targeted GREEN and record counts/timings.
- [x] One independent read-only review, material fixes RED→GREEN, full API36 phone/tablet and legacy regression; core/JVM/build/lint/syntax and relevant screenshots.
- [x] Publish 0.6.2 with evidence/limitations, commit/push and verify SHA/clean state.

Ledger: baseline script walks textNodes repeatedly in rangeAt/offsetOf/highlights. Every typography config unwraps/normalizes/recreates identical marks. Alternative of arbitrary debounce/delayed updates was rejected because it changes user responsiveness; cache invalidation and one batched DOM pass address the measured work directly. No source change before regression/measurement RED.
