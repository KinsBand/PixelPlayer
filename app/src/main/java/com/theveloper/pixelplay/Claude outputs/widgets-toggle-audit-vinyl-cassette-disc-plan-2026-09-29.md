# PixelPlayer: widget audit, vinyl redesign, and new Cassette and Disc widgets (plan)

**Date** 2026-09-29 · **Status** Plan only. Nothing has been changed yet.
**Scope** (1) Every setting under Settings → Widgets must visibly switch its element on or off on the placed widget. (2) Redesign the vinyl (turntable) widget and make sure it actually spins on the home screen. (3) Add two new widgets: **Cassette** and **Disc (CD)**.

Files read for this plan: everything in `ui/glancewidget/`, `data/preferences/WidgetCustomization.kt`, `WidgetPreferencesRepository.kt`, `data/service/WidgetUpdateManager.kt`, the widget wiring and action handling in `MusicService.kt`, `presentation/viewmodel/VisualWidgetsViewModel.kt`, `presentation/screens/settings/WidgetsSettings.kt` and `WidgetPreviews.kt`.
I could **not** read `res/`, `AndroidManifest.xml` or `app/src/test/`, because they are outside the connected folder. Phases 4–5 need those folders, so they have to be connected before those phases start.

---

## 1. Audit: why toggles "don't work"

### 1.1 Three bugs that break every widget at once

These explain most of the "some toggles don't work" reports, because they affect every widget and not just one setting.

| # | Bug | Where | Effect |
|---|---|---|---|
| **S1** | The config is read **once, in `provideGlance`**, then captured by the `provideContent` lambda. A settings change sends `ACTION_WIDGET_UPDATE_PLAYBACK_STATE` → `widget.update()`. While a Glance session is alive, `update()` only **recomposes**; it does not run `provideGlance` again, so the widget keeps rendering the old `config`. | all 5 widgets | A toggle only seems to apply once the session happens to time out, which looks random. The turntable is the worst case. Its spin loop calls `update()` every ~100 ms, which keeps the session alive indefinitely, so **turntable settings never apply while music plays**. |
| **S2** | `MusicService` builds `WidgetUpdateManager` **without** the `turntableOptions` argument, so the service always uses `TurntableOptions()` defaults (SMOOTH, 8 rpm, clockwise). `TurntableWidget.provideGlance` calls the same controller with the **user's** options. | `MusicService.kt:286`, `WidgetUpdateManager` | Spin mode **Off / Tick / Position**, speed and direction are overridden on every playback event. When the user's speed differs from 8 rpm, the two callers disagree, so the loop is stopped and restarted on every update. |
| **S3** | A settings change never re-syncs `TurntableSpinController`. `persist()` clears the render cache and broadcasts, but `WidgetUpdateReceiver` doesn't call the controller. | `VisualWidgetsViewModel.persist`, `WidgetUpdateReceiver` | Changing spin mode or speed does nothing until the next play/pause. |

### 1.2 Toggle matrix (✓ honoured · ✗ ignored · ⚠ partial or conditional · — not shown for that widget)

| Setting | Adaptive | Bar 4×1 | Control 4×2 | Grid 2×2 | Turntable |
|---|---|---|---|---|---|
| Background: Solid / Transparent | ✓ | ✓ | ✓ | ✓ | **✗ never applied** |
| Background: Outlined | **✗ same as transparent, no outline** | ✓ | ✓ | ✓ | ✗ |
| Corner radius | — (by design) | ✓ | ✓ | ✓ | **✗ shown, never applied** |
| Accent (Material You / Album / Fixed) | ⚠ surface and text only; buttons stay on `GlanceTheme` | ✓ | ✓ | ✓ | ✓ |
| Fixed accent **colour** | **✗ no colour picker exists anywhere in the UI.** "Fixed" always gives the default indigo | same | same | same | same |
| Title | **✗** | ✓ | ✓ | **✗** | **✗ hidden in settings but read by the code**, so the text layouts can't be reached |
| Artist | **✗** | ✓ | ✓ | **✗** | ✗ (same as Title) |
| Prev / Next | **✗** | ✓ | ✓ | ✓ | ⚠ only in wide or tall shapes; a square cell shows nothing |
| Shuffle | **✗** | **✗** | ✓ | **✗** | ⚠ only if Prev/Next is on **and** the shape is wide or tall; its icon doesn't show whether shuffle is on |
| Repeat | **✗** | **✗** | ✓ | **✗** | ⚠ same as Shuffle |
| Favourite | **✗ always shown in the Large layout** | **✗** | **✗** | **✗** | ⚠ only when Badges is on; hidden in the wide layout |
| Progress (None / Line / Wavy) | **✗** | ✓ | ✓ | **✗** | — |
| Spin mode / speed / direction | | | | | ⚠ S2 and S3 |
| Disc size, label size, tonearm, grooves, sheen | | | | | ✓ (still subject to S1) |
| Badges | | | | | ⚠ one switch controls both bubbles; the play bubble can't be hidden on its own |

That's **18 settings that are ignored outright** and **7 that are partial**, before counting S1–S3.

### 1.3 Smaller issues found

- **Previews don't match the widgets.** `ContentWidgetPreview` ignores Shuffle, Repeat, Favourite, Progress and Accent. The Grid preview always draws Prev/Next. So a toggle can look like it works in the preview but not on the home screen, or the other way round.
- In the adaptive widget, the favourite icon pair (`round_favorite_24` / `rounded_favorite_24`) looks swapped compared with the turntable (`rounded_favorite_24` / `round_favorite_border_24`). Check whether "not favourite" shows a filled heart there.
- `TurntableWidget` compares `repeatMode` against a raw `1`, while `ControlWidget4x2` uses `Player.REPEAT_MODE_ONE`. Unify them (this was already an open item in the Sept 16 turntable notes).
- `SettingsRegistryTest` still has no `"widgets"` mapping (also an open item from Sept 16), and the test folder is still unreachable.

---

## 2. Why the vinyl doesn't spin reliably on the home screen

Each point below is a likely cause, listed from most to least probable. Every fix gets a log line so it can be confirmed on a device.

1. **The recomposition gets skipped.** The frame loop calls `widget.update()`, and Glance re-reads `PlayerInfoStateDefinition`. That state is **one shared DataStore**, and when nothing has changed it returns the **same `PlayerInfo` instance**. Under Compose strong skipping (the default from Kotlin 2.0.20), `TurntableContent` and `Disc` get the same parameters and are **skipped**. The angle comes from `SystemClock` inside `Disc`, not from any Compose state, so nothing forces a new frame. Result: the record stays still, or only moves when a real state change happens (a track change, or position drift over 3 s).
2. **S2 and S3** (above) start and stop the loop with conflicting options.
3. **Frame size.** `MAX_RENDER_PX = 512` gives 1 MiB of ARGB for each frame. At large cell sizes that can push a `RemoteViews` update past the ~1 MB binder transaction limit or the per-widget bitmap budget. When that happens, the update fails silently and the frame is dropped.
4. **The loop only starts from two places** (`provideGlance` and the service's update sweep). If the app process is killed while playback continues in a new process, nothing restarts the loop until the next playback event.

---

## 3. Target architecture (shared by all spinning widgets)

### 3.1 Settings are read live, not captured once
In `provideContent`, collect `WidgetPreferencesRepository.configFlow(kind)` with `collectAsState(initial = loadWidgetConfig(...))`. The config then becomes Compose state, so a settings change recomposes the live session straight away. This fixes S1 for every widget in one place: `WidgetConfigAccess.rememberWidgetConfig(kind)`.

### 3.2 One `SpinClock` for all spinning widgets (replaces `TurntableSpinController`)
- **Scope:** one controller for Vinyl, Disc and Cassette. It keeps a `StateFlow<Long> frame` and an angle accumulator **per kind**, because each kind has its own speed.
- **Frame source:** the composable reads `frame` with `collectAsState()` and passes it **as a parameter** to the disc composable. That makes skipping impossible, which fixes the root cause in §2.1. Keep calling `update()` about every 30 s only to revive a session that has died.
- **Options:** it reads the user's options itself, from the repository flow, so it can't be driven with the wrong options. This removes the need for S2's constructor parameter.
- **Settings changes:** it listens for them and re-parks and restarts itself. This fixes S3.
- **Existing guards:** keep them (screen off, battery saver, no widget placed, frame rate derived from speed). Also add restarting after process death: `MusicService.onCreate` → `SpinClock.reconcile()`.
- **Spin-up / spin-down:** ease in over about 900 ms when play starts, and ease out over about 700 ms on pause, like a real platter. This costs about 10 extra frames per transition.
- **Frame size limit:** at most **384 px** (576 KB). Render at `min(cellPx, 384)`.

### 3.3 Two ways to spin (decision D1)
| | **Frame-push** (what exists today, fixed) | **Native** (new) |
|---|---|---|
| How it works | The app re-renders a rotated bitmap for each frame | A `ProgressBar` whose indeterminate drawable is a `<rotate>` layer, embedded through Glance `AndroidRemoteViews`. The launcher animates it at display refresh rate with **no app cost** |
| Smoothness | 2–10 fps | 60–120 fps |
| Dynamic album art on the spinning part | Yes | **No.** It can only spin a resource drawable, tinted with `setProgressIndeterminateTintList` (API 31+) |
| Speed | Any value | A fixed set of speeds, one XML layout variant each (e.g. 4 / 8 / 16 rpm × 2 directions), because `indeterminateDuration` can't be set remotely |
| Best for | A vinyl whose label shows the album art; a CD with the album art printed on it | **Cassette reels** (the hubs are pure geometry, so this is ideal). Also a "Sleeve" vinyl style (see §4) |

**Recommendation:** use native spinning for cassette reels, frame-push for vinyl and CD, and add native spinning as an optional "Silky" vinyl style. Before building on it, the native approach needs a one-hour spike on Pixel Launcher, One UI and Nova (see Phase 1).

---

## 4. Vinyl redesign

All proportions below are measured from a real 12" LP (diameter D = 302 mm), expressed as fractions of the radius R.

| Element | Spec |
|---|---|
| **Body** | Not a flat fill. Radial gradient from 100% body colour at the rim to 92% near the label, so the disc reads as slightly dished. A **raised rim bevel**: 1.5% of R wide, lighter on the top-left and darker on the bottom-right (fixed light, drawn in the overlay). |
| **Lead-in** | 0.967R – 0.985R. A smooth band with no grooves. |
| **Grooves** | 0.40R – 0.967R. About 60 hairlines at 3–6% alpha, not 16 rings. Split into **4–7 track bands** with narrow, brighter gaps between them. The band count and spacing come from a seed based on the artwork hash, so every album gets its own record but it looks the same on every frame. |
| **Dead wax / run-out** | 0.345R – 0.40R. A glossier band with a faint handwritten-style matrix scratch (a short arc of strokes). It's asymmetric, **so the rotation is readable**. |
| **Label** | 0.33D (as now, 0.34), with album art via `BitmapShader`. Add: a thin printed ring 6% in from the label edge carrying the song title in small caps along an arc (`Path` + `drawTextOnPath`), plus "SIDE A · 33⅓" opposite it. This gives an asymmetric mark that makes the rotation obvious even on dark art. |
| **Spindle** | 0.024D. A metallic radial gradient (#D8D8DC → #7A7A80) with a 1 px highlight, **drawn in the fixed overlay** because a spindle doesn't visibly spin. |
| **Sheen** | Replace the single radial blob with the classic **two-lobe "bow-tie" reflection**: a `SweepGradient` with bright wedges at about 35° and 215°, 12–16% alpha, clipped to the grooved area. **Fixed** while the record turns (this is how it looks in real life). A second, much fainter anisotropic streak adds depth. |
| **Shadow** | A soft drop shadow (blur 4% of D, offset down-right by 1.5%), baked into the overlay **below** the disc and never rotated. The record then sits on the wallpaper instead of floating. |
| **Tonearm** (redesigned) | Pivot base (a circle with a knurled ring) → S-curved tube → headshell with cartridge → counterweight behind the pivot. **The stylus follows playback progress** from the lead-in to the run-out: `r = 0.967R − p·(0.967R − 0.40R)`, solved for the arm angle. **It lifts to its rest** when paused (the rest position is drawn off the record). It's rendered in the overlay, and its position is quantised to 1% of progress so the overlay cache stays small. |
| **Vinyl variants** (new setting) | **Classic black** (the default; body #121014) · **Coloured** (translucent body tinted from the album palette at 85% alpha, so the wallpaper shows through faintly) · **Picture disc** (album art across the whole record, grooves drawn at 8% alpha over it) · **7" single** (a large 0.22D centre hole with a three-spoke adapter "spider"). |
| **Badges** | Split the setting into **Play bubble** and **Favourite bubble**. The shadow and style match the disc. |
| **"Sleeve" layout** (new, optional, native spin) | A square album sleeve (the art) on the left, with the record **sliding half out to the right** and spinning natively at 60 fps. The record uses a printed label tinted with the album accent colour. Suits 3×2 or 4×2 cells. |

---

## 5. New widget: Cassette

**Default size** 4×2; resizable from 3×2 up to 5×3 (`SizeMode.Exact`). A real cassette is 100.4 × 63.8 mm (aspect 1.574), and the shell keeps that aspect at every cell size.

### 5.1 Anatomy (fractions of shell width W and height H)

| Part | Spec |
|---|---|
| **Shell** | Rounded rect with corner radius 0.035W. A bevel with a 1 px top-left highlight and a bottom-right shade. Five screws (four corners at 0.05W / 0.07H, plus one at bottom centre), each with a cross slot at a fixed but different angle. |
| **Label** | 0.06H – 0.62H, inset 0.05W. **Style (setting):** **Paper** (cream #F2EBD9, four ruled lines, title and artist in a handwritten font, a circled "A" on the left, a coloured stripe at the top taken from the album accent) · **Art** (the album art cropped as a band across the label, with a scrim behind the text) · **Clear** (no label; the shell is translucent smoked plastic, so the internals are visible). |
| **Window** | A stadium shape centred at 0.43H, 0.60W × 0.22H, with smoked plastic at 35% alpha and a fixed glare stripe. |
| **Reels** | Hub centres **0.42W apart** (the real spindle spacing), at 0.43H. Each hub is white with six teeth. **Tape pack radius is driven by progress and keeps the tape's area constant** (tape length is proportional to area): `r_left = √(r_h² + (r_max² − r_h²)(1 − p))`, `r_right = √(r_h² + (r_max² − r_h²)·p)`. Pack colour is #3A271C with a subtle radial sheen. |
| **Tape path** | Visible along the bottom of the window between the two packs, running tangent to each. |
| **Head area** | A trapezoid at the bottom 0.25H: two capstan holes, three rectangular openings, and a felt pressure pad. |
| **Controls** | Deck-style keys along the bottom edge, or overlaid on the window, following the content toggles: ⏮ ⏯ ⏭, plus Shuffle, Repeat and Favourite when those are on. Tapping the window plays or pauses. |

### 5.2 Motion
- **Reels spin at constant tape speed:** angular velocity is proportional to 1 / pack radius, so **the reel with less tape spins faster**. With native spin, each reel picks one of five speed buckets based on its current radius. The bucket is re-chosen on each normal widget update (at least every 3 s of drift, and at every track change). When paused, the reels switch to a static image.
- The tape pack sizes change only on normal updates, so they cost nothing between frames.

### 5.3 Settings
Shell colour (Black / Smoke clear / White / Accent), Label style (Paper / Art / Clear), Reel spin (Native / Off), Side indicator (on/off). Plus the shared appearance and content toggles.

---

## 6. New widget: Disc (CD)

**Default size** 2×2; resizable from 1×1 to 4×4. Wide and tall shapes add a text and transport column, like the turntable.

### 6.1 Geometry (real 120 mm CD, as fractions of radius R)
| Band | Radius |
|---|---|
| Centre hole (transparent, so the wallpaper shows) | 0 – 0.125R |
| Clear polycarbonate hub | 0.125R – 0.275R |
| Stacking ring (a thin raised circle) | ≈ 0.28R |
| Mirror / clamping band | 0.275R – 0.383R |
| Data / printed area | 0.383R – 0.967R |
| Clear outer rim | 0.967R – R |

### 6.2 Faces (setting)
- **Silver (data side):** a mirror gradient plus an **iridescent diffraction sweep**. A `SweepGradient` runs through the spectral hues at 18–24% alpha, with a second counter-phase sweep at 10% so the colour bands cross the way they do on a real disc. **The rainbow is fixed** (it follows the light, not the disc). The rotation is carried by faint asymmetric features: the burn edge, a few micro-scratches, and the ring code in the mirror band.
- **Printed (label side):** album art across the data area, with the clear hub and hole cut out, and the title in small caps on an arc. No trademarked logos.
- **CD-R:** gold or dark-blue dye tint on the data side, with a burn-edge ring at the progress radius. This is a subtle progress indicator.

### 6.3 Presentations (setting)
- **Disc** on its own, with the play and favourite bubbles on the 45° edge, like the turntable.
- **Jewel case:** a square case with the art as the insert, a hinge spine with vertical ridges on the left, and a diagonal gloss reflection. The disc is **slid out of the top-right** and spins.
- **Discman:** a round player lid with the disc seen through a smoked window, a row of buttons, and a small LCD showing `TRK 03  02:14` in a monospace font. The LCD time only changes on normal updates.

### 6.4 Motion
Uses frame-push, since the printed art has to rotate. At 10 fps, anything above about 8 rpm starts to strobe (the wagon-wheel effect). **Speed-feel trick:** above 6 rpm, render with light angular motion blur (three rotated copies across ±4°, alpha 0.5/0.3/0.2, merged into the cached base). The disc then reads as spinning fast without needing more frames.

---

## 7. Making toggles impossible to break again

1. **Capability table.** Each widget kind declares the features it supports, e.g. `WidgetKind.features: Set<WidgetFeature>` (TITLE, ARTIST, PREV_NEXT, SHUFFLE, REPEAT, FAVORITE, PROGRESS, BACKGROUND, CORNER, …). The **settings screen only shows toggles for the features in that set**, so the screen and the widget can't disagree.
2. **Shared control composables.** `WidgetTransport(content, colors, sizeClass)` and `WidgetTrackText(...)` are used by **every** layout in every widget, including all 11 layouts in the adaptive widget. Each flag is then honoured in exactly one place. When a size bucket has no room for a control, the drop order is Repeat → Shuffle → Favourite → Prev/Next. Settings shows a one-line note under the toggles: "Some controls appear when the widget is wide enough".
3. **Per-widget decisions** (change these if you'd prefer something else):
   - **Bar 4×1:** Favourite is shown before Prev. Shuffle and Repeat appear when the widget is at least 5 cells wide.
   - **Grid 2×2:** a Favourite heart overlaid on the artwork corner. Title and Artist on a scrim along the bottom of the artwork. A thin progress bar along the bottom edge. Shuffle and Repeat are removed from its capability set.
   - **Adaptive:** honours every flag in every bucket, using the drop order. OUTLINED now draws a real outline. Buttons follow the accent source.
   - **Turntable:** Background and Corner become real, applied to the stage. Title and Artist toggles are shown. In a square cell with Prev/Next on, a compact transport pill sits under the disc (it takes 18% of the height, and the disc shrinks to fit).
4. **Fixed accent colour picker:** 12 swatches (with the album palette's top three first), plus a hue/tone slider.
5. **Preview parity:** previews call the same renderers and the same capability set. Cassette and Disc previews reuse the real bitmaps, animated with `graphicsLayer`, exactly as the turntable preview already does.
6. **Tests:**
   - `glance-appwidget-testing` (`runGlanceAppWidgetUnitTest`): for **every kind × feature × on/off**, check that the node with that feature's content description is present or absent. This is the automated form of "every toggle works".
   - Unit tests: repository defaults and reset for the new kinds; tape-pack area conservation; that the tonearm radius is monotonic in progress; that `SpinClock` angle is continuous across park, restart and speed change.
   - Fix `SettingsRegistryTest` parity for the new entries (needs the test folder connected).

---

## 8. Phases and acceptance criteria

| Phase | Contents | Done when |
|---|---|---|
| **P0 Toggle correctness** | §3.1 live config · capability table · shared control composables · fixes to all five widgets per §7.3 · colour picker · preview parity · Glance unit tests | The §1.2 matrix is all ✓ or —. Each toggle applies on the home screen **within 1 s, while music is playing**. |
| **P1 Spin reliability** | `SpinClock` (§3.2) · remove S2/S3 · 384 px cap · reconcile after process death · spin-up/down · **native-spin spike** on 3 launchers · frame and failure logging | The vinyl spins on Pixel Launcher, One UI and Nova at every size from 1×1 to 4×4. Off / Tick / Position / Smooth each behave as labelled. It stops when the screen is off, in battery saver, and when the last widget is removed. No `TransactionTooLarge` in logcat. |
| **P2 Vinyl redesign** | Everything in §4 | Side-by-side screenshots in light/dark and at three sizes pass review. The frame cost stays within today's budget. |
| **P3 Cassette** | §5, native reels, receiver, provider XML, picker preview (`previewLayout` for Android 12+, image for older versions), strings | Reels run at 60 fps with no app wakeups, the pack sizes track progress, and every toggle passes the P0 tests. |
| **P4 Disc** | §6, all faces and presentations | Same criteria as P3. Motion blur reads as speed without strobing. |
| **P5 Polish** | Accessibility (e.g. "Cassette, *Song* by *Artist*, playing, 1:23 of 3:45") · localisation of the new strings · battery measurement (Smooth vs Tick vs Native over 30 min) · a build-notes doc in the project | The numbers are recorded, and TalkBack reads every control. |

**Files:** new ones are `SpinClock.kt`, `VinylRenderer.kt` (renamed from `TurntableRenderer`), `CassetteWidget.kt` / `CassetteRenderer.kt` / `CassetteWidgetReceiver.kt`, `DiscWidget.kt` / `DiscRenderer.kt` / `DiscWidgetReceiver.kt`, `WidgetCapabilities.kt`, `WidgetControls.kt`.
Edited: all five widgets, `WidgetCustomization.kt` (new kinds and option classes), `WidgetPreferencesRepository.kt`, `WidgetUpdateManager.kt`, `WidgetUpdateReceiver.kt`, `MusicService.kt` (wiring only), `VisualWidgetsViewModel.kt`, `WidgetsSettings.kt`, `WidgetPreviews.kt`, `SettingsRegistry.kt`.
Resources: `res/xml/*_widget_info.xml`, `res/layout/*` (native-spin layouts and picker previews), `res/drawable/*` (reel hubs, rotate drawables), `res/font/*` (the handwritten font), `strings_widget.xml`, and `AndroidManifest.xml`.

---

## 9. Decisions needed before building

- **D1 · Native spin.** Accept the §3.3 split (native for cassette reels and the "Sleeve" vinyl, frame-push for the album-art vinyl and the CD)?
- **D2 · "Disc" means a CD?** The plan assumes a compact disc, with Disc / Jewel case / Discman presentations.
- **D3 · Handwritten font** for the Paper cassette label. It needs an OFL-licensed font file in `res/font` (e.g. Caveat, about 90 KB), or it falls back to the system cursive font.
- **D4 · Overflow behaviour** for the Bar and Grid widgets (§7.3). Hide controls that don't fit and show a note (proposed), or refuse to show the toggle at all?
- **D5 · Folder access.** Connect `app/src/main/res`, `app/src/main/AndroidManifest.xml` (via `app/src/main`) and `app/src/test/java/com/theveloper/pixelplay` before P3.
