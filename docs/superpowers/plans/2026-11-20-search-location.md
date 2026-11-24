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

- [x] Add real renderer tests for literal/case/cross-inline/emoji search, overlap/cleanup/node reuse, and consecutive initial configuration. Add actual UI tests for same/cross-chapter result visibility, unchanged source while typing, return and recreation after moving past a hit.
- [x] Build tests and observe failures on 0.6.3.
- [x] Implement cached-index matching and range compositing without HTML injection; integrate pending initial search before progress fallback, with a saved anchor taking priority on recreation.
- [x] Separate selected and edited queries, clear the selected query on other navigation, remove native auto-find side effects, and run targeted tests GREEN.

### Task 2: Review and delivery

- [x] One independent read-only review, then fix material findings with RED→GREEN coverage.
- [x] Run core/JVM/build/lint and both JS syntax checks; run full device suites on API36 phone/tablet and API26.
- [x] Inspect phone/tablet search screenshots, document evidence and boundaries, bump 0.6.4/code11, and restore test devices.
- [x] Commit/push authorized branch and verify remote SHA and clean status.

## Final verification · 2026-11-20

- Task 1: initial RED 4/4; first implementation GREEN 7/7. Review adds 2 failing methods; fixed with boundary-aware DOM matching and shared lowercase/whitespace discovery. Targeted suite GREEN 9/9.
- Final production APK: 0.6.4/code11, SHA256 863554537df792c8ae4b6e413221a0ff53b4b9c434df1906618189b0d57ce7a1; unchanged across device verification.
- Phone API36: 45/45 (364.300s); tablet API36: 45/45 (338.183s); API26: 45/45 (304.442s). Core12/JVM1/build/JS syntax pass; lint0errors20warnings.
- One independent review; three Important findings fixed. UI diagnostics and experiments removed. Tests use the real clickable result parent with bounded delivery retry and explicit async restoration/dismissal waits. Test failure history and limitations are recorded in docs/releases/search-location-0.6.4.md.
- Phone/tablet screenshots visually checked. Preserve the existing authorized feature branch/workspace, restore test emulators, commit and push. No PR or merge is requested.
