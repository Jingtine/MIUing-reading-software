# Book pagination and Chinese typography implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans inline, test-first, with one fresh whole-change review.

**Goal:** Accurate whole-book page ranges/progress and optional per-theme Chinese paragraph layout without losing reading anchors.
**Architecture:** Shared application JS measures physical columns; one interruptible auxiliary WebView scans chapters using the same configuration. A pure page map and bounded IO cache feed footer/menu navigation. Native host keeps the two-WebView limit.
**Tech Stack:** Existing Kotlin/Compose/WebView/JavaScript/coroutines only.
**Spec:** docs/superpowers/specs/2026-11-21-pagination-typography-design.md

## Global Constraints

- Existing feat/miuing-android feature checkout, clean base680dddde556e36ffcce9891bb44ba0ee06dacfb3; continue authorized branch without another worktree or confirmation.
- Android compile/target36, min26. No dependencies, permissions, font bytes, publication files or location schema changes.
- At most main + one auxiliary WebView; at most two snapshots, each <=2Mpx.
- Preserve source text/anchors and book style restoration. Windows personal fonts/APKs stay ignored.
- New layout defaults inactive. Missing/invalid cache never invents full-book totals.

## Review Focus

- Mixed Han/Latin, inline highlights and protected code/captions preserve exact text and offsets: Task1 real renderer test.
- Odd final tablet column, empty/illustrated chapters and fractional viewport width: Task2 renderer cases.
- Old callbacks after font/viewport change and cancelled background counts: Task3 generation/interaction integration tests.
- Cache key collisions, malformed data, partial cache and upper bounds: Task3 cache tests plus Task2 map tests.
- Full-book slider finishing while counts become ready, backward chapter landing and source return: Task4 real UI tests.

### Task 1: Optional Chinese layout

Files: ReadingAppearance.kt, ThemeSettingsPanel.kt, ReaderScreen.kt, TextReader.kt, reader-typography.js, reader.js; ThemeOptionsStoreTest.kt, ChineseTypographyTest.kt.
Interface: ThemeOptions adds chineseLayout:Boolean=false, firstLineIndent:Float=2f, paragraphSpacing:Float=.5f; normalized ranges0..3 and0..1.5. JS class MIUingChineseTypography.restore()/apply(flow,config) owns only its temporary paragraph styles.
- [x] Add persistence/clamping/legacy-default and renderer style/text/anchor/restore tests; observe RED.
- [x] Implement settings, optional controls and renderer module, wire actual main/preview configuration; run GREEN and existing theme/anchor checks.

### Task 2: Physical page geometry and pure page map

Files: reader-pagination.js, reader.js, ReaderPanel.kt or new BookPageMap.kt; BookPageMapTest.kt, PaginationGeometryTest.kt.
Interface: JS MIUingPagination.measure(shell,flow,config), pages(shell,config), reset(); native ChapterPageRange(first,last,total), BookPageInfo and BookPageMap(counts,columns). Page map returns null while any count unknown; page target contains chapter/position. Keep old MIUing.pages viewport semantics.
- [x] Add deterministic map boundary/overflow/unknown and actual WebView single/double-column/odd-last/image cases; observe RED.
- [x] Implement measured range cache and outside-flow tail padding; verify exact text/anchor and forward/backward last-page behavior GREEN.

### Task 3: Cache and interruptible whole-book counting

Files: PaginationCache.kt, BookPagination.kt, ReadingPreview.kt, ReadingTurnHost.kt, ReadingWeb.kt, ReadingLayout.kt, TextReader.kt; PaginationCacheTest.kt, BookPaginationTest.kt.
Interface: cache read/write for book/layout key + IntArray partial counts; preview.measurePages(onMeasured,onFailure) uses count request IDs; host measurement API owns shared auxiliary and interruption callbacks; coordinator starts/updates/cancels immutable layout plans and emits map/current range, never progress saves.
- [x] Add cache persistence/corruption/key eviction and real three-chapter count/cache reuse/invalidation/drag priority/disposal tests; observe RED.
- [x] Implement locked IO cache, generation guard and bounded worker. Count fonts with exact source settings, keep at most2 WebViews, pause on background and interaction. Run GREEN plus old drag/font/initial layout tests.

### Task 4: Whole-book footer and progress navigation

Files: ReaderScreen.kt, TextReaderChrome.kt, ReadingMenu.kt; ReaderChromeTest.kt, BookPaginationTest.kt.
Interface: TextReader emits completed BookPageMap + current ChapterPageRange for current layout; ReaderScreen derives BookPageInfo, snapshots map when opening menu, maps global slider target through navigate(); footer falls back to explicit chapter labels while unknown/scrolling.
- [x] Add full-book range/percent, chapter boundary and menu no-navigation-until-release/source-return cases. Update old assertions to accept accurate new scope without weakening page/anchor checks; observe RED.
- [x] Wire footer/header and frozen menu scope; verify GLOBAL and fallback paths GREEN.

### Task 5: Final review, verification and delivery

- [x] Fresh read-only whole-change review per requesting-code-review; fix substantive findings with regression cases.
- [x] JS syntax + Node input suite, Core12/JVM/lint, full Android16 phone and focused tablet/API26 matrices. Preserve both font variants; check actual personal8 and standard6 assets and fonts.
- [x] Version0.6.10/code17; document actual results and limits. Commit/push authorized branch with no private fonts/APKs. Verify remote==HEAD and clean tree, restore/stop only owned emulators; deliver tested named personal APK.
