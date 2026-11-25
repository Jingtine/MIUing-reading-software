# Independent EPUB compatibility corpus

Source: [W3C/IDPF EPUB 3 Samples](https://github.com/IDPF/epub3-samples), pinned to `7651e2002b631e6577fadf7e9e0692fa6efb8746`.

Use a local clone at that commit and sparse-checkout the nine directories listed in `build_corpus.py`, then:

```powershell
python tools/epub-compatibility/build_corpus.py --checkout .tools/epub3-samples --output .tools/epub-corpus
adb push .tools/epub-corpus /data/local/tmp/miuing-epub-corpus
adb shell am instrument -w -e epubCorpusDirectory /data/local/tmp/miuing-epub-corpus -e class com.miuing.reader.EpubCorpusTest com.miuing.reader.test/androidx.test.runner.AndroidJUnitRunner
```

The tool verifies the commit and each raw Git blob, bypassing checkout newline filters, then builds deterministic EPUB ZIPs and a manifest with hashes. It includes independent plain/obfuscated OTF and WOFF font counterparts. Instrumentation checks decoded font bytes against those plain references and checks actual WebView loading.

Samples and fonts remain under ignored local directories, outside source control and APKs. The upstream default sample license is CC-BY-SA 3.0 with exceptions described by sample metadata; embedded fonts have their own licenses. Retain original metadata and consult the [sample table](https://idpf.github.io/epub3-samples/30/samples.html) for attribution. This repository distributes only the build/test code, not sample content.

The tests intentionally use an explicit instrumentation argument. A run without the local corpus skips these tests; it does not prove corpus compatibility. Font/vector tests in `EpubCompatibilityTest` run without external files.

## Actual process termination

With both debug APKs installed on a dedicated test emulator, run this separately from other instrumentation:

```powershell
python tools/epub-compatibility/verify_process_restore.py --adb .tools/android-sdk/platform-tools/adb.exe --serial emulator-5554 --output .tools/epub-process-phone
```

The explicit test fixture imports a generated EPUB, saves a known passage/bookmark/note, and ends instrumentation. The host opens the reader, confirms the actual visible paragraph, backgrounds and terminates the process, verifies that PID is gone, reopens and verifies the same visible passage and records. It checks page/scroll fraction within 0.01 and removes only its own per-run token-owned fixture in a finally block, including failed or timed-out seeds. The seed also rolls back its own import if initialization fails. XML hierarchy/results are retained in the specified local output directory. An existing title is checked before entering cleanup and is never replaced. `--inject-seed-failure` deliberately fails after import to exercise cleanup (the command exits with a failure; cleanup must report success and leave no fixture record).
