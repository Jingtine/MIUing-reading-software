# Per-hit search excerpts

The 50-character pre/match/post excerpt idea and whitespace normalization in `reader-text.js` are adapted from [Foliate-js search.js](https://github.com/johnfactotum/foliate-js/blob/78914aef4466eb960965702401634c2cb348e9b1/search.js), pinned to the same commit as the existing parsers. Copyright 2022 John Factotum, MIT; original license remains in `app/src/main/assets/licenses/foliate-js-LICENSE.txt` and ships with the APK.

The adaptation uses the existing MIUing live DOM matcher, Unicode codepoint-safe context, a bounded result list and a progressive lowercase-offset cursor. It does not import Foliate's Intl.Segmenter matcher, avoiding divergent discovery and navigation rules on Chrome69. Native scanning borrows the same passive preview used for pagination and never writes reading progress.
