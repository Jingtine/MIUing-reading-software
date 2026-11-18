# Reader input and drawing optimization plan

> **For agentic workers:** Use superpowers:executing-plans inline with systematic-debugging, test-driven-development and verification-before-completion. Continue the approved feature checkout and push verified work as authorized.

**Goal:** Fix lost rapid page-turn input and remove per-frame Path/RectF/gradient allocation from the existing animation.
**Architecture:** A fresh pointer/touch begins a fresh click-suppression lifetime; suppress its trailing touch click until the next gesture rather than using a timer, and retain detail-zero keyboard/AT activation. When another turn arrives during a snapshot animation, finish the old visual layer and advance the live reader directly. Cache drawing primitives and normalized shaders on the overlay, transform them for each frame, and retain existing bitmap bounds/disposal.
**Tech Stack:** Existing Kotlin/Compose/WebView/Canvas, API26+; no new dependency or permission.
**Spec:** docs/superpowers/specs/2026-11-18-miuing-design.md; continuation of docs/superpowers/plans/2026-11-18-page-turns.md.

## Constraints and review focus
- Preserve offline content isolation, exact anchors, text selection, chapter boundaries, scrolling and reduced-motion behavior.
- Suppress the synthetic click belonging to a swipe, while accepting a distinct touch immediately afterward.
- A rapid turn must count once; disposed frames must not restart or leak/reuse recycled bitmaps.
- Reused gradients must retain identical positioning for forward/reverse turns and phone/tablet sizes.
- Do not claim measured FPS or battery improvement without measurements; verify allocation warnings and real rendered frames.

### Task 1: Reproduce and fix input loss
- [x] Add device regressions: a swipe plus its synthetic click plus a fresh edge tap advances twice; a turn arriving during a visible animation advances twice and releases the old overlay.
- [x] Run both on the baseline and observe failures for the two independent causes.
- [x] Reset suppression on fresh touch and interrupt an active animation without dropping the new turn. Run the full page-turn suite.

### Task 2: Reuse drawing resources
- [x] Cache RectF, Path, Matrix and normalized fold/shadow shaders; preserve bitmap limits and Canvas restore/alpha behavior.
- [x] Run lint and confirm DrawAllocation findings in PageTurnOverlay are eliminated; inspect phone/tablet frames and verify reverse/cancellation behavior.

### Task 3: Verify and publish
- [x] Core/JVM/device suites on API35 phone/tablet and API26, build/lint/syntax; independent final review and required fixes.
- [x] Update 0.4.1 version/evidence/parity and prepare verified work for the authorized commit/push; Git verifies remote SHA and clean tree after publication.

## Ledger
Baseline 8985e32, clean tree. Root causes confirmed in source: reader.js retains a 400ms click suppression across gestures; PageTurnOverlay.turn returns whenever a snapshot exists. Lint reports six drawing allocation warnings in the overlay (24 total warnings). This optimization does not add finger-following page deformation.

- Baseline core 12/12 and geometry 1/1 passed. Both new input tests failed (expected two turns, actual one), then passed 2/2 in 6.465s after fresh-touch reset and active-animation interruption.
- Cached draw resources built successfully; DrawAllocation findings dropped from six to zero and lint warnings from 24 to 18, with zero errors. No FPS or battery claim is inferred.
- Independent static review approved cached shader transforms, canvas/alpha restoration, interruption and stale-frame guards; no actionable issues. It did not claim device timing or original-device equivalence; finger-following remains an already documented pending feature.
- First full suites each exposed a physical-swipe timing regression. Added event diagnostics and a deterministic delayed-click case; the latter failed (expected page one, actual page zero) with the 400ms window. Replaced it with a gesture latch, explicitly preserving detail-zero keyboard/AT clicks and resetting for new pointer/touch input. Full device verification is pending this final amendment.
- Final phone full suite passed 17/17 (97.460s), API26 full suite passed 17/17 (96.618s), including physical swipes and all three new regressions. Phone screenshot inspected; cached normalized shaders retain the fold/edge placement. Tablet run remains in progress.
- Final tablet full suite passed 17/17 (100.137s), including all seven page-turn cases and existing PDF/text behavior. Tablet screenshot inspected. Final syntax and diff checks passed; APK checksum is recorded in docs/turn-optimization-0.4.1.md. No deferred minor findings from this review; finger-following is an unchanged pending feature, not a claimed outcome of this optimization.
