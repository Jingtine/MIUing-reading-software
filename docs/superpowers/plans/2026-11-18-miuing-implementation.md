# MIUing Android Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Build an Android offline reader following Apple Books' library and reading organization, with MIUing branding.

**Architecture:** A JVM core parses imported text and EPUB independently of Android. A Compose application owns local storage and adaptive library UI; WebView and PdfRenderer render text/EPUB and PDF respectively.

**Tech Stack:** JDK 17, Kotlin, Jetpack Compose, Gradle, Android SDK.

**Spec:** docs/superpowers/specs/2026-11-18-miuing-design.md

## Global Constraints

- Android phones and tablets; MIUing name and supplied icon.
- Offline EPUB, PDF, TXT; MOBI/AZW3 later.
- Reference Apple Books 7.6 resources; do not claim visual equivalence without device comparison.
- Preserve Apple reference package, exclude it from application distribution.

## Review Focus

- Malicious or oversized EPUB entries: bounded extraction and path containment.
- Partial multi-file import: keep successful books, surface per-file failures.
- Rotation/process recreation: persisted book and reading positions.
- Missing images/metadata and non-UTF8 text: usable fallback and readable content.
- Removed original file: imported copy must remain readable.

### Task 1: Offline document core
Files: core/src/main/java/com/miuing/core/{Publication,PublicationParser}.java; core/src/test/java/com/miuing/core/PublicationParserTest.java; scripts/test-core.ps1.
Interface: PublicationParser.open(Path source, Path extractionDirectory) -> Publication, with title, author, format, chapters and cover; chapter holds title and local content path.
- [ ] Add executable JVM tests using real EPUB ZIP fixtures: metadata/spine order, traversal rejection, UTF-8 BOM and GB18030 TXT, malformed files and expansion bounds.
- [ ] Run tests and verify failures for missing parsing behavior.
- [ ] Implement secure XML parsing, bounded EPUB extraction, ordered spine and TXT decoding.
- [ ] Run the whole core test suite with JDK 17.

### Task 2: Android library and import
Files: settings.gradle.kts, build.gradle.kts, app/build.gradle.kts; app/src/main/java/com/miuing/reader/{MainActivity,LibraryStore,LibraryScreen}.kt; app/src/main/AndroidManifest.xml and res/.
Interface: LibraryStore exposes books, import(uri), save(book), delete(book) and collections. Book has stable ID and source path, current chapter, position, bookmarks and collection IDs.
- [ ] Configure reproducible Gradle dependencies and Android manifest; copy supplied icon.
- [ ] Import through system document picker into private storage; validate before indexing; hash duplicate files; write metadata atomically.
- [ ] Build Home, Library, Collections and Search with Apple-like typography, cover grid/list, sorting, context actions and adaptive navigation.
- [ ] Verify private copies and collection/delete behavior with Android unit/instrumentation tests where SDK permits.

### Task 3: Reading
Files: app/src/main/java/com/miuing/reader/{ReaderScreen,TextReader,PdfReader}.kt and app/src/main/assets/reader.js.
Interface: ReaderScreen(book, store, onClose) opens persisted positions and saves changes; renderer events update chapter and progress.
- [ ] Render EPUB/TXT local content with safe local resource loading; disable remote requests and book-authored scripts.
- [ ] Implement paged/scroll reading, chapter navigation, table of contents, persistent bookmarks, text search and theme/font settings.
- [ ] Render PDF on demand with page navigation and zoom.
- [ ] Add selection highlights/notes and book-level collections without dummy menu actions.
- [ ] Verify reader lifecycle and long-document behavior; document parity gaps explicitly.

### Task 4: Verification and delivery
Files: README.md; docs/reference/parity.md; local Gradle wrapper.
- [ ] Run core tests, Android unit tests, lint and assembleDebug; inspect all failures.
- [ ] Review complete diff, fix material defects, and inspect on available phone/tablet emulator or device.
- [ ] Document exact build instructions, resulting APK path and unverified behavior.
- [ ] Connect provided remote locally; do not push reference binaries or unreviewed artifacts.

## Execution record

User approved original-app direction with “ok”. Execute inline; routine architecture decisions are delegated. No existing Git repository exists, so work in the supplied project directory, with a feature branch when Git initialization is available. Build tools are downloaded only into ignored `.tools/`. The system Java shim hangs; use D:/Software/JDK-17 explicitly. Network access requires sandbox escalation.
