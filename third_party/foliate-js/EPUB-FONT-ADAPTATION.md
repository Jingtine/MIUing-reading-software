# EPUB font-resource adaptation

`core/src/main/java/com/miuing/core/EpubFontObfuscation.java` adapts the IDPF and Adobe font-obfuscation algorithms and identifier handling from [Foliate-js epub.js](https://github.com/johnfactotum/foliate-js/blob/78914aef4466eb960965702401634c2cb348e9b1/epub.js#L554-L597), lines 554–597, commit `78914aef4466eb960965702401634c2cb348e9b1`, blob `e8bd70333accb9f9f6fb7389cb434b6805156da0`.

Copyright 2022 John Factotum. MIT license, retained in `app/src/main/assets/licenses/foliate-js-LICENSE.txt` and shipped in the APK alongside the existing Foliate parsers.

The Java adaptation uses the platform SHA-1 implementation and writes only the first 1040/1024 bytes of extracted font copies. It adds strict local manifest/path checks, explicit rejection of unsupported encryption, case-insensitive Adobe UUID recognition, duplicate/conflict handling and validation before resource changes. It does not port Foliate's renderer, loader or application UI. The original EPUB is never modified.
