# Pixel Player home-screen widgets: full inventory before removal (2026-10-05)

A record of everything the Glance widget system did, written just before it was removed. The **Camera hole island** (overlay around the front camera, `ui/overlay/*`) is a separate feature. It stays in the app, under Settings › Appearance.

## The 7 widgets

| Widget | Default cell size | Resize | How it looks / what it does |
|---|---|---|---|
| **Adaptive** (`PixelPlayGlanceWidget`) | 4×2 (min 10dp) | both axes | One widget with **11 layouts picked by cell size**. At 1×1 it's a circle. Supports every option except corner radius, because each layout picks its own shape. |
| **Bar 4×1** | 4×1 | horizontal | One row: artwork, then title/artist, then transport controls. Favourite, shuffle and repeat join the row when switched on. If the row is too narrow they drop out in a fixed order, and the text always keeps a minimum width. |
| **Control 4×2** | 4×2 | horizontal | Artwork and details on top, a full row of transport controls below. The favourite heart sits beside the title so it never pushes shuffle or repeat out. |
| **Grid 2×2** | 2×2 | both | Four equal tiles: artwork, play, previous, next. Title and artist sit on a scrim over the artwork, with the heart in the corner. No shuffle or repeat. With prev/next off, artwork and play fill the full height. |
| **Turntable** | 2×2 (min 40dp) | both | A vinyl record that spins while music plays. It's drawn as bitmaps (`TurntableRenderer`) in 3 layers: a fixed shadow, a rotating base, and a fixed overlay. The base has vinyl, grooves with track gaps, a dead-wax band with an etched matrix number, the album-art label with print, and the centre hole. The overlay has the bow-tie reflection, rim highlight, spindle and an optional tonearm. Progress is shown by the tonearm moving across the record. |
| **Cassette** | 4×2 (min 110×70dp) | both | Stacked vector layers: shell, details, label, window, tape packs, hubs, glass, handwriting. **The launcher animates the reels** (the hubs are indeterminate ProgressBars with a `<rotate>` drawable), so they cost no battery. Tape-pack sizes follow playback progress and are area-true. The emptier reel turns faster. Tapping the window plays/pauses, and tapping anywhere else opens the app. |
| **Disc (CD)** | 2×2 (min 40dp) | both | A real 120mm CD with true proportions: 15mm hole, clear hub with stacking ring, mirror band with matrix code, data area, clear rim. The rainbow diffraction stays fixed in the overlay while the disc turns. Motion blur kicks in at high speed. It can show on its own, half out of a jewel case, or through the lid of a portable player. |

## Shared customisation (per widget, Settings › Widgets)

There was a live preview pinned at the top, a widget picker, and a **Reset** action in the top bar. Each widget only showed the controls it actually honours (`WidgetKind.features`). For size-dependent widgets the screen explained that some controls only appear once the widget is big enough.

**Appearance**

- Background: Solid (default), Transparent (wallpaper shows through), or Outlined (surface-coloured outline drawn as a bitmap border).
- Corner radius: 0–48dp, default 28. Not offered for Adaptive.
- Accent source: Material You, Album art (default, uses the palette taken from the cover), or Fixed colour.
- Fixed colour swatches (12): indigo night (default `#1B0A33`), electric blue, teal, forest, mustard, burnt orange, tomato, magenta, violet, graphite, bone, blush.

**Content toggles**

- Title (on), Artist (on), Prev/Next (on), Shuffle (off), Repeat (off), Favourite (on).
- Progress: None, Line (default) or Wavy. The wavy one used `WavyLinearProgressIndicator`, drawn as a bitmap. Not offered on Turntable, Cassette or Disc, which show progress physically.
- Options a widget doesn't support were forced off at render time (`effectiveContent`).

## Turntable options

- Vinyl style:
  - Classic: black vinyl with an art label.
  - Coloured: translucent, tinted from the accent.
  - Picture disc: art across the whole record.
  - 7" Single: wide hole with a 3-spoke adapter.
- Disc size 55–100% (default 82%), label size 18–58% (default 34%).
- Spin mode:
  - **Smooth**: continuous frames while playing.
  - **Tick**: one step every ~2s, low battery use.
  - **Position**: angle comes from playback position, with no extra wake-ups.
  - **Off**: never turns.
- Speed 2–24 rpm (default 8), clockwise or anticlockwise.
- Toggles: Play bubble/badges (on), Tonearm (off), Grooves (on), Sheen (on).

## Cassette options

- Shell: Black (default), Smoke (translucent, shows the tape path), White, or Accent-tinted.
- Label: Paper (cream with ruled lines and handwritten track, default), Album art, or None.
- Toggles: Spin reels (on), Side "A" circle (on).

## CD options

- Face: Silver (mirror with rainbow), Printed (art across the disc, default), or CD-R (gold dye with the burn edge following playback).
- Presentation: Disc only (default), Jewel case, or Portable player.
- Disc size 55–100% (default 86%).
- Spin mode as for the turntable. Speed 4–40 rpm (default 16), direction setting, play bubble toggle.

## Engine and plumbing

- **SpinClock**: one frame loop for the turntable and CD. It runs only while audio plays (plus a short coast after pause), while such a widget is placed, while the screen is on, and never in battery saver. It has inertia: about 1s spin-up and a coast to stop (`SpinMotion`, closed-form angle). Each frame is a real recomposition (the angle is passed as a parameter) so Glance can't skip it.
- **SpinStage**: shared layout for round-object widgets. A wide cell (≥1.6:1) puts text and controls beside the stage. Other cells stack them underneath and shrink the stage.
- **WidgetLayoutMath**: pure, unit-tested geometry: which controls fit (`planTransport`), bubble placement, tape-pack radii, stylus position.
- **WidgetUpdateManager** (in `MusicService`): debounces updates (250/300ms), diffs `PlayerInfo` so it only re-renders on visible changes, sends a follow-up update for late artwork, and keeps a 4-item queue preview.
- `MusicService` built a `PlayerInfo` snapshot for the widgets: title, artist, art bytes (sanitised, capped and cached, with 30s failure back-off), album-art theme colours, favourite, shuffle, repeat, progress, and the queue.
- Widget buttons sent `ACTION_WIDGET_*` intents to `MusicService` (play/pause, next, prev, favourite, play-from-queue, shuffle, repeat).
- Per-widget settings were stored in their own DataStore (`WidgetPreferencesRepository`). Live config reached widgets already on the home screen through `WidgetConfigAccess`.
- Debug performance report had a "Widget active" field and a `widget_update` metric.
- Dependencies: `androidx.glance` / `glance-appwidget` / `glance-material3` 1.3.0-alpha01.
- 34 Settings search entries, ~120 widget strings in 11 languages, 52 drawables/layouts/xml files, and 1 unit test (`WidgetLayoutMathTest`).
