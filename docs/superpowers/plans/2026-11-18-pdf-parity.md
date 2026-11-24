# PDF reading parity continuation

> **For agentic workers:** Use superpowers:executing-plans inline, with test-driven-development and verification-before-completion.

**Goal:** Add offline PDF search, embedded outline, selectable text, highlights/notes, freehand markup and annotated-copy export to the approved Apple Books replication.
**Architecture:** Keep Android PdfRenderer for page images. Use Apache-licensed PdfBox-Android for page text/geometry, outlines and export. Persist selections as existing page-scoped notes; ink strokes are an optional backward-compatible book field. Export only to an explicitly chosen destination, never mutate the imported original.
**Tech Stack:** Existing Compose/JDK17/API26+ project; com.tom-roush:pdfbox-android:2.0.27.0.
**Spec:** docs/superpowers/specs/2026-11-18-miuing-design.md

## Global constraints
- Local/offline operation, preserve existing EPUB/TXT and book records.
- Keep page images rendered by the system and align overlays in normalized page coordinates.
- Do not promise OCR for scanned pages; distinguish no text from no search results.
- Existing feature branch and user-approved continuation; keep changes reviewable and push verified work to the same branch.

## Review focus
- Rotated/cropped pages: selection, search and exported marks must use the same coordinate mapping.
- Unsupported or malformed PDF content must show an error, not crash the reader.
- Background extraction/rendering must not reopen a closed document or block the main thread on disposal.
- Large documents: extract pages on demand, cancellable bounded search, avoid loading every page bitmap.
- No marks must alter the user's original file; exporting must produce a separate readable PDF.

### Task 1: PDF document service
Files: app PdfDocumentService.kt, PdfGeometry.kt, Android PdfDocumentServiceTest.kt, Gradle dependency.
Interfaces: PdfDocumentService(file,context) with pageCount, outline(), pageText(page), search(query), export(destination,notes,ink), close(). Page text includes mapped normalized glyph boxes and text offsets. Geometry mapping handles crop/rotation.
- [x] Write executable fixture tests for text/outline/search and exported highlight/ink round-trip, including original digest unchanged.
- [x] Verify missing behavior fails; implement resource initialization, serialized background access and bounded page cache.
- [x] Verify rotation/crop geometry with hand-derived coordinate cases.

### Task 2: PDF interaction and persistence
Files: PdfReader.kt, ReaderScreen.kt, LibraryStore.kt, PDF Android UI tests.
- [x] Add failing device tests for search result navigation, outline navigation and persisted PDF marks.
- [x] Add page-scoped search overlays, long-press/drag selection, highlight/note actions and ink mode with undo.
- [x] Save optional ink strokes compatibly and restore marks on reopen.
- [x] Add Android create-document export action, clear success/failure feedback, and embedded-outline/page navigation panels.

### Task 3: Verify and publish continuation
- [x] Core suite, Android phone/tablet runtime tests, assembleDebug and lint.
- [x] Inspect real screenshots and independent review; fix material findings.
- [x] Update README/parity/evidence and prepare verified work for publication to origin/feat/miuing-android; commit/push identity is verified from Git after publication.

## Ledger
Baseline: 2c02c54, clean working tree. PDFBox Android is Apache-2.0, documented by its maintainer at https://github.com/TomRoush/PdfBox-Android. APK size impact and any remaining PDF limitations will be reported after real build/test evidence.

2026-11-18 implementation ledger:
- Geometry identity stubs failed before rotation/crop mapping implementation; fixture tests established text/outline/export behavior and exposed appearance rectangle expansion, ligature and rotated geometry issues.
- Native UI tests exposed long-press toolbar jitter, delayed selection readiness and outline clipping; corrected interactions and deterministic readiness checks.
- Independent PDF review identified Activity-scoped export cancellation, stale search overlay and repeated page-tree walks. Added application-owned staged export, query reset and cached single-page extraction; follow-up review found no remaining material issue in export ownership.
- `:core:check :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` succeeded; core 12/12, geometry 1/1. `:app:lintDebug` succeeded.
- `adb -s <serial> shell am instrument -w com.miuing.reader.test/androidx.test.runner.AndroidJUnitRunner`: API35 phone 10/10 (59.120s), API35 tablet 10/10 (61.200s), API26 Google APIs 10/10 (63.645s). Final logs are local `.tools/pdf-final-api35.log`, `pdf-final-tablet.log`, `pdf-stable-api26.log`.
- Screenshots inspected on phone and tablet. Debug APK 26,950,064 bytes. Limitations and evidence recorded in docs/releases/pdf-parity-0.3.md; full original-app parity remains ongoing.
