# Reader page turns implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline, test-driven-development and verification-before-completion. Continue in the user-authorized feature checkout/branch; no additional design confirmation required.

**Goal:** Add page-curl and fast-fade effects to EPUB/TXT pagination and correct reverse chapter navigation, without losing exact text anchors or selection.
**Architecture:** The app-owned reader script handles page boundaries and requests a native turn. A bounded temporary bitmap of the current WebView is drawn by a native overlay while the live renderer advances one viewport. Selection remains in the original DOM. Continuous scrolling bypasses the animation. Android disabled animations are respected.
**Tech Stack:** Existing Kotlin/Compose, WebView, Android Canvas/ValueAnimator, API26+; no new dependencies.
**Spec:** docs/superpowers/specs/2026-11-18-miuing-design.md. Apple official iPhone guide describes Curl, Fast Fade and Scroll: https://support.apple.com/zh-cn/guide/iphone/-iphc1af7c57/ios . Native fold shading is an approximation pending direct original-device comparison.

## Global constraints
- Keep MIUing identity, user icon, Android phone/tablet support and offline-only book access.
- Do not execute book-authored scripts; preserve CSP, text anchors, existing notes and settings.
- Continue on feat/miuing-android and push verified continuation as already authorized.

## Review focus
- A swipe must advance once, with no native horizontal scroll or trailing synthetic click.
- Previous chapter from its beginning opens the end of the previous chapter; chapter zero and final chapter remain bounded.
- Selection/link gestures must not turn the page; long presses preserve selection behavior.
- Reconfiguration/disposal during an animation must remove/recycle snapshots without publishing a stale location.
- Snapshot work must be bounded, remain readable on tablets, and bypass motion when system animations are off.

### Task 1: Turns and reverse navigation
- [x] Add device test with a real multi-chapter EPUB: tap/swipe advances one viewport; backward boundary reaches previous chapter end; selection suppresses turns; first/last limits hold.
- [x] Run against baseline and observe missing reverse-boundary behavior; implement miuingAdvance/native bridge, gesture ownership, and direction-sensitive chapter landing.
- [x] Run relevant device suite and record evidence.

### Task 2: Native effects and settings
- [x] Add failing settings/runtime test for curl/fade persistence and temporary overlay visibility while underlying text advances.
- [x] Add PageTurnOverlay.kt with a bounded snapshot, fold/backside/shadow rendering and fade mode; integrate lifetime with TextReader's WebView container.
- [x] Add page style choices to reading settings; migrate existing scrolling preference, preserve font/anchor behavior, cancel snapshots on disposal/reconfiguration.
- [x] Verify both effects, continuous scrolling, recreation and motion-disabled behavior; save real phone/tablet screenshots.

### Task 3: Verification and publication
- [x] Core/JVM/device full suites; phone/tablet/API26; build/lint; independent final review and material fixes.
- [x] Update version, README/parity/evidence and ledger; prepare verified commit for the authorized push. Commit/push identity is verified from Git after publication.

## Ledger
Baseline b6714ad, clean working tree. Existing feature checkout retained to continue the user's requested repository work and reuse its SDK/emulators. Full original visual equivalence is not claimed.

- Baseline core 12/12 and JVM geometry 1/1 passed. New gesture test failed: one swipe landed at 2.9778 viewports instead of 2. Fixed native-scroll double handling; gesture/navigation test passed.
- Missing page-style controls failed before implementation. Native overlay/settings tests subsequently passed; animations use one bitmap capped at two million pixels and 1200×2000 bounds. No new dependency or permission.
- Animation observation originally used ActivityScenario.onActivity, whose dependency bytecode confirms a waitForIdleSync barrier. Replaced transient-frame checks with main-thread execution without waiting for the animation to finish; interruption test calls actual Activity.recreate during a visible animation. The production layer did not need a change for this test issue.
- Final independent review found long-chapter percent-based boundary skipping. longChapterKeepsItsFirstAndLastPagesReachable reproduced both first/last page failures with >202 viewports before replacing percentages with a one-CSS-pixel boundary check.
- Final: minor (deferred): a distinct edge tap within 400ms after a swipe can be suppressed; retain for a later gesture-polish pass.
- Reviewer explicitly did not verify physical selection, tablet legibility, timing, or original-device visual equivalence; runtime checks and screenshots belong to this implementation's device verification, and original-device equivalence remains pending as documented in the approved spec.
- Additional full-suite finding: API26 PDF search displayed the target page but retained the previous-page number when a fragment remained above it. Failure screenshot/hierarchy showed page two occupying most of the viewport, saved index zero. Changed current-page reporting to largest visible area; full API26 suite then passed 14/14 (87.306s).
- Repeated phone anchor-test failure was a fixture identity error after an earlier interrupted run left a same-title book. Diagnostics showed the open book's previous notes/anchor while the asserted newly imported ID remained unopened. Use a unique title for this fixture; do not alter the reader to hide the error. Asynchronous persistence still has a bounded five-second wait.
- Final phone (1080×2400, 420 dpi) full suite passed 14/14 (97.534s). Final core 12/12, geometry 1/1, JavaScript syntax, build and lint succeeded; lint 0 errors / 24 warnings. Final APK hash is recorded in docs/releases/page-turns-0.4.md.
- Tablet first pass reproduced a final partial-spread rounding failure (last page false, first page true). When remaining distance is at most one viewport, advance directly to the physical end; keep viewport snapping for interior turns. Retest final APK on tablet/API26 plus portrait turn checks before publication.
- Final APK full suites: API35 tablet 14/14 (102.561s), API26 14/14 (86.245s). Tablet intermediate curl/fade frames visually inspected; full-width spread treatment is documented as an approximation, not original finger-following behavior.
- Final portrait delta verification passed PageTurnTest 4/4 (41.773s). Final lint succeeded with 0 errors / 24 warnings, and JavaScript syntax passed. Ready for authorized commit/push on the existing branch.
