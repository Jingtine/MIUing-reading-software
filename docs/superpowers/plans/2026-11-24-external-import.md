# System file intake — implementation plan

User approved the 0.7.3 scope and requested economical execution. Reuse the existing reader, importer and AndroidX components. Reference: Readium test-app manifest separates VIEW and SEND filters; no upstream code or additional library is required.

## Design

- One ViewModel owns LibraryStore and a serial import queue, surviving Activity recreation. Single external book opens after import; multiple books return to library with a summary. Duplicate hashes retain progress/notes. Use keyed reader composition when switching books.
- Support content URIs from VIEW, SEND, SEND_MULTIPLE and ClipData. Keep VIEW and SEND manifest filters separate, content-only VIEW, supported MIME types plus common generic binary. Do not register text/* or */*. Reject network/file URLs and malformed/oversized batches (64 max).
- Resolve formats using bounded source header inspection, filename and provider MIME, then existing full parsers. Preserve AZW/AZW3 provenance. Copy into private storage using existing bounds; check cancellation while copying. No external permission needed after successful copy.
- Imports serialize with backup operations. Rotation must not replay the initial intent or lose the active import. SavedStateHandle records an interrupted-operation hint for process recreation; do not automatically reopen expired URI grants. Show errors/summary outside the worker so notifications cannot block the queue.
- Existing library actions and reader behavior remain unchanged. No new runtime dependency, min26/target36.

## Steps

- [x] Tests first: intent parsing/content-only scope, missing/wrong filename detection, cold/warm launches, multiple/duplicate inputs, recreation and failed permission; observe RED.
- [x] Add focused IncomingBooks/ImportSource helpers and LibrarySession; wire onCreate/onNewIntent, keyed reader and progress/error UI. Keep Activity thin.
- [x] Android36/26 intake tests and existing import/library/backup regression; core/JVM/lint. Review all changed product code once, fix verified findings.
- [x] Version0.7.3/code24, compact standard/personal builds; verify signer/fonts/notices. Document scope and test evidence, push authorized branch and verify remote.

## Acceptance

External single files open, multiple files summarize, repeated delivery deduplicates, original provider files can disappear after copy, failed input publishes no phantom book. Activity recreation does not repeat a request or cancel its queued work. Existing notes/progress survive switching and duplicate imports.
