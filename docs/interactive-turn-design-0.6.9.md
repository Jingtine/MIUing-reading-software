# Interactive text page turn

Approved scope: make EPUB/TXT curl follow a horizontal drag, then complete or rebound. Preserve existing tap, fade, continuous scroll, selection, links, location restoration, system motion preference and both font build variants. Whole-book pagination is a later task.

## Invariants

- The live source DOM, chapter and persisted reading location stay unchanged during preview. Only an accepted release advances once. Cancellation, selection, vertical movement, a second finger, configuration changes and disposal never advance.
- Use one persistent native host, one live reader WebView, at most one passive preview WebView, and at most two capped snapshots (each <=2 million pixels). No per-move bitmap allocation. No network, book scripts, new dependencies, or font changes.
- The passive WebView shares renderer, local resource loader, CSP, font/theme/notes and viewport with the live reader. It has no input, accessibility or progress-saving callbacks. Cache only one chapter/configuration; destroy on configuration, viewport or book change.
- The host keeps source and target images through a cross-chapter handoff; remove them after the new live renderer is ready and visually painted. Main WebView ownership stays in TextReader; host owns only preview and overlay.

## Input and visual state

A separate application-owned JS recognizer owns touch state. Lock a single horizontal direction after 12 CSS pixels, before a stationary long press. Progress is signed displacement / viewport width, clamped to [0,1]; reversing toward the start reduces it. A release commits at 28%, or at >=8% with recent forward velocity >=0.6 CSS px/ms. Velocity uses a bounded recent sample window; pausing does not retain fling velocity. A canceled/multitouch stream rebounds. Links, text selection and vertical scroll retain their normal behavior.

The renderer computes the adjacent page or adjacent chapter's first/last page. Book edges consume the drag without allocating a preview. Native receives a unique gesture ID and target; progress events never mutate source layout. JS retains the pending decision until native settling completes. Completion validates the ID and layout revision before advancing. A new independent input may finish a released within-chapter decision once; held gestures cancel. Cross-chapter completion blocks repeated input until replacement. Synthetic tail clicks stay suppressed; keyboard/accessibility clicks remain accepted. Fade, disabled motion and legacy synthetic start/end gestures retain the existing end-triggered path.

The host captures source and prepares target asynchronously. Until both are available, source stays visible and the latest finger fraction is retained. Release may wait for preparation for at most three seconds; failed/slow preview falls back to the requested real turn without an effect, only after committed release. A canceled release clears immediately if preparation is incomplete. Ready frames animate from the current fraction to zero or one. On completion, within-chapter publication waits for a visual-state callback before releasing the images. Cross-chapter publication retains the target image until the expected document is ready/painted, with a five-second cleanup deadline. All asynchronous work is guarded by gesture identity and disposal state.

## Structure

- `reader-gestures.js`: input ownership, direction/thresholds, IDs, completion and click suppression.
- `reader.js`: target calculation, existing source navigation, layout-ready notification.
- `ReadingWeb.kt`: concrete bridge and shared secure WebView setup/loading.
- `ReadingPreview.kt`: one passive preview renderer, asynchronous markup read, seek and visual readiness.
- `ReadingTurnHost.kt`: gesture lifecycle, preparation, cancellation, handoff and ownership.
- `PageTurnOverlay.kt`: capped source/target snapshots, finger fraction and settling; retain old tap entry point.
- `TextReader.kt`: Compose configuration and wiring.

## Acceptance

Real Android/WebView tests must observe a visible moving fold before release, unchanged live and stored position while held, commit exactly once, rebound with no location change, reverse/cancel behavior, chapter-boundary handoff and cancellation, resource disposal and existing tap/motion/selection behavior. Run existing core/JVM checks, lint and reader regressions on API36 phone plus focused API26/tablet checks. Keep personal font files/APKs ignored. This remains an approximation of Apple Books' paper geometry; do not claim identical physics or measured real-device performance.
