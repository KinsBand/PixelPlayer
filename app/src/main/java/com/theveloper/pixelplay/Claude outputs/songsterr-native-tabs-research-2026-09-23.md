# Songsterr native tabs: research and fix plan (2026-09-23)

**Status:** research only. No code has been changed. The findings were checked against live Songsterr data for 6 songs (Back in Black, Enter Sandman, Smells Like Teen Spirit, Seven Nation Army, Hotel California, Sweet Child O' Mine). The parse failures were reproduced with Gson.

Files reviewed: `data/songsterr/{SongsterrClient, TabParser, SongsterrApiService}.kt`, `data/songsterr/model/*`, `presentation/components/{DrumsPerformanceView, GuitarPerformanceView}.kt`.

## Why drums always fall back to the website

The drum data downloads correctly. **Gson then fails to read it**, so `parseRevision()` returns null. That leads to the WebView extractor (which uses the same parser and fails again), then the 12 s timeout, then the Songsterr site.

Reproduced with Gson 2.10:

| Field in the real JSON | App model | Result |
|---|---|---|
| `note.accentuated: 1` or `2` (a number: 1 = accent, 2 = heavy accent) | `Boolean` | `JsonSyntaxException: Expected a boolean but was NUMBER` |
| `note.string: -0.5`, `1.5`, `3.5` (for drums this is the staff line) | `Int?` | `Expected an int but was -0.5` |
| `measure.alternateEnding: [1]` (an array) | `Int?` | `Expected an int but was BEGIN_ARRAY` |

- **All 6 drum tracks tested** contain both numeric accents and fractional `string` values, so drums can never parse natively.
- **3 of the 6 popular guitar tracks** contain numeric accents, so those songs fail too. That's why guitar only works on some songs.

## Guitar: what's parsed or drawn wrong

1. **Only voice 0 is read** (`m.voices.getOrNull(0)`). Voice 2 is lost, and so are drum feet on tabs that split hands and feet.
2. **Time signature:** `signature` is only present on the bar where it changes. The app falls back to 4/4 on every other bar, which breaks every 3/4, 6/8 or mixed-meter song after bar 1.
3. **Repeats:** the field is `repeat` (the number of times), not `repeatCount`. Repeat end bars are never shown.
4. **Bends:** `tone` is in hundredths of a whole tone (25 = ¼, 50 = ½, 100 = full). The app treats ≥2 as a full bend, so every bend reads "▲1".
5. **Slides:** the real values are `legato`, `shift`, `below`, `above`, `upwards` and `downwards`. The app checks `into_from_below` and similar values, which never occur, so every slide draws as "/".
6. **Grace notes** (`graceNote: onBeat/beforeBeat`) have a duration but take no time in the bar. The app counts them, which squashes the rest of the bar.
7. **Ties:** a tied note is drawn as nothing. Songsterr shows it as `(5)`.
8. **Hammer-on vs pull-off:** `hp` is always drawn as "h". It should be "p" when the next fret on that string is lower.
9. **7- and 8-string guitars:** `numStrings.coerceAtMost(6)` cuts off the low strings. There's also no capo display (`capo` field).
10. **Other fields not read:** `leftHandVibrato` (slight/wide), `harmonic` types (natural/pinch/artificial/tapped + `harmonicFret`), `letRing`, `tapping`, `tremoloBar`, `brushStroke`/`upStroke`, `chord.text` (chord names like E5), `text` annotations, `staccato`, `dots`/`tuplet` brackets, and tempo changes (only the first BPM is used; Sweet Child has 16).
11. **Track choice:** the app takes the *first* guitar track, which is often a lead or overdub part. The API gives `popularTrackGuitar`, `popularTrackBass` and `popularTrackDrum`. The page state also has `isDrums`, `isGuitar` and `isBassGuitar`, which are more reliable than `instrumentId` ranges (bass is 33–39, not 33–35).

## Drums: what to fix after the parse crash

- A drum note's `fret` is the General MIDI note number, and `string` is its vertical staff position. Seen: 36 kick, 38 snare, 42/46/44 hi-hat closed/open/pedal, 43 floor tom, 49/57 crash, 51 ride.
- Keep the current MIDI-to-row mapping. Add 37 (side stick), 39 (clap), 41 (low floor tom), 53 (ride bell) and 59 (ride 2). Only show rows that the song actually uses.
- Accent levels: `>` for 1, `^` for 2. Ghost notes in brackets. Flams come from `graceNote` beats.
- Merge all voices into one grid.
- Grid positions: the current code draws each note 30% into its slot and assumes 16 grid lines per bar. Place notes by exact tick (onset ÷ bar length) instead. That fixes triplets and shuffles, which currently drift off the grid lines.

## Fetching: simpler and more robust

- Use `GET /api/meta/{songId}` (JSON) instead of downloading the HTML page and scraping `<script id="state">`. It returns `revisionId`, `image`, `tracks[]` and the `popularTrack*` indexes. The meta response has **no `partId`**; the CDN path uses the track's **index** (checked on 6 songs: the part JSON's own `partId` equals the index each time).
- CDN URL: `https://{host}.cloudfront.net/{songId}/{revisionId}/{image}/{index}.json`.
  - Normal hosts, rotated on 403 or 404: `dqsljvtekg760`, `d34shlm8p2ums2`, `d3cqchs6g3b5ew`.
  - An image token ending in `-stage` uses `d3d3l6a6rcgkaf`.
  - A missing image token uses the legacy path `/part/{revisionId}/{index}` on `d3rrfvx08uyjp1`.
  - (Host list from the SongsterrToGuitarPro project; not independently verified.)
- Cache each part's JSON on disk by `songId/revisionId/index`, so reopening a song is instant and works offline.
- Remove the hidden-WebView extractor. After the parser fixes it isn't needed, and it doubles the work. Keep "Open in Songsterr" as a manual button, not an automatic fallback.

## Layout: one horizontal row per section and riff

In real data, section markers are clean (Intro / Verse 1 / Chorus / Solo / Bridge / Outro), and riffs repeat inside a section. For example, Back in Black rhythm guitar has Verse 1 = a 4-bar riff × 2, and a Bridge made of 2 unique bars.

Proposed model (`TabLayout.kt`, pure Kotlin, easy to unit-test):

1. **Section** = the bars from one `marker` up to the next one (bars before the first marker become "Intro").
2. **Riff folding:** inside a section, hash each bar (all voices: durations, strings, frets, ties). Find the shortest length L (1, 2, 4 or 8 bars) that repeats across the whole section. Show that riff once with a "×N" badge. If no length fits, fold runs of identical consecutive bars ("×3").
3. **Repeated sections:** if a section's hash matches an earlier one ("Chorus" again), show it collapsed with a "Same as Chorus (bar 19)" label that can be tapped to expand.
4. **Rendering:** a vertical `LazyColumn` of sections. Each section is **one horizontal row** (a single horizontally scrolling Canvas) with its name and bar range pinned on the left. The rows are no longer cut every 4 bars.
5. **Bar width from content, not fixed:** merge the onsets from all voices into time slots. Each slot gets a minimum width (for example 18 dp for a 16th note, and wider for longer notes, using a square-root scale). A bar of 16ths is then never squashed into the same width as a bar of whole notes.

Optional next step: **follow playback.** Build a bar → milliseconds timeline from `automations.tempo` (with the real repeats unrolled), highlight the current beat, and auto-scroll to the current section row and within it. Songsterr's tempo map is for its own synth audio and can drift from the recording, so add a "nudge offset" control. (Songsterr also has per-video sync points; the endpoint isn't researched yet.)

## Suggested build order

1. Parser crash fixes: accent as `Int`, `string` as `Double`, `alternateEnding` as `List<Int>`, `repeat`. Read all voices and carry the time signature forward. *This alone should make drums native.*
2. Switch to `/api/meta`, use the `popularTrack*` indexes, rotate CDN hosts, add the disk cache, and remove the WebView fallback.
3. The section/riff layout and content-based bar widths.
4. The guitar notation list above (bends, slides, ties, h/p, grace notes, 7-string, capo, chords, text, tempo changes).
5. Drum polish (extra MIDI notes, accent levels, flams, used rows only).
6. Optional: follow playback.

## Sources

- [savesterr](https://github.com/j4ckxyz/savesterr): endpoints, JSON field list, grace/bend/tie rendering rules.
- [SongsterrToGuitarPro](https://github.com/djrobson5/SongsterrToGuitarPro): CDN host buckets, drum `string`/`fret` meaning, slide and accent values.
- The live Songsterr API, read from the in-app browser on 2026-09-23.
- Songsterr's terms treat print/export as a paid feature and don't allow automated tools that give paid features without a subscription. Showing tabs in the app is the same kind of use as those tools, so consider the account/Plus question before shipping widely.
