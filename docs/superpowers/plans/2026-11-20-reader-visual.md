# Reader visual alignment implementation plan

> Execute inline in existing authorized feature branch, with one final read-only review.

**Goal:** Match the directly observed iOS18 reader chrome more closely without inventing whole-book page numbers.
**Architecture:** Pure overlay changes in TextReaderChrome, source chapter label from existing history, system serif fallback at WebView settings boundary only.
**Spec:** docs/superpowers/specs/2026-11-20-reader-visual-design.md.
**Constraints:** min26/target36; no dependencies/assets/fonts added; preserve viewport insets, source anchors, publisher fonts and PDF behavior.

### Task 1: Calibrate observed differences

- [x] Extend actual seek/return test with top-left position, close separation and unchanged viewport; add real glyph-width test for default serif and publisher override. Run baseline RED.
- [x] Change overlay positioning, subdued status color and native vector menu glyph; pass truthful source chapter label. If baseline proves missing serif fallback, set WebView.standardFontFamily only.
- [x] Targeted reader controls/font/anchor regressions GREEN; inspect screenshots against local original image.

### Task 2: Verify and deliver

- [x] One read-only review; material fixes get regression evidence.
- [x] Core/JVM/build/lint and appropriate Android16/Android8/tablet regression; visually inspect phone portrait, tablet landscape/rotated menu and return entry. Phone short-landscape behavior tested; its screenshot was overwritten by tablet capture and is not claimed as archived visual evidence.
- [x] Record exact-reference scope and remaining differences, bump0.6.6/code13, restore test devices and prepare delivery on the authorized branch. Commit/push and remote SHA/clean-state verification are the final session delivery commands.

### Verification and rulings

Baseline RED: corrected AVD density first, then confirmed wrong return corner and missing serif fallback. Targeted GREEN5/5. One review found insufficient status contrast; new test reproduced it, minimum-alpha calculation passed all twelve palettes. Dark system-bar screenshot exposed black icons; actual-window regression reproduced and verified flags/colors restore on leaving reading. Shared system-bar appearance also applies to PDF; its controls and data handling remain unchanged.

Frozen package verification: Core12/12, JVM1/1, build successful, JS syntax passed, lint0errors/20existing warnings. API36 phone53/53; API26 and API36 tablet related32/32 each. Exact timings, package hash, screenshots and remaining differences are in docs/reader-visual-0.6.6.md. No application source changes after that build.
