# Portable library backup

The user requested reuse of suitable GitHub technology to improve reader maturity. Implement a working local backup/restore feature using Zip4j 2.11.6 (Apache-2.0), verified against its upstream release and source. Keep the existing Android/Compose reader and seven-format import pipeline.

## Approaches and decision

- Android automatic backup cannot provide an explicit portable book archive and has provider quotas; it remains disabled.
- A database migration would not itself back up book files and would add unrelated scope.
- **Selected by the implementer:** Zip4j streams and validated central-directory reads, combined with Android's existing document picker. This supports portable ZIP/Zip64 without loading the archive into memory.

## Behavior

Add a small “书库备份” menu beside import, with export and restore actions. Export includes original books, converted reading copies, available covers, progress, bookmarks, notes, PDF ink and collections. Explain that reading appearance settings and fonts are not included. No cloud account or storage permission.

Restoration merges missing books only. An existing SHA-256 book ID keeps its current metadata and reading records. Collection names are unioned. Report restored/skipped counts. Never overwrite existing directories, including orphaned ones; report a repair-needed error instead. No destructive restore option.

## Format and boundaries

ZIP contains versioned `manifest.json`, portable Book records and a file inventory (path, size, SHA-256). Book JSON codec is shared with LibraryStore, not duplicated. Use generated paths only: `books/<64 lowercase hex ID>/source.<supported extension>`, optional `normalized.epub` for converted formats, and optional `cover`.

Validate every entry, reject duplicates/unlisted/missing entries, encrypted or split archives, unsupported schema and unsafe paths. Verify source hashes against book IDs and all payload hashes. Limits: archive 8 GiB, expanded payload 16 GiB, manifest 16 MiB, central directory 16 MiB (name 256 bytes, extra/comment 4096 bytes each), 10,000 books, source 1 GiB (converted source 64 MiB), normalized copy 256 MiB, cover 16 MiB. Stream copies check coroutine cancellation and limits. ZIP contents are never extracted using arbitrary archive paths.

## Consistency and integration

Capture immutable library state on Main; do stream work on IO. Block interaction during export/restore with a cancellable progress dialog. Rotation/cancellation removes staging. SAF export builds a complete local archive before writing the destination; on output failure attempt to remove the incomplete destination. Restoration stages and validates the entire archive before any store mutation. Move missing book directories into the library, then atomically publish a single merged index; rollback moved directories and in-memory state on failure. Process death before publishing may leave orphan directories, but must not change existing books or index. Export does not include caches or stale absolute paths. Restored covers are portable and reading caches regenerate on open.

## Verification

Instrumented tests cover all Book fields, converted copies, source independence, non-destructive merge, collection union, corrupt payload, traversal, schema mismatch, missing/extra entries, limits, cancellation and failed index publication. UI smoke checks menu, explanation and SAF actions on Android 16, plus Android 8 compatibility. Existing library/import regression tests and JVM/core tests must pass. Record actual checks, source/license and remaining limits in release documentation.
