# Reading history implementation plan

> **For agentic workers:** Use executing-plans inline to implement this plan task-by-task.

**Goal:** Return to the original passage after deliberate EPUB/TXT navigation.

**Architecture:** A small location value and a saveable list of at most 20 sources live in the reader session. ReaderScreen routes deliberate jumps through one function; ReadingMenu owns its slider preview. TextReaderChrome renders a return icon in its existing bottom-left slot.

**Tech Stack:** Kotlin, Compose saveable state, existing WebView bridge and UIAutomator.

## Global constraints

- Android API26 minimum, API36 target; no new dependencies or permissions.
- Preserve UTF-16 anchors and existing progress fallback.
- Ordinary page turns/scrolling do not add history; close/reopen clears it; Activity recreation retains it.
- Avoid generic navigation infrastructure and unrelated refactoring.

## Task 1: Real UI regressions

Files: `app/src/androidTest/java/com/miuing/reader/ReaderChromeTest.kt`.

- [x] Add consecutive same/cross-chapter jumps with return after recreation, anchor assertions, no history on ordinary movement, unchanged viewport, and session clearing.
- [x] Add slider seek/return regression proving the original passage is retained.
- [x] Build existing APK/tests and run new cases against baseline; require failure because return action is missing.

## Task 2: Minimal implementation

Files: new `app/src/main/java/com/miuing/reader/ReadingLocation.kt`; modify `ReaderScreen.kt`, `ReadingMenu.kt`, `TextReaderChrome.kt` in the same directory.

- [x] Save locations as `(chapter, position, anchor)` via a Compose listSaver; keep only 20 unique adjacent sources.
- [x] Add `navigate(c:Int,p:Float=0f,a:Int=-1,target:String="")` in ReaderScreen. Capture source only when the current text reader has published its location, then save the target once and increment jump. Return pops without recording the destination. Disable new navigation while the destination is loading.
- [x] Route links, contents, bookmarks, notes, search and menu chapter/seek through it; keep natural chapter movement outside history. Clear text selection on deliberate navigation.
- [x] Move slider preview state into ReadingMenu, change seek callback to `(Float)->Unit`, and remove the redundant parent position preview callback.
- [x] Add optional return callback to TextReaderChrome in the existing bottom-left 48dp slot; give the icon content description `返回跳转前位置`.
- [x] Run targeted tests GREEN; check chapter, exact retained anchors, recreation and source reset.

## Task 3: Verification and delivery

- [x] Self-review all navigation call sites, loading state, bounded history and saveable state; fix material findings with regression coverage.
- [x] Run `:core:check :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` and both JS syntax checks.
- [x] Run full instrumentation suite on API36 phone, API36 tablet window (1600×1200/160dpi) and API26; capture return control screenshots and verify viewport/buttons through UI assertions. Restore device display settings.
- [x] Record results and limitations; update parity list and inspect diff/status. Deliver on the existing authorized feature branch.

## Final verification record · 2026-11-20

- API36 phone: 39/39 (313.436s); API26 phone: 39/39 (285.166s); API36 tablet window: 39/39 (289.392s).
- Final Gradle build/check/lint: successful; core: 12/12; JVM: 1/1; lint: 0 errors, 20 warnings; both reader scripts pass syntax checks.
- Ruling: replace the earlier image-inspection limitation with actual visual checks — image viewing is available in the resumed session. Phone and tablet screenshots show readable text and unobstructed controls; exact Apple Books visual parity remains outside this verification.
- Final APK repackaging changed ZIP size. All 279 ZIP entries match the tested installation byte-for-byte after decompression (SHA256 per entry); version remains 0.6.3/code10. No code changed during final device verification.
- Existing review fixes and RED→GREEN evidence are recorded in `docs/releases/reading-history-0.6.3.md`; no additional features were added during final verification.
- Publication: commit and push to `feat/miuing-android`, then verify remote SHA and clean worktree. Keep the existing workspace and branch for continued development, as previously authorized.
