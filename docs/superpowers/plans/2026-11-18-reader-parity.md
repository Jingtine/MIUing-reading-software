# MIUing reading parity continuation

> **For agentic workers:** Use superpowers:executing-plans inline. Follow test-driven-development and verification-before-completion.

**Goal:** Continue the approved Apple Books replication with nested EPUB navigation, stable text locations, exact selections and richer reading settings.
**Architecture:** Keep the tested JVM parser; expose flattened hierarchical navigation entries containing chapter, fragment and depth. Move the controlled renderer JavaScript to an application asset with a JSON configuration boundary. Store text offsets independently of screen pagination and retain backward compatibility with existing records.
**Tech Stack:** Existing Kotlin/Compose, Java, WebView and Android API26+ project.
**Spec:** docs/superpowers/specs/2026-11-18-miuing-design.md

## Global constraints
- Preserve existing EPUB/PDF/TXT files and reading records.
- No changes to confirmed offline scope; no book-authored scripts or network access.
- Existing user instruction delegates execution; do not reopen architecture approval.
- Current baseline d4148a9 was pushed to origin/feat/miuing-android before this work.

## Review focus
- EPUB 3 nav versus EPUB 2 NCX, nesting, escaped fragments and missing navigation.
- Font, theme, layout and window changes must not relocate the passage being read.
- Duplicate selected text and selections crossing paragraphs must retain exact location.
- Old book, bookmark and note records must load without an anchor field.
- Malformed navigation or unknown fragment must not make otherwise valid chapters unreadable.

### Task 1: Hierarchical navigation
Files: core Publication.java, PublicationParser.java, PublicationParserTest.java; new EpubNavigation.java if parsing warrants separation.
Interface: Publication.navigation: List<Navigation>; Navigation(title, chapter, fragment, depth). Keep existing constructor and chapters API.
- [x] Write and run failing real EPUB fixtures covering EPUB3 nested nav, EPUB2 NCX with safe external DOCTYPE, URI fragments, missing/bad nav fallback and unsafe destinations.
- [x] Implement bounded traversal and safe local resolution; nav preferred over NCX, chapters as fallback.
- [x] Run core suite and wire indented directory entries to exact chapter/fragment navigation.

### Task 2: Stable text anchors and selections
Files: LibraryStore.kt, ReaderScreen.kt, TextReader.kt, assets/reader.js, Android instrumentation tests.
Interfaces: integer UTF-16 text offsets, -1 for old fraction-only locations; bookmarks retain both. Note start/end default -1. Configuration is JSON and scripts are application-owned.
- [x] Add failing device tests for same-passage restoration after font change/recreation, exact repeated-text highlights and cross-paragraph highlights.
- [x] Extract renderer asset, add DOM text-offset capture/restore, chapter fragment jumps and selection ranges; preserve original markup structure when highlighting.
- [x] Save/load optional anchors with old-record compatibility; make note navigation and editing use exact anchors.
- [x] Run device tests and existing regression suite.

### Task 3: Reading settings and validation
Files: ReaderScreen.kt, renderer configuration, instrumentation tests, parity/verification docs.
- [x] Add persisted line spacing, margins, letter spacing and application-window brightness to the original-style reading settings panel.
- [x] Run core, phone/tablet instrumentation, assembleDebug and lint; inspect screenshots and fix failures.
- [x] Independent code review; address material findings, update parity evidence, commit verified changes.

## Ledger
Plan runs in the existing feature branch and supplied workspace. Task status and real commands/results will be recorded here, not inferred from old reports.

### Verified execution

- Baseline d4148a9 pushed to origin/feat/miuing-android before implementation.
- Core fixtures first failed for missing EPUB3/NCX navigation, then passed; additional disabled-heading fallback fixture failed before its fix. Final core suite: 12 passed, 0 failed.
- Device test first demonstrated missing anchors; a later real EPUB with leading style/script demonstrated incorrect element-selection offsets before its fix.
- Final `:core:check :app:assembleDebug :app:assembleDebugAndroidTest` succeeded. Final `:app:lintDebug` succeeded (nonblocking warnings remain).
- Final Android API35 phone suite: OK (6 tests), 38.225 seconds.
- Final Android API35 1600x1200 / 160dpi tablet suite: OK (6 tests), 37.104 seconds.
- Code review completed; material findings fixed and exercised by regressions: fragment consumption, cancelled selections, note-editor dismissal, fallback when all navigation entries are disabled, strict text-node boundaries and disposal guards.
- Ruling: use a concretely typed helper at addJavascriptInterface because lint resolves Compose remember() to generic T even with an explicit local type; runtime bridge methods remain annotated, and no lint rule is disabled.
- Existing worktree reused on feat/miuing-android. No reference app binaries or local SDK/build outputs are included in the commit.
- Original-app visual parity and remaining PDF/animation work are still open; this plan completes only the reading-parity continuation described above.
