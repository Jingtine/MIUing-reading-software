# Search location implementation plan

> Execute inline with executing-plans. Use the existing authorized feature branch.

**Goal:** Locate the first actual text match when an EPUB/TXT chapter search result is selected, preserving the source for return.

**Architecture:** Selected query is distinct from the editable query. The initial reader destination uses the DOM text index; temporary search and stored note ranges share one text-preserving renderer.

**Tech Stack:** Existing Kotlin/Compose/WebView and application-owned JS; no dependencies.

**Spec:** `docs/superpowers/specs/2026-11-20-search-location-design.md`.

## Global constraints

- min26, compile/target36; EPUB/TXT priority; no new dependencies or permissions.
- Keep original book content and stored note ranges unchanged; first match per chapter only.
- Preserve UTF-16 anchors, source history and ordinary page/scroll behavior.

## Review focus

- Literal regex punctuation and case handling must never execute a user pattern.
- Adjacent/overlapping note/search ranges must not duplicate text or destroy notes.
- Reconfiguration before initial layout must retain the selected search destination.
- Typing a query and changing chapter naturally must not cause unsolicited scrolling.
- Recreation after reading past a hit must retain the current passage rather than returning to the hit.

### Task 1: Search destination and transient ranges

Files: `reader-text.js`, `reader.js`, `TextReader.kt`, `ReaderScreen.kt`; tests in `ReaderEngineTest.kt` and `ReaderChromeTest.kt`.

Interfaces: `MIUingTextIndex.find(query)` returns `{start,end}` or null in current DOM UTF-16 offsets; `highlights(notes, query='')` draws persistent note union and one temporary search hit. Configuration `search` is the selected query captured for the WebView, not editable text. `navigate(...,find:String="")` records source and selected query atomically.

- [ ] Add real renderer tests for literal/case/cross-inline/emoji search, overlap/cleanup/node reuse, and consecutive initial configuration. Add actual UI tests for same/cross-chapter result visibility, unchanged source while typing, return and recreation after moving past a hit.
- [ ] Build tests and observe failures on 0.6.3.
- [ ] Implement cached-index matching and range compositing without HTML injection; integrate pending initial search before progress fallback, with a saved anchor taking priority on recreation.
- [ ] Separate selected and edited queries, clear the selected query on other navigation, remove native auto-find side effects, and run targeted tests GREEN.

### Task 2: Review and delivery

- [ ] One independent read-only review, then fix material findings with RED→GREEN coverage.
- [ ] Run core/JVM/build/lint and both JS syntax checks; run full device suites on API36 phone/tablet and API26.
- [ ] Inspect phone/tablet search screenshots, document evidence and boundaries, bump 0.6.4/code11, and restore test devices.
- [ ] Commit/push authorized branch and verify remote SHA and clean status.
