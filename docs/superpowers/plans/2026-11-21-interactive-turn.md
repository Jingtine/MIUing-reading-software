# Interactive page turn implementation plan

Spec: `docs/superpowers/specs/2026-11-21-interactive-turn-design.md`.
Execute inline on the existing authorized feature branch `feat/miuing-android`. No dependencies or font data changes; preserve personal build isolation. Existing permission to push this repository applies.

1. Add real WebView drag regression tests for held preview, cancel and accepted release. Run them against 0.6.8 and record expected failure before implementing.
2. Implement a focused JS recognizer and shared bridge/secure WebView helpers. Add ready notification and target calculation without mutating source during drag.
3. Implement the single passive preview, persistent host and two-frame overlay; wire TextReader and prove the new tests green. Preserve the old tap animation path.
4. Extend boundary, reversal, cancellation, lifecycle and rapid-input tests. Run JS syntax, core/JVM, lint and existing reader regression tests. Resolve defects from evidence and record material decisions in the ledger.
5. Fresh whole-change review; fix material findings with regression coverage. Bump to 0.6.9/code16, document actual verification, build both font variants and retain a verified personal APK. Commit/push authorized branch after checking staged files contain no personal fonts/APKs. Verify remote HEAD and working tree.

Each step is complete only with recorded commands/results, not implementation alone. Builder choices that preserve the spec may be settled inline. Emulator checks do not establish physical-device frame rates or battery performance.
