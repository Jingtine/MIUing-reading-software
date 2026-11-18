# Theme panel visual parity plan

> **For agentic workers:** Use superpowers:executing-plans inline with test-driven-development and verification-before-completion. User confirmed original-page comparison as the next work; continue in the existing approved checkout and publish verified progress to the same branch.

**Goal:** Replace the flat reading-settings list with the original's compact Themes & Settings hierarchy and a separate, functional Customize screen.
**Architecture:** ReadingAppearance.kt owns palette, serializable per-theme typography and backward-compatible SharedPreferences storage. ThemeSettingsPanel.kt owns an adaptive bottom panel (phone) / bottom-right panel (tablet), nested style/appearance/font pickers and a saved draft for customization. ReaderScreen retains reading/navigation and applies only committed typography; TextReader maps real Android/book font choices and spacing into its existing app-owned CSS.
**Tech Stack:** Existing Compose/Kotlin/WebView/API26+, no new runtime dependency.
**Spec:** docs/superpowers/specs/2026-11-18-miuing-design.md.

## Reference and scope
- Local Books 7.6 targets macOS 15.8; labels come from its Localizable.loctable. Mac package is not a runnable phone reference.
- Fixed official iOS18 guide: https://support.apple.com/en-gb/guide/iphone/iphc1af7c57/18.0/ios/18.0 . Screenshot Art/S1002_bksThemesSettings_iPhone.png was downloaded to .tools/apple-books-themes-ios18.png and inspected.
- Image shows header/close, small/large A, page style and appearance buttons, brightness, six cards in two rows (Original/Quiet/Paper; Bold/Calm/Focus), Customize footer. Main panel must no longer expose every spacing slider.
- Card colors are estimates sampled from that public screenshot, not exact native design tokens. System/book fonts are real Android substitutes; Apple proprietary faces and exact preset typography remain unverified.
- Preserve reading location, notes, offline/CSP boundaries and existing PDF tools. Do not claim full visual equivalence or add unrelated reader features.

## Review focus
- Migrate old global font/spacing into the previously selected theme once, without losing settings.
- Draft changes persist through activity recreation, commit with Done, and require discard only when leaving dirty customization; font picker back keeps the draft.
- Reset affects only the selected theme and returns to its real defaults; changing themes restores each saved customization.
- Every visible control must work; small phone landscape/large text must scroll without hiding Done/close; tablet panel stays bounded.
- Appearance/font/spacing changes keep stable text anchors, exact highlights and existing PDF navigation.

### Task 1: Model and migration
- [x] Add failing device tests for old-settings migration, per-theme persistence, reset isolation and invalid-value normalization.
- [x] Add ThemeOptions, defaults and ThemeOptionsStore; verify tests pass.

### Task 2: Panel hierarchy and rendering
- [x] Add a failing UI test: compact main panel hides detailed sliders, Customize/font/layout flow commits or discards, separate theme state restores, and reopen/recreation preserves state and passage.
- [x] Add adaptive panel, real font/bold/layout controls, theme reset and appearance/style pickers. Use a draft; keep main controls immediate.
- [x] Integrate new profiles and CSS without reintroducing book scripts or changing file formats. Update existing UI tests to navigate the new style picker.

### Task 3: Visual comparison and publication
- [x] Capture real phone/tablet main and customization panels; compare arrangement, six-card order, typography, spacing and close action with the fixed screenshot. Record remaining differences explicitly.
- [x] Core/JVM/full device regression on phone/tablet/API26, build/lint/syntax, independent final review and material fixes.
- [x] Update version 0.5.0, evidence/README/parity; commit/push and verify remote identity.

## Ledger
Baseline d1bd7a7, clean working tree. User confirmed visual comparison before more feature accumulation. Chosen scope is the reading appearance panel; the reader's outer toolbar/menu and library pages remain subsequent comparisons. Native font availability and exact Apple theme parameters will not be fabricated.

User steering: EPUB/TXT text reading is the priority; PDF receives regression coverage only. Migration tests RED 3 → GREEN 3. UI RED missing Customize, then RED passage drift → GREEN with retained logical anchor. One independent review found publisher styles defeating selected fonts, a second initial layout replacing the saved destination, and shared route scroll offsets. Added real styled-EPUB and WebView regressions: RED 4 → GREEN 4. Full runs exposed re-entered customization retaining an old scroll offset on a short phone, now reset on new entry; font-picker back still preserves the draft's scroll. Menu tests now close explicitly after selection instead of racing popup dismissal with Back. Final build 2026-11-19: core 12, JVM 1, APK/test APK, lint 0 errors / 20 warnings. Final device results follow in docs/theme-panel-0.5.md.
