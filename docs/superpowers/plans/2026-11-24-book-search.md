# Full-book search plan

User approved per-hit snippets and cross-chapter navigation, retaining source location. Continue economical inline execution with one review.

## Design

Reuse the renderer's existing DOM matcher and its one auxiliary ReadingPreview. Foliate-js search.js at pinned78914a is the reference for per-hit context excerpts; adapt only that small idea/algorithm, retaining its MIT attribution. Jsoup was checked but not added: a second HTML/CSS interpretation could disagree with actual visible text.

Expose bounded findAll through the shared text index. BookSearchEngine serially borrows the preview, using the same ReadingLayout/CSS/fonts and CSP. Pause background page counting while a query scans, never create a third WebView. Debounce300ms, query<=256 UTF16 chars, maximum1000 hits with an explicit truncation message, per-chapter20s timeout, cancellation cleans its preview. Suspend between chapters while backgrounded; lifecycle changes cancel/restart a pending search.

Results contain chapter, local ordinal and pre/match/post excerpts (50 context codepoints each, normalized whitespace). Navigation uses the existing live matcher ordinal, never snippet offsets. Results list shows every recorded hit, chapter label and highlighted match. Cross-chapter prev/next use the selected result snapshot. Closing search returns the original history entry; browsing results adds only one source entry. Typing never moves the reader. Recreate recomputes transient results for the saved active query and preserves the current anchor. PDF behavior unchanged.

## Steps

- [x] RED tests for per-hit DOM excerpts/Unicode/inline/hidden content/limits, actual results list and cross-chapter navigation/source return.
- [x] Refactor shared matcher, focused preview search worker, reader/search panel integration. Reuse existing preview lifecycle, no dependency/permission.
- [x] Android16/8 plus tablet checks, existing search/history/pagination regressions, core/JVM/lint; focused independent review.
- [x] 0.7.4/code25 compact standard/personal APKs, verify signature/fonts/scripts/licenses; document actual limits/results, commit/push authorized branch.
