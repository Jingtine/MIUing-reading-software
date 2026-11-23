# EPUB Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Reuse Foliate's standard font-resource support and validate real text books and reading-location stability.

**Architecture:** Small Java resource transform after extraction/manifest parsing; existing renderer and record schema remain intact. Independent pinned sample corpus is test-only. Focused native tests prove font load and location behavior.

**Tech Stack:** Java17 core, Android/Kotlin WebView, existing Foliate MIT reference, Android instrumentation.

**Spec:** `docs/superpowers/specs/2026-11-23-epub-compatibility-design.md`

## Global Constraints

min26/target36; no new renderer/dependency/permission. IDPF prefix1040, Adobe1024; original files unchanged; prefix memory <=1040 bytes. Test samples/font bytes remain ignored. Preserve seven import formats and existing text offsets.

## Review Focus

- Multiple identifiers and XML whitespace must select the designated IDPF key, including non-ASCII text.
- Duplicate declarations must not XOR a resource twice; conflicting algorithms must fail before modifications.
- Encoded traversal/absolute/fragment paths and non-font encryption must be rejected.
- Reopened extracted fonts must equal known original bytes, including their entire suffix.
- Real WebView font loading and subsequent layout changes must restore passage/records, not silently use fallback.

### Task 1: Standard font resources

**Files:** create `core/.../EpubFontObfuscation.java`, `core/src/test/.../EpubFontObfuscationTest.java`; modify PublicationParser, core test entrypoint and third-party attribution.

**Interfaces:** package-private `EpubFontObfuscation.decode(Path root, Document packageDocument, Map<Path,String> manifestTypes)` called after resource manifest resolution. `EpubFontObfuscationTest.run(Path root)` registers tests with existing test runner.

- [x] Write known-byte and rejection tests, run `:core:check` and observe missing behavior failures.
- [x] Adapt pinned Foliate algorithms, enforce declared font/path/key boundaries, preserve original archive; run core tests GREEN.

### Task 2: Real books and reading stability

**Files:** new corpus manifest/build utility under `tools/epub-compatibility/`; new native `EpubCompatibilityTest`/`EpubCorpusTest`; reuse BookReaderHarness and existing engine/pagination/location tests.

**Interfaces:** optional `-e epubCorpusDirectory /data/local/tmp/miuing-epub-corpus`; committed tool creates pinned EPUBs and verifies Git blob IDs; no sample content checked in.

- [x] Fetch fixed public samples locally, build EPUBs, independently compare OTF/WOFF decoded font bytes with upstream plain files.
- [x] Observe actual embedded-font loading failure before production integration on Android36, then verify GREEN and repeat on Android26.
- [x] Validate original-publisher font, selected font/reflow, bookmark/note/anchor restoration, real corpus import and TOC, long chapter boundaries and existing text-index behavior. Check wide tablet window; record timings/limitations.

### Task 3: Review and delivery

**Files:** README, compatibility release record/parity, version/build config and actual screenshots.

- [x] Independent final review and affected regressions; core/JVM/lint and standard/personal compact builds.
- [x] Verify original signer, 6/8 fonts, exact scripts and licenses; document actual corpus/Android results and limits.
- [x] Commit/push authorized branch, verify remote and clean only owned test artifacts/AVDs.
