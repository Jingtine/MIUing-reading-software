# Reading Fonts Implementation Plan

> Execute inline using superpowers:executing-plans in the existing authorized feature checkout; no new worktree or repeated approval.

**Goal:** Add the four user-supplied fonts to offline EPUB/TXT body choices, keeping UI text on Android system fonts.
**Architecture:** ReadingFonts catalog owns IDs/labels/assets and native font-face configuration. ThemeOptions uses the catalog for validation; ThemeSettingsPanel displays its choices. TextReader serves assets over its existing local HTTPS origin; reader.js awaits chosen font before restoring anchors. KingHwa uses three Unicode sources to stay below legacy WebView30MiB without losing encoded characters; original source plus deterministic development script and coverage report retained.
**Tech Stack:** Existing Kotlin/Compose/WebViewAssetLoader, JS FontFaceSet; no dependencies.
**Spec:** docs/superpowers/specs/2026-11-21-reading-fonts-design.md

## Global constraints

min26/target36. Preserve book mode, per-theme preferences, publisher overrides, source anchors, PDF behavior and existing offline CSP. No Apple font names without actual files. Preserve full encoded-character coverage and original sources; no network fetch in the app.

## Review focus

Missing/undecodable font must not leave reading stuck; rapid config changes or navigation must supersede old async work; publisher styles must restore; large font choice list must scroll on phones; asset provenance must distinguish supplied files from original presets.

### Task 1: Fonts end to end

Files: new ReadingFonts.kt and assets/fonts; modify ReadingAppearance.kt, ThemeSettingsPanel.kt, TextReader.kt, reader.js; test ThemeOptionsStoreTest.kt and ThemePanelTest.kt.

- [x] Add RED persistence test using literal four IDs; add actual UI selection/load/anchor/recreation/publisher restoration test.
- [x] Observe failures before implementing.
- [x] Add catalog and original full files; wire UI/normalization/config and local asset handler. Add font-face CSS and await selected face before anchor restoration, guarded by existing layout revision.
- [x] Run GREEN targeted tests and inspect actual body screenshots.
- [x] Run Core/JVM/build/lint/JS checks, full API36 phone suite, related font/theme/reader regressions on API26/tablet. One final review, fix material findings with evidence.
- [x] Document actual scope/size/provenance, bump0.6.7/code14 and restore test emulators. Prepared commit/push on the authorized branch; remote SHA/clean-state confirmation are the final delivery commands.

## Delivery evidence

See docs/releases/reading-fonts-0.6.7.md for RED/GREEN evidence, one review finding, legacy30MiB adaptation, source preservation, final package hash and actual screenshots. Additional tablet source drift was reproduced with loaded font and offscreen target; owned-scroll coordinates fix preserves intended passage without treating manual scrolls as restoration. Final exact package: Core12/JVM1, build/JS/lint0errors20existing warnings, phone57/57, API26/tablet13/13 each. No application source edits after final freeze. All original font inputs preserved; KingHwa39,575 encoded characters and all per-character outlines/metrics validated across the runtime shard union.
