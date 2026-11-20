# Reading sleep implementation plan

> Execute inline in the existing authorized feature branch; one final independent review.

**Goal:** Bound EPUB/TXT reading screen-on requests and restore system behavior outside reading.
**Architecture:** ReadingSleepGuard owns one View flag and one reusable timeout callback. Compose binds enablement and lifecycle; a small reading-settings panel owns the persisted switch.
**Tech Stack:** Existing Kotlin/Compose, Android View/Handler, lifecycle-runtime-compose.
**Spec:** docs/superpowers/specs/2026-11-20-reading-sleep-design.md.

## Constraints / review focus

- min26/target36, no new dependencies/permissions or system-setting writes; default enabled, 600000ms.
- Preserve pre-existing keepScreenOn, cancel callbacks on disposal, release on pause; new interaction re-arms an expired timer.
- Avoid motion-event consumption, recomposition resetting idle time, timers in the background, or changing PDF behavior.

### Task 1: Implement bounded reading sleep

- [x] Add UI regression in ReaderChromeTest: default enabled, switch off/on, recreation, lifecycle pause/resume and returning to library. Run RED on baseline.
- [x] Add ReadingSleep.kt with timer, lifecycle-bound modifier and switch panel; integrate ReaderScreen, ReaderPanel and ReadingMenu header. Keep global preference separate from themes.
- [x] Add real View short-duration instrumentation tests for expiry/re-arm/cancel and pre-existing flag; run targeted GREEN.

### Task 2: Verify and deliver

- [x] One independent read-only review; fix material findings with regression evidence.
- [x] Core/JVM/build/lint, targeted phone/API26/tablet verification and appropriate existing full regressions. Inspect setting screenshots; preserve original-reference limitations.
- [x] Bump 0.6.5/code12; record results, restore emulators, commit/push existing branch, verify remote SHA and clean status.

## Final verification · 2026-11-20

- Base UI RED → GREEN4/4. Review ownership regression RED → GREEN5/5 including native WebView touch and no reset on ordinary recomposition.
- API36 phone full50/50 (385.134s); API26 relevant29/29 (267.312s); API36 tablet relevant29/29 (304.037s). Core12/JVM1/build/lint0errors20warnings/JS syntax pass.
- Ruling: Android uses bounded View keepScreenOn rather than writing global sleep settings; physical sleep timing remains system policy, not exact Mac timeout addition. Relevant29 covers sleep4/chrome14/turn8/theme2/PDF UI1 on compatibility/tablet; unchanged parser and PDF data code get full coverage on main API36 phone.
- One Important ownership finding fixed with RED evidence. Minor deferred: initial lifecycle registration may replace the same pending callback once, with no accumulated requests or altered final state. Full record: docs/reading-sleep-0.6.5.md.
- Phone/tablet setting screenshots visually checked. Final APK0.6.5/code12 SHA256 f60bca19711262e3a327eb11f6db970486f8c4d9e6f79f4bead2bbdfdf5bf2be unchanged during verification.
- Restore test emulators; commit/push existing authorized branch and verify SHA/clean state. Preserve the existing development workspace; no merge or PR requested.
