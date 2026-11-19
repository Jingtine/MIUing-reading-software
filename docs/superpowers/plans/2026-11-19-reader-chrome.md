# Text reader controls implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline, test-driven-development, and verification-before-completion. Continue in the existing authorized feature checkout. A single fresh final review follows implementation.

**Goal:** Bring EPUB/TXT reading controls closer to the fixed iOS18 reading-page reference without disturbing text layout when controls appear or disappear.

**Architecture:** A stateless TextReaderChrome owns overlay controls; ReadingMenu owns the bounded menu and chapter navigation. ReaderScreen orchestrates existing reader capabilities and typed ReaderPanel routes. Actual chapter page metrics come from the existing WebView bridge. PDF keeps its existing controls. No new framework or generic state/event abstraction.

**Tech Stack:** Existing Kotlin, Compose, WebView, API26+, no new dependency or permission.

**Spec:** docs/superpowers/specs/2026-11-18-miuing-design.md, with latest user priority EPUB/TXT and maintainable code.

## Global constraints
- Preserve existing offline/CSP, note ranges, theme drafts, persistence and page-turn behavior.
- User has already requested implementation and pushes; no repeated design approval.
- Compare fixed Apple iOS18 guide image Art/S1001_bksBookPage_iPhone.png, downloaded to ignored .tools/apple-books-reading-ios18.png. It shows top-right close, quiet top-center chapter status, bottom-center position, bottom-right reading menu. Menu contents are described by the guide; expanded menu geometry is not verified.
- Display actual chapter page counts, explicitly labelled as chapter pages; do not fabricate whole-book pagination.
- Scope is text reading controls. PDF receives existing regression only. Orientation lock, sharing, jump-back history and exact expanded-menu visuals remain subsequent comparisons, not permanently removed features.

## Review focus
- Hiding/showing overlays and opening menus must not resize WebView or move the passage.
- Opened menu and theme settings must not stack or swallow Back; activity recreation restores the active menu.
- Chapter boundaries, bookmarks and direct chapter progress must remain functional and persist actual anchors.
- Small landscape and enlarged system text must keep actions reachable; wide menus stay bounded.
- Page counts must describe the actual layout, refresh after typography/orientation changes, and avoid stale bridge callbacks after WebView disposal.

## Task 1: Reading overlays and menu
Files: create TextReaderChrome.kt, ReadingMenu.kt, ReaderPanel.kt, ReaderChromeTest.kt; modify ReaderScreen.kt, TextReader.kt, reader.js and existing UI tests.

- [x] Add failing real imported-EPUB tests for top-right close/bottom-right menu, no old toolbar/slider on reading canvas, functional menu/theme/contents/search/bookmarks/navigation, recreation, and stable WebView size/anchor across control toggles.
- [x] Run targeted instrumentation. Expected: fail because 阅读菜单 is missing in 0.5.0.
- [x] Add typed panel routes and separate readable UI components. Overlay text controls on a stable full reader viewport, preserve PDF controls. Report current/total chapter pages through the existing bridge; percentages in scrolling mode. Reuse existing theme and sheet actions with exclusive menu transitions. Adapt shared UI test helpers to new entry points.
- [x] Run targeted then full phone/tablet/API26 regressions, core/JVM, build/lint/syntax. Expected: all pass; inspect actual screenshots and record warnings/remaining differences. Add meaningful regressions for any material review findings.
- [x] One independent read-only review; resolve material findings via RED→GREEN. Update 0.6.0 and reference/evidence docs, commit/push, verify SHA and clean status.

## Ledger
Baseline 51fed00, clean feature branch. Existing spec and user's repeated continue request authorize this work in place. Alternative of adding more inline buttons was rejected because it preserves the wrong reading hierarchy and increases ReaderScreen coupling. A large state framework or rewrite of PDF is unnecessary; scoped stateless UI components and typed destinations are sufficient. Pre-flight: chrome/menu consume the existing callbacks and new chapter metrics; typed routes replace all panel string comparisons together to prevent mixed navigation behavior. Previous theme work is complete and is not being reimplemented.

RED 2 missing menu → GREEN 5 including existing theme/parity. ReaderPanels extracted as stateless domain UI rather than moving a monolithic state object into another file. An early test queried its own stale LibraryStore snapshot; corrected to inspect persisted data through a fresh store. Full26 runs each found one existing slow-swipe issue (physical gestures 634/757ms): explicit 700ms slow swipe RED, displacement-first fix GREEN while stationary long press remains suppressed. One independent review found late-font metrics and contrast, plus missing UI seek/anchor/page assertions; corrected with device-font delayed request RED (201 actual vs336 reported) → GREEN, true UI seek/bookmark/reflow/landscape tests GREEN. Status text uses full theme ink (minimum calculated contrast5.24:1); bookmark accessibility exposes actual action; helper directly waits for menu trigger.

Final review rulings:
- Expanded original menu/iPad pixel equivalence cannot be judged without reference captures; report approximation and preserve that follow-up. Cost: remaining visual differences, not fabricated equivalence.
- Whole-book pagination, jump-back history, sharing and orientation lock remain explicit subsequent scope; do not add fake controls. Cost: these original capabilities are still absent in 0.6.
- PDF architecture is unchanged except extracted display components and shared bookmark action. Existing regression remains required. Cost: pre-existing PDF limitations remain documented.
- Device touch/TalkBack/large text verification belongs to executor: run emulator matrix and large-text landscape flow; report physical-device/TalkBack audible behavior as unverified. Cost: hardware or assistive-technology-specific behavior may differ.
- Font-ready refresh gates by configuration object identity, because navigation also changes the positional layout revision. This cancels stale configurations without losing page-count refresh merely because the user turned a page; it does not write reading progress.

Initial full31 GREEN on API35phone222.762s, API26phone213.457s, API35tablet225.827s. Large-font1.3 short landscape flow GREEN. Screenshot checks found an additional rendering issue: white margins after closing the menu were GREEN3/3, but margins behind the opened dialog were black even after VisualStateCallback. Added masked-paper assertion RED at(5,327)=#000000; single-variable software-layer experiment GREEN1 with visibly correct paper. Ruling: scope a native software-layer fallback to Chromium providers with major version<=69; newer renderers remain accelerated. Remove the experimental test flag and retain both pixel assertions. This replaces the earlier no-workaround ruling with measured evidence. No frame-time improvement is claimed, physical GPU/TalkBack remains unverified. Final APK SHA256 e37212a6a33900c794ce254498331ce71a1e1e0bbdc24a1533cea88e5d4edd9a, 27,172,308 bytes; core12/JVM1/build/lint(0 errors20warnings)/syntax GREEN. Re-running the affected API26 and unchanged modern API35 path, including all31 tests and new pixel assertions, before publication.

Final verification: API26 full31 GREEN198.812s, API35tablet full31 GREEN230.252s, both include raster assertions. Final screenshots inspected and saved; modern phone full31 was green before legacy-only fallback. Publication on the authorized feature branch.
