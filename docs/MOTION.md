# Motion map

The native `MotionPolicy` is authoritative. It combines the preference, foreground state, interactive display, Battery Saver and animator scale. `AlpineScreen.setMotionAllowed` updates the CSS gate. `onPause` also calls WebView `pauseTimers`; `onResume` resumes them. Native dialogs get `QuietSheetMotion` only when motion is allowed. The master switch freezes immediately; it adds no fade after motion has been disabled.

## Scene variables

- `--p`: quantitative hold progress, from 0 to 1. JavaScript cannot grant an unlock.
- `--scenic-p`: decorative night-to-dawn progress; frozen while motion is off.
- `--pause-p`: growth of the ten-minute pause sprig.
- `--delay`: per-item entrance stagger.
- Palette variables `--zen`, `--mid`, `--haze`, `--sun`, `--rock`, `--snow`, `--mist`, `--trees`, `--deep` define static dusk/night/dawn colours. Colour itself is not animated: the complete dawn SVG fades as one layer.

## Layers and timings

| Screen / layer | Motion | Duration |
|---|---|---|
| `#breeze` | Grouped mist, smoke and foreground pine; translate/rotate | 42 s ambient |
| `#sparks` | Three stars and two fireflies share one opacity layer | 28 s ambient |
| `#bird` | One bird crossing on idle Home | 30 s cycle |
| Home cards | Fade + 12 px rise, first entrance only | 300 ms; 60 ms stagger |
| Start halo | One opacity/scale glow | 350 ms |
| Native sheets | XML translate + opacity; dim behind sheet | 300 ms in; 250 ms out |
| Duration chips / number | Scale 1.04; fade text, no spinner | 250 ms |
| App picker | Selected moss fill; check reveal; no list entrance/scroll effects | 150 ms check |
| Setup lantern | Two-frame transform loop; guard/access light opacity | 20 s loop; 300 ms light |
| Setup failure | Dim lantern; native 4 px cycle shake | 300 ms |
| Hold halo | Breathing scale/opacity, only while holding | 8 s |
| Two ring arcs | Clipped semicircles reveal via transforms | 300 ms progress; 400 ms reset |
| `#dawn` | Cross-faded SVG, warmer cabin and thicker smoke | 300 ms progress; 1.5 s reset |
| Original moon | Sinks using transform | 300 ms progress; 1.5 s reset |
| `#growingTree` | Seedling scales to tree; blossoms at completion | 300 ms progress; 1.5 s reset |
| Blocked message | Fog opacity, then automatic dismissal | 500 ms in/out; clears at 2.5 s |
| Completion | Sunrise wash and “Unlocked” entrance; birds | 350 ms; 20 s bird pass |
| Exit | No status screen or ambient animation; silently closes | None |
| Pause sprig | Transform updated once a second, still at completion | 350 ms transitions |
| History | Seven trees grow once per open; latest session tick | 300 ms, 40 ms stagger; 150 ms tick |
| Jar tap | Single scale/opacity response | 300 ms |
| Notification | Static icon and calm text | None |

During a hold there are at most eight animated elements/layers: breeze group, sparks group, two ring arcs, dawn layer, moon, tree, halo. Multiple small details intentionally share ambient layers. No particle emitter or JavaScript animation-frame loop is used. Scenery behind native sheets is frozen, and only the small settings preview can animate. History backgrounds are still while its seven trees enter. Starting a hold dismisses any interception fog so it does not add a ninth moving layer.

The native touch gate checks live session state, focus, visibility, the display, obscured-event flags, pointer count, current coordinates and historical coordinates. Its elapsed-realtime clock is independent of the decorative scene. A release destroys progress immediately; the 400 ms / 1.5 s recovery is visual only. Haptics occur at 25%, 50% and 75%.


## 5.2 native panels

App-owned confirmations use a 12 dp fade/slide over 300 ms on entry and an 8 dp fade/slide over 250 ms on dismissal. Bottom sheets retain their native 300/250 ms XML transitions. The same master, foreground, screen and power gates apply to both. Chips have 4 dp margins so a 1.04 scale fits without clipping. A bounded ripple mask and zero elevation prevent square pressed edges or shadows. No animation loop or polling was added.

## 5.3 hold ring and Exit

Empty ring halves use opacity zero. Each moving semicircle clips one CSS pixel beyond its centre line until that half is complete, removing antialiased slivers at the 12 and 6 o’clock boundaries. The gap closes at half/full progress; the visible ring still animates only with transforms. A release retains the current arc opacity during its 400 ms drain and hides the empty halves once recovery finishes. No new loop, particle, or JavaScript frame animation is added. Tap highlight, caret and selection decoration are suppressed on the hold circle; keyboard focus remains visible.

Active-session Exit opens the same native confirmation style as other dialogs and freezes the scenery behind it. Inactive Exit cancels touch tracking and freezes/pauses the WebView immediately, releases access and closes without an additional screen. Any teardown checks run only during Exit, at 250 ms intervals for at most three seconds. A delayed Android service callback releases itself without further UI.
