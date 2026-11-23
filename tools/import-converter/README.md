# Offline format importer build

This builds the import-only script used for MOBI7, KF8/AZW/AZW3 and FB2. The app stores the original file and normalizes parsed sections/resources into its private EPUB representation, then uses the existing reader.

```powershell
npm ci --prefix tools/import-converter --ignore-scripts --no-audit --no-fund
node tools/import-converter/build.cjs
```

esbuild0.25.12 is a development dependency only. Gradle uses the checked-in `app/src/main/assets/book-import.js`; building an APK does not install Node packages or download parser code. The target is Chromium69; `compat.js` supplies missing browser APIs. Keep third-party source line endings as LF so exact bounded-decompression patches remain reproducible.

`bounds.cjs` bounds PalmDOC/HUFF record decompression, recursive dictionary expansion, structure table counts and total text concatenation. `index.js` checks actual containers, encoding, DRM and Print Replica; `publication.js` preserves local navigation/notes and removes active content; `resources.js` localizes embedded files and streams bounded chunks to the native writer. The converter never mounts book HTML into its running document.

When changing the parser or adapter, rebuild and run MultiformatImportTest and MultiformatReaderTest on Android16 and Android8's original WebView. ExternalMobiCorpusTest additionally accepts `-e mobiCorpusDirectory /data/local/tmp/miuing-mobi-corpus`; source fixtures are kept out of Git. See the release record for pinned fixture URLs and verification scope.
