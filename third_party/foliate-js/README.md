# Pinned format parsers

Source: [johnfactotum/foliate-js](https://github.com/johnfactotum/foliate-js/tree/78914aef4466eb960965702401634c2cb348e9b1), commit `78914aef4466eb960965702401634c2cb348e9b1`.

- `mobi.js`, `fb2.js`: unmodified upstream parser modules, MIT, Copyright 2022 John Factotum.
- `fflate.js`: unmodified upstream `vendor/fflate.js`; Foliate pins fflate0.8.2. MIT, Copyright 2023 Arjun Barrett.

Full distribution notices: [Foliate-js](../../app/src/main/assets/licenses/foliate-js-LICENSE.txt), [fflate](../../app/src/main/assets/licenses/fflate-LICENSE.txt). These files are also packaged inside every APK.

Only these parsing modules are bundled. The Foliate renderer/demo, PDF.js, network catalogues, and reader UI are not included. MIUing's own import adapter lives in [tools/import-converter](../../tools/import-converter/). Bounded decompression patches are applied during the build; original upstream files remain unchanged. An upstream update must deliberately review/update these insertion points and rerun real format/WebView tests.
