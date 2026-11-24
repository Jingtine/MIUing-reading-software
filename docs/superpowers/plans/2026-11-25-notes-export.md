# Notes organization and export implementation plan

> Execute inline using superpowers:executing-plans; user requested economical implementation and authorized selective reuse after GitHub research.

**Goal:** Filter current-book highlights/notes, group by chapter, export the visible selection as UTF-8 TXT or Markdown.

**Architecture:** Keep existing Note/Book storage and source anchors. A pure Kotlin grouping/formatting unit feeds a focused Compose panel. An activity-scoped export session owns the immutable selection and SAF write across configuration changes. Reuse the tested staged document writer used by backup; no new dependency.

**Design:** Search excerpt, annotation and chapter label; All / Highlights (blank annotation) / Notes (nonblank annotation). Sort by chapter and known source offset, retain duplicates and stable order for legacy offsets. Export the exact selection captured when tapping export. Chapter headings use publication chapter titles, with chapter/page fallback. Do not invent timestamps or positions for legacy notes. Multiline text, Unicode and Markdown punctuation must survive. Empty results cannot export. Existing edit/delete/jump behavior stays available. Rotation retains a pending picker snapshot and active write; process restart reports an interrupted request instead of exporting another book. Cancellation/provider failure cleans incomplete output where supported.

**Alternatives:** Whole Readium adoption would replace working storage/navigation; a Markdown rendering dependency adds nothing to text-file output. Use existing Kotlin/Compose/SAF, with Readium's separated excerpt/annotation presentation and KOReader/Foliate's book/chapter export organization as references. No source copied from GPL/AGPL applications. Research URLs recorded in delivery notes.

**Constraints:** minSdk26, target/compile36; phone/tablet; system UI fonts; original notes and book files never changed by export; no network/storage permission or dependency added.

## Review focus

- Multiline Markdown punctuation and Unicode: literal content must remain readable and escaped.
- Duplicate and legacy notes: retain all entries; no fabricated source offset/date.
- Filtered export/picker cancellation: exact immutable selection, no stale output.
- Rotation/process restart: no wrong-book export or unhandled result.
- Provider write/close errors: cleanup and clear failure feedback.

## Tasks

- [x] RED then GREEN JVM tests for grouping, filter/search, stable sorting, fallback labels, TXT/Markdown literal text and filenames. Add NotesDocument.kt.
- [x] RED then GREEN native UI/session tests for filters/edit/jump integration and real SAF exports, cancellation and rotation. Extract DocumentExport from backup, add focused ReaderNotes.kt and NoteExportSession.kt, connect MainActivity/ReaderScreen.
- [x] Run core/JVM/lint, focused Android16/8 and tablet checks; one independent final review. Document results and limits. Package 0.7.5/code26 standard/personal APKs, retain original signing key. Commit/push existing authorized feature branch after checks.
