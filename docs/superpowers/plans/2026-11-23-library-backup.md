# Portable Library Backup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Export and safely merge a portable MIUing library using a mature GitHub ZIP component.

**Architecture:** Shared Book JSON codec; bounded archive codec independent of UI; LibraryStore owns atomic merge. MainActivity owns document pickers and operation lifetime, with a small standalone backup menu.

**Tech Stack:** Kotlin, Android SAF, Compose, coroutines, Zip4j 2.11.6.

**Spec:** `docs/superpowers/specs/2026-11-23-library-backup-design.md`

## Global Constraints

- Keep minSdk 26, target/compile 36, seven import formats and original reader.
- Apache-2.0 notice shipped in assets; pin dependency 2.11.6.
- Archive 8 GiB, expanded 16 GiB, manifest 16 MiB, books 10,000.
- No overwrite of existing records or files; no appearance/font backup.

## Review Focus

- Cancellation after partial destination write must clean temporary data and attempt destination deletion.
- Corrupt final payload must leave all earlier staged books unpublished.
- Existing orphan directory must never be replaced or removed by rollback.
- Converted books must remain readable after all original source directories disappear.
- Failed atomic index publication must restore in-memory collections/books and remove only newly moved directories.

### Task 1: Archive and atomic merge

**Files:** new `BookJson.kt`, `LibraryBackup.kt`, `LibraryBackupTest.kt`; modify `LibraryStore.kt`, Gradle dependencies and notices.

**Interfaces:** `LibraryBackup(store).export(OutputStream)` and `.restore(InputStream): RestoreResult`, suspending; store exposes internal snapshot/root and one atomic merge boundary. Tests use isolated LibraryStore roots.

- [x] Write roundtrip, merge and invalid archive tests; run and observe missing-feature failure.
- [x] Extract existing serialization unchanged into BookJson, integrate pinned Zip4j, bounded staged validation and rollback-safe merge.
- [x] Run tests for roundtrip, duplicate retention, tamper/traversal/schema/missing/extra entry rejection, limits, cancellation, orphan and index failure.

### Task 2: SAF and library UI

**Files:** new `LibraryBackupMenu.kt`; modify `MainActivity.kt`, `LibraryScreen.kt`; new `LibraryBackupUiTest.kt`.

**Interfaces:** Backup menu receives export/restore callbacks; existing library function receives an optional backup action. Operation progress blocks library changes and permits cancellation.

- [x] Write UI test for menu explanatory text and export/restore intents; observe failure.
- [x] Add CreateDocument/OpenDocument launchers, IO operations and cancellation-safe cleanup/results.
- [x] Run UI tests and existing library/import regressions on API 36 and API 26.

### Task 3: Delivery and review

**Files:** README, backup release documentation, standard/personal APK outputs (ignored).

- [x] Independent final code review; address actionable findings and rerun affected checks.
- [x] Core/JVM tests, Android lint and standard/personal APK builds; verify fonts/signers/notices and record exact evidence.
- [x] Commit and push only source/docs/license changes to the authorized branch; verify remote commit and clean workspace.
