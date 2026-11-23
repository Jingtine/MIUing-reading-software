# EPUB resource compatibility and reading regression

User direction: prioritize reflowable text, Apple Books-like behavior, Android phones/tablets, maintainable code, and examine GitHub implementations before building. Continue autonomously under the established execution preference.

## GitHub research and approach

- Readium Kotlin Toolkit provides a complete navigator but adopting it would change navigation, positions, annotations and UI integration. Defer that separate migration.
- Foliate-js is already pinned in this project. Reuse its `epub.js` font-obfuscation algorithms at commit `78914aef4466eb960965702401634c2cb348e9b1`, MIT, blob `e8bd70333accb9f9f6fb7389cb434b6805156da0`. Port the small resource transform into the existing Java extraction boundary; preserve attribution and the existing shipped MIT license. No new renderer or runtime dependency.
- Use W3C-maintained IDPF/epub3-samples at commit `7651e2002b631e6577fadf7e9e0692fa6efb8746` as independent real-book test inputs, including plain/obfuscated OTF and WOFF pairs, Moby Dick, internal links, children's literature and EPUB specification. Keep sample bytes/fonts under ignored `.tools`, not in source or APK. Record source and licenses.

## Scope and behavior

Add support for the IDPF and Adobe standard font-obfuscation schemes. IDPF uses SHA-1 of the package's designated identifier with only XML whitespace removed, XOR first 1040 bytes. Adobe uses a valid publication UUID, XOR first 1024 bytes. Preserve the suffix, original EPUB and embedded CSS. Decode only declared local font resources in the extraction directory. Font loads must succeed with the reader's existing `book` typeface; user-selected body fonts and all existing positions/notes remain compatible.

Reject unsupported encryption, invalid/missing keys, undeclared or missing targets, traversal/absolute/query/fragment paths and conflicting duplicate declarations with a clear import error. Duplicate identical font declarations must not XOR twice. Treat only the standard font transformation as supported; never add DRM decryption. Reopening always transforms freshly extracted bytes, preserving idempotent results.

Keep Java processing bounded to the prefix (1040 bytes max per font), reuse existing XML and resource containment helpers, and validate all declarations before modifying resource files. Preserve minSdk26/target36 and existing seven formats, limits, no network permission and CSP.

## Validation and limits

Core RED→GREEN tests verify independently generated known-byte vectors, identifier choice/whitespace, UUID case, prefix/suffix boundaries, repeated opens, duplicate/conflicting entries and rejected metadata/paths. Compare decoded real OTF/WOFF bytes with upstream plain counterparts. Actual Android WebView must load the font (not just claim a CSS family), then maintain the same passage after font/viewport changes and Activity recreation, with notes and bookmarks intact.

Run the corpus through import/TOC/rendering and report actual per-book results on Android36 and Android26. Exercise long-chapter navigation and existing text-index/performance tests; record measured scope without claiming emulator results establish battery life or real-device performance. Wide-window checks cover tablet behavior. Fixed-layout, RTL/vertical pagination and advanced MathML remain separate work unless a newly reproduced defect in this scope requires a bounded fix.

No manual design gate is required for routine choices under the user's ongoing autonomous execution direction. Independent builder and final code reviews remain part of this round; any material change to scope is stated explicitly.
