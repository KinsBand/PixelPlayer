# Tabs update: one-line Instruments card, Edit mode (drums first), tab timing panel

2026-10-05 · PixelPlayer · Instruments page (Songsterr tabs)

**Your answers**
- Instruments card → **one-line card**
- Publishing → **hand-off through a Songsterr sign-in** (the app builds the file and submits it through Songsterr's own page; the draft is deleted once the version is live)
- Edit scope → **drums first**, with an editor core that guitar and bass can plug into later

The work is split into three prompts, one per area (A cleanup, B tab timing, C edit mode). Each one has its own plan. Do them in the order A → B → C. C is the big one and is split into phases.

---

## 1. Evidence (all read from the code today)

| # | Where | What it shows |
|---|---|---|
| E1 | `presentation/components/tabs/TabPracticePanel.kt:242-281` | Row 3 Instruments card: `InstrumentIcon` + a two-line `Column` (part / "person · N muted") + `Text("Instruments  ›")`. `height(58.dp)`, `RoundedCornerShape(18.dp)`, `clickable { controller.openPicker() }` |
| E2 | `TabPracticePanel.kt:736-738` | Pill subtitle: `"Older version · tap for other tabs"` else `"Tap for other tabs of this song"` |
| E3 | `TabPracticePanel.kt:815-818` | Listings page: title "Other tabs of this song" + helper `"If this tab looks wrong, another upload of the song may fit better."` |
| E4 | `TabPracticePanel.kt:851-854` | History page: helper `"Every edit saved on Songsterr, newest first. Older ones can have different parts."` |
| E5 | `TabPracticePanel.kt:706-711` | Sheet header's first button: `ArrowBack`. On PARTS it means "Close" (`sheetState.hide()` then `closePicker()`); on other pages it means "Back to parts" |
| E6 | `TabPracticePanel.kt:713-725, 747-754` | The "Tab by X" pill and the History button are toggles: tapping either again goes back to `InstrumentsPage.PARTS` |
| E7 | `TabPracticePanel.kt:1205-1221` | Tab timing sits inline in `TabOptionsSection` (options menu): title "Tab timing", helper "Moves the cursor against the song", then `LyricsSyncControls(offsetMillis = controller.syncOffsetMs …)` |
| E8 | `presentation/components/LyricsSheet.kt:703, 1798-1817` | `var showSyncControls`; `AnimatedVisibility(visible = … && showSyncControls, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut())` with `LyricsSyncControls` directly above the "Playback Controls Row". Uses default specs, not MotionTokens |
| E9 | `presentation/components/subcomps/LyricsMoreBottomSheet.kt:383-413` | "Adjust sync" / "Hide sync controls" `ListItem` with `Icons.Rounded.Tune`; on tap: `dismissSheetAnimated()` then `onToggleSyncControls()` |
| E10 | `res/values/strings_player.xml:176-177` | `lyrics_controls_adjust_sync` = "Adjust sync", `lyrics_controls_hide_sync` = "Hide sync controls" |
| E11 | `LyricsSheet.kt:1745-1776` | The bottom controls column has its own vertical drag: dragging down past `controlsHideThresholdPx` calls `hideControlsNow()`, dragging up opens `practice.panelExpanded` |
| E12 | `TabPracticePanel.kt:148-153` | The practice panel opens and closes with `AnimatedVisibility` using `spring(StiffnessMediumLow)` + `tween(220)` / `tween(140)` (hard-coded) |
| E13 | `ui/theme/MotionTokens.kt` | `EmphasizedDecelerate`, `EmphasizedAccelerate`, `Emphasized`; `DurationShort4 = 200`, `DurationMedium2 = 300`, `DurationMedium3 = 350` |
| E14 | `data/songsterr/SongsterrApiService.kt:9,14` | Only two `@GET` calls (`api/songs`, `api/song/{id}`). There's no write or upload API anywhere |
| E15 | `data/songsterr/SongsterrJson.kt:174-190` | `parseTrack` → `trackFrom(JsonObject)` → `RevisionTrack`. It keeps only the fields that are modelled, so the round trip **loses data** |
| E16 | `data/songsterr/TabParser.kt:16-18, 57-95` | Drum notes store Songsterr's staff position in `string` and the articulation id in `fret`. `ARTICULATIONS` lists about 30 kit pieces (glyph, GM sound, staff position) and is exposed as `drumArticulations` |
| E17 | `data/songsterr/SongsterrClient.kt:333-346` | `fetchRevisions` is cached for 24 h in `revisions_$songId.json` |
| E18 | `presentation/components/tabs/TabPracticeController.kt:167, 201, 242, 251, 336` | `panelExpanded`, `picking`, `currentRevisionId`, `isDrumsView`, `chooseRevision()`. The file is already 1450 lines |
| E19 | `presentation/components/TabNotationView.kt:468-472` | `detectTapGestures` maps a tap to a `PlacedMeasure` (tap to seek today) |
| E20 | `presentation/components/tabs/ScoreLayout.kt:13-73` | `PlacedSlot(slot, x)`, `PlacedMeasure.xAt(tick)`: the layout already knows the x of every beat, which hit-testing can reuse |
| E21 | `presentation/components/tabs/NotationAuditionPlayer.kt:50` | `play(key, track, font, entries)` already plays notation back |
| E22 | `presentation/components/tabs/DrumSession.kt:38` | `connected` = an electronic drum kit is plugged in (MIDI) |
| E23 | (search) | No Guitar Pro / GPIF / MusicXML writer exists in the code |
| E24 | `LyricsSheet.kt:1957` | `TabPracticePanel` sits under the lyrics toolbar (the tools area) |
| E25 | `DailyMixLauncher.kt:636-640` | `MaterialTheme.motionScheme.defaultSpatialSpec / fastSpatialSpec` are already used in the app |

Other screens and files read: `RevisionModels.kt` (Revision\* model: measure `signature / marker / repeatStart / repeat / alternateEnding / doubleBarline`; beat `type / dots / tuplet / velocity / text / graceNote / rest`; note `fret / string / tie / ghost / staccato …`).

---

## 2. Your reference screenshots: what to copy and what to beat

The 45 screenshots in **yes yes** are the flat.io Android editor with a drum set selected.

**Worth copying**
- A two-row tool sheet. Row 1 holds the current palette, row 2 holds the palette tabs (note, pitch, articulation, dynamics, grace, bar, clef, key, time, repeats, text), with a history clock on the right.
- Durations from whole note to 32nd, with an "add note" toggle.
- A blue caret in the score. Long-press and drag selects a range, and a floating bar appears over it (delete, copy, cut, paste, duplicate, repeat).
- A magnifier loupe while dragging a note up or down.
- A strip of drum pads, each showing its notehead on a mini staff.

**What flat gets wrong (our chances to beat it)**
1. **Cut-off pad names**: "oustic Bass Dru", "oustic Snare Dr". The pads are one long strip you have to scroll in a line, with no kit layout.
2. **Tools that don't apply to drums**: clefs, key signatures and accidentals are all offered on a drum part, and using one throws a red toast: *"This operation is not possible on unpitched."*
3. **Lots of sideways scrolling** for the palettes. Dynamics take three screens.
4. **No timing context**: you can't hear or see your edit against the real song.
5. **No real-time entry** from an electronic kit.
6. **No review of changes** before saving.

MuseScore mobile can only view scores; editing needs the desktop app. So beating both comes down to five things: fast drum entry, only tools that make sense, the real song underneath, recording from an e-kit or pads, and a clear diff before publishing.

| | flat.io (mobile) | MuseScore (mobile) | **PixelPlayer edit mode** |
|---|---|---|---|
| Drum pads | 1 scrolling strip, names cut off | — (desktop only) | Kit-shaped board, full short names, long-press for variants |
| Tools on drums | All, including clef/key (then an error) | — | Only drum tools; pitched tools hidden |
| Entry | Tap to step | — | Tap to step, **chords by quick double tap**, **record from pads or an e-kit, snapped to the grid** |
| Hear it | Synth | — | Synth **or against the real song**, with the existing tab timing |
| Bar fullness | Rests fill silently | — | Bar fill meter + automatic rests |
| Changing a kit piece | Retype the notes | — | **Swap piece** on a selection (hi-hat → ride) |
| Before saving | Nothing | — | **Diff review** (changed bars highlighted) + a "what changed" note |
| Where it lives | Their cloud | Their cloud | Published as a new Songsterr version that every Songsterr user can see |

---

## 3. ASCII visual plans

### V1: Instruments card (prompt A)

```
BEFORE (two lines, E1)
┌────────────────────────────────────────────────┐
│ 🥁  Drums                         Instruments › │
│     Lars Ulrich · 1 muted                       │
└────────────────────────────────────────────────┘

AFTER (one line, same 58dp height as the tiles above)
┌────────────────────────────────────────────────┐
│ 🥁  Drums  ·  Lars Ulrich · 1 muted          ›  │
└────────────────────────────────────────────────┘
   icon  titleSmall   bodySmall @ 60% (ellipsis)  chevron
```

### V2: Instruments sheet header and pages (prompt A)

```
BEFORE (E2, E5)
 ( ← )  ( 👤 Tab by Kirk                 ⇕ )  ( ⟲ )
          Tap for other tabs of this song

AFTER
 ( ✎ )  ( 👤 Tab by Kirk                 ⇕ )  ( ⟲ )
   │      one line; on an older version: "Tab by Kirk · older version"
   └─ Edit (enters prompt C). Close = swipe down / tap scrim.
      Back from the Tabs / History pages = tap the lit pill or ⟲ again (E6, already works).

Listings page                      History page
 Other tabs of this song            Versions of this tab
 (helper text removed, E3)          (helper text removed, E4)
 ┌ Enter Sandman · 5 parts ✓ ┐      ┌ Version 13 · latest      ✓ ┐
 └───────────────────────────┘      └────────────────────────────┘
```

### V3: Tab timing panel (prompt B)

```
┌──────────────────────────────────────────────┐
│                tab / lyrics area             │
├──────────────────────────────────────────────┤
│                    ────                      │ ← panel grab handle (drag down = hide)
│  Tab timing                        +120 ms   │
│ ( −0.5 )( −0.1 )(  now / reset  )( +0.1 )( +0.5 )│  ← existing LyricsSyncControls
├──────────────────────────────────────────────┤
│  ⏯   ━━━━━━━━━━●──────────────  1:02   ⏭     │ ← play / slider / skip (unchanged)
└──────────────────────────────────────────────┘

Options menu (Instruments section), replacing the inline control (E7):
  [ ⏱  Adjust tab timing ]   ⇄   [ ⏱  Hide tab timing ]
```

### V4: Edit flow (prompt C)

```
Instruments sheet ──(✎)──▶  "Edit a copy" page
                             Based on Version 12 · Tab by Kirk
                             Your edits become Version 13 when you publish
                             ┌ 🥁 Drums ·  Lars              Edit › ┐
                             │ 🎸 Lead guitar · Kirk        Soon    │  (phase 2)
                             └──────────────────────────────────────┘
                                    │ tap Drums
                                    ▼
                 Instruments page in EDIT MODE (V5)
                  │      ↺ edits auto-saved as a small local draft
                  │ ✓
                  ▼
                 Review & publish sheet (V8)
                  │ Publish
                  ▼
   Songsterr sign-in (once, in-app browser) ─▶ submit ─▶ Version 13 live
                  ─▶ local draft deleted ─▶ app reloads versions and shows v13 ✓
```

### V5: Edit mode, whole screen (portrait)

```
┌──────────────────────────────────────────────┐
│ ✕   Drums · Draft from v12        ↶   ↷   ✓  │ ← edit bar (swaps in for the lyrics toolbar)
├──────────────────────────────────────────────┤
│  ♩=118        5                 6            │
│  ─x───x───x───x───┼─x───x───x───x───┼─       │  hi-hat line
│  ─────────●───────┼─────────●───────┼─       │  snare
│  ─●───────────●───┼─●─────────[●]───┼─       │  kick   [ ] = changed (tertiary tint)
│           ▲                                  │  caret (accent)
│  bar 5  ▓▓▓▓▓▓▓▓▓▓▓▓░░░░  3 of 4 beats         │  bar fill meter
├──────────────────────────────────────────────┤
│                    ────                      │
│  ♩+   𝅝   𝅗𝅥   ♩   ♪   𝅘𝅥𝅯   𝅘𝅥𝅰  │  •   ³   𝄽       │  row A: current palette
│ (Notes)(Accents)(Dynamics)(Bar)(Repeats)(Text)│  row B: palette chips (drum-only set)
├──────────────────────────────────────────────┤
│    ( Crash )        ( Ride )       ( China )  │
│ ( HH ✕ )   ( Hi T ) ( Mid T )   ( Low T )     │  drum pad board laid out like a kit
│ ( Snare )                 ( Floor T )         │  (from the drummer's seat)
│ ( Pedal )   [ ═══════ Kick ═══════ ]  ( ● Rec )│
└──────────────────────────────────────────────┘
 Pad = mini staff with its notehead + full short name, 64dp+, never cut off.
```

Landscape: the score goes on the left 60% and tools + pads on the right 40%, with the same parts.

### V6: Pad variants (long-press) and the loupe

```
          ┌───────────────────────────────────┐
          │  Snare     Rim shot    Side stick  │
          │   ●           ◇            ✕       │   ← slide your finger and lift to pick
          └──────────────▲────────────────────┘
                     ( Snare )  long-press

  Hi-hat → Closed ✕ · Half ⊘ · Open ⊗ · Pedal ✕(below)      (E16 articulations)
  Crash  → Crash ✕ · Choke ✕' ;  Ride → Ride · Edge · Bell ◇ · Choke

Dragging a note up or down in the score:
   ┌──────────────────────┐
   │ ─────────────────    │
   │ ── + ●  Floor Tom ── │   ← loupe shows the NAME of the kit piece at that line
   │ ─────────────────    │
   └──────────────────────┘
```

### V7: Selection and quick actions

```
  ┌──────────────────────────────────────────────────────┐
  │ 🗑   ⧉ Copy   ✂ Cut   📋 Paste   ×2 Repeat   ⇄ Swap ▾ │   floating, follows the selection
  └──────────────────────────────────────────────────────┘
     ─x──x──[x──x──x──x──x──x]──x──
             └── long-press + drag
  Swap ▾ → "Hi-hat → Ride", "Snare → Rim shot" … changes every note of that piece in the range
```

### V8: Review & publish

```
┌──────────────────────────────────────────────┐
│ Publish to Songsterr                         │
│ Drums · 6 bars changed · +41 −12 notes       │
│ ┌ bar 17 ─────────────── before ▸ after ──┐ │
│ │ x x x x │ x x x x   ▸   x x x x │ x xxx x │ │  swipe through the changed bars
│ └─────────────────────────────────────────┘ │
│ What changed?  [ Fixed chorus fills_______ ] │
│ Signed in as  trai ▾                         │
│ ( Keep editing )               ( Publish )   │
└──────────────────────────────────────────────┘
```

### V9: Recording (phase 4)

```
│ ● REC  bars 5–8 · count-in 1 bar · grid 1/16 · against: Original ▾ │
│ Play the pads or your e-kit. Notes snap to the grid; ↶ undoes the take. │
```

---

## 4. Prompt A: Instruments card + sheet cleanup + Edit button (copy-ready)

**Goal:** Make the Instruments card a clean single line, remove three helper texts, and turn the sheet's back button into an Edit button.

**Scope:** `presentation/components/tabs/TabPracticePanel.kt` only (E1-E6).

**Current behaviour:** The card has two stacked text lines and an "Instruments ›" label (E1). The sheet pill has a second line, "Tap for other tabs of this song" (E2). The Listings and History pages have helper texts (E3, E4). The header's first button is a back arrow that closes the sheet or goes back to parts (E5). The pill and History are already toggles back to parts (E6).

**Spec:**
- Card (E1): one `Row` with `InstrumentIcon` · part name (`titleSmall`, onBackground) · `" · "` + person + `" · N muted"` (`bodySmall`, onBackground at 60%), both in one `Text` with `AnnotatedString`, `maxLines = 1`, ellipsis · `Icons.Rounded.ChevronRight`. Keep the 58dp height, the click target and the colours. Remove the "Instruments ›" text.
- Pill (E2): delete the subtitle `Text`. When `controller.source?.revisionId != null`, add `" · older version"` to the title line.
- Delete the helper `Text` blocks at E3 and E4. Keep the page titles.
- Header (E5): replace `ArrowBack` with `Icons.Rounded.Edit`, `description = "Edit this tab"`, `active = editMode`. Tap → `page = InstrumentsPage.EDIT` (the new page from prompt C, step C3). Until C3 lands, Edit opens a "Coming soon" `SheetMessage`. The sheet closes by swipe or scrim. On LISTINGS/HISTORY, the user goes back by tapping the lit pill or History again (E6).

**Motion:** No new motion. The page swap keeps the current instant behaviour. (Animating the page change is prompt C, step C3.)

**Accessibility:** The Edit button stays 52dp. The card keeps one merged semantics node: "Drums, Lars Ulrich, 1 muted. Open instruments".

**Don't:** Change the colours, the tile sizes or `InstrumentPicker`.

**Acceptance criteria**
- [ ] The card is one line at every font scale up to 1.3× (ellipsis, no wrap).
- [ ] None of the three strings appears anywhere (search for them).
- [ ] Edit appears in place of the back arrow. Swipe down still closes the sheet. Tapping the lit pill or History returns to parts.
- [ ] `gradlew.bat assembleDebug` passes.

### Plan A
1. Card to one line: `TabPracticePanel.kt:242-281` (E1). Use `titleSmall`/`bodySmall` roles. Done when it renders on one line.
2. Remove the pill subtitle and add the older-version suffix: `:713-745` (E2). Done when the pill is one line.
3. Delete the helper `Text` blocks under the two page titles (E3 at `:816-819`, E4 at `:852-855`). Done when a search finds 0 matches.
4. Back → Edit in the header at `:706-711` (E5). Add `EDIT` to `InstrumentsPage` with a placeholder. Done when tapping Edit shows the placeholder page.
5. Verify: build, then check light/dark and album-art colours, TalkBack reading of the card, and font scale 1.3×.

---

## 5. Prompt B: Tab timing works like Adjust sync (copy-ready)

**Goal:** Tab timing becomes a panel that slides in above the play / slider / skip row. It opens from "Adjust tab timing" in the options menu and closes from "Hide tab timing", by dragging the panel down, or by leaving the Instruments page.

**Scope:** `TabPracticePanel.kt` (`TabOptionsSection`, E7), `LyricsSheet.kt` (controls column, E8/E11), `TabPracticeController.kt` (state, E18), `strings_player.xml` (E10).

**Current behaviour:** Tab timing is a full inline block inside the options menu (E7). Lyrics sync is an `AnimatedVisibility` above the play row, toggled from the menu by a `ListItem` that first dismisses the sheet (E8, E9). Dragging down on the controls column hides all the controls (E11).

**Spec:**
- State: `var timingPanelVisible by mutableStateOf(false)` on `TabPracticeController` (E18), so both the menu and the sheet read it. It resets to false when the Instruments page closes.
- Menu (E7): replace the inline block with a `ListItem` styled like E9: `Icons.Rounded.Timer` (or `Tune`, to match), headline "Adjust tab timing" / "Hide tab timing" (new strings `tab_timing_adjust`, `tab_timing_hide`), trailing text with the current offset when it isn't 0 ("+120 ms"). On tap: dismiss the sheet with its animation, then toggle the panel (same order as E9).
- Panel (in `LyricsSheet.kt`, right before the Playback Controls Row, next to E8): `visible = tabPractice?.timingPanelVisible == true`. Content: a 32×4 handle (`onBackground` at 25%) · a `Row` with "Tab timing" (`titleSmall`) and the offset (`labelLarge`, accent, `"+120 ms"`, "In sync" at 0) · the existing `LyricsSyncControls(offsetMillis = controller.syncOffsetMs, onOffsetChange = { controller.syncOffsetMs = it } …)`.
- If lyrics sync and tab timing are both open, tab timing sits above lyrics sync. Only one can be open at a time: opening one closes the other.
- Gesture: a vertical drag on the panel **consumes** its events (so the column-wide hide at E11 doesn't also fire). It follows the finger with `graphicsLayer.translationY` and alpha. Past 48dp (or a fast fling) it hides; otherwise it springs back.

**Motion:**
- Enter: `expandVertically` + `fadeIn`, `tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)`.
- Exit: `shrinkVertically` + `fadeOut`, `tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)` (200 is 67% of 300).
- Drag: the panel follows the finger; release uses `MaterialTheme.motionScheme.fastSpatialSpec()` (E25) to settle at 0 or continues into the exit.
- Interruption: toggling during an animation reverses from the current value. Re-grabbing during the spring-back cancels the spring and follows the finger from where it is.
- Reduced motion: at animator scale 0 it appears and disappears instantly (Compose follows the system duration scale). The drag still works.

**Accessibility:** The timing buttons are 44dp+ inside the 52dp row. The panel gets `paneTitle = "Tab timing"`. The offset change is announced as a live region ("Tab 120 milliseconds later"). The handle has a custom action, "Hide tab timing".

**Don't:** Change `LyricsSyncControls` or the lyrics sync panel. Don't use hard-coded easing.

**Acceptance criteria**
- [ ] The menu shows "Adjust tab timing" and switches to "Hide tab timing" while the panel is open. The inline control is gone from the menu.
- [ ] The panel opens above the play row. Dragging it down hides it without hiding the whole controls column.
- [ ] Offset changes still move the cursor live (`syncOffsetMs`).
- [ ] Leaving the Instruments page closes the panel.
- [ ] Animator scale 0× / 0.5× / 1×, light/dark and album-art colours all checked.
- [ ] `gradlew.bat assembleDebug` passes.

### Plan B
1. Add `timingPanelVisible` and reset it on page close: `TabPracticeController.kt` (E18). Done when the state exists and resets.
2. Add the strings `tab_timing_adjust` / `tab_timing_hide` in `strings_player.xml` (E10).
3. Swap the inline block for the toggle `ListItem`: `TabPracticePanel.kt:1205-1221` (E7), following E9's dismiss-then-toggle. Done when the menu item toggles the state.
4. Build a `TabTimingPanel` composable (handle + header + `LyricsSyncControls`) in `TabPracticePanel.kt`.
5. Place it in `LyricsSheet.kt` before the play row, with the token-based enter/exit (E8, E13). Make it mutually exclusive with `showSyncControls`.
6. Add the drag-to-hide with event consumption (E11), the spring-back via `motionScheme` (E25), and the a11y pane title, live region and custom action.
7. Verify: everything in the acceptance list, plus the regression check below.

**Risks:** the column-wide drag at E11 could steal the gesture, so test drag-down on the panel and drag-down below it. The panel adds height to the bottom column, so check compact controls and landscape.

---

## 6. Prompt C: Edit mode, drums first (copy-ready)

**Goal:** From the Instruments sheet, Edit makes a copy of the version on screen. The user picks a part. The Instruments page switches to an editor: the score stays at the top and the tools area becomes edit tools (a drum pad board when drums is selected). On ✓ the user reviews a diff and publishes it to Songsterr as the next version, then the local draft is deleted.

**Scope (new files):** `data/songsterr/edit/` (`TabDraft`, `DraftStore`, `DrumEditOps`, `TabExport`), `presentation/components/tabs/edit/` (`TabEditController`, `EditTopBar`, `EditToolsPanel`, `DrumPadBoard`, `PadVariantPopup`, `SelectionActionBar`, `NoteLoupe`, `BarFillMeter`, `ReviewPublishSheet`, `SongsterrSignInSheet`).
**Touched:** `TabPracticePanel.kt` (EDIT page, panel swap; E5, E24), `TabNotationView.kt` (taps in edit mode; E19), `LyricsSheet.kt` (toolbar ↔ edit bar), `SongsterrClient.kt` (fresh revisions after publish; E17).

**Current behaviour:** Songsterr is read-only (E14). Parsing keeps only the modelled fields (E15). Drum notes are `fret = articulation id`, `string = staff position` (E16). Taps on the score seek (E19). The layout knows every beat's x (E20). Notation can be auditioned (E21). E-kits connect over MIDI (E22). There's no Guitar Pro writer (E23).

**Spec, data:**
- **Lossless drafts.** Keep the **raw part `JsonObject`** from the CDN for every part. Edits patch the raw JSON. After each edit, re-derive the view with `SongsterrJson.trackFrom(raw)` → `TabParser.toRenderModel` (E15, E16). Never serialise from `RevisionTrack` (that would drop unknown fields).
- `TabDraft(songId, baseRevisionId, baseAuthor, parts: Map<index, JsonObject>, dirty: Set<index>, message)`. Undo/redo is a list of inverse ops (cap 200).
- `DraftStore`: one small file `filesDir/tab_drafts/{songId}_{baseRev}.json` holding **only the dirty parts**. It autosaves 1 s after the last edit, offers to restore if the app was killed, and is **deleted after a successful publish** or when the user discards.
- Drum ops (`DrumEditOps`): toggle a note (articulation at the caret beat, added to the chord or removed), set the duration, add a rest, dot, tuplet 3, tie, accent / ghost / staccato, velocity (dynamics), insert/delete bar, time signature, repeat start/end ×N, alternate ending, marker, beat text. **Every tool maps to a field the Songsterr model already has** (RevisionModels). Bars are kept full: a shorter note splits the rest after it, and a longer note eats into later rests or gets clipped with a warning.

**Spec, UI (V4-V8):**
- **EDIT page** in the Instruments sheet (V4): "Based on Version N · Tab by X", a note that edits become the next version, and the parts list. Drums rows say "Edit ›". Other parts say "Soon" (disabled, shown at 38% alpha).
- **Edit bar** (V5) replaces the lyrics toolbar: ✕ (discard or keep the draft, as a dialog) · title "Drums · Draft from v12" · ↶ ↷ · ✓ (opens Review). It uses the same height as the toolbar.
- **Tools area** (V5) replaces `TabPracticePanel` content when `editMode`. Row A shows the current palette. Row B shows the palette chips **Notes · Accents · Dynamics · Bar · Repeats · Text**. Clef, key and accidental tools are never shown for drums. Then the **DrumPadBoard**.
- **DrumPadBoard**: pads come from `TabParser.drumArticulations` (E16), grouped as cymbals / hats / toms / snare / kick and arranged like a kit. Each pad shows a mini staff with the notehead at its `staffPos` and a full short name ("Kick", "Snare", "Floor T"). Pads are at least 64dp with `secondaryContainer` / `onSecondaryContainer`.
  - Tap: toggles that piece at the caret, auditions it (E21), gives a haptic, and moves the caret forward by the current duration. If **Chord** is on (or the second pad is tapped within 250 ms), the caret stays so hand and foot land together.
  - Long-press opens **PadVariantPopup** (V6).
- **Score in edit mode** (E19, E20): a tap places the caret at the nearest `PlacedSlot`. A vertical drag on a note moves it to another staff line, with the **NoteLoupe** naming the piece (V6). Long-press + drag selects a range and shows the **SelectionActionBar** (V7: delete, copy, cut, paste, repeat ×N, **Swap piece**). Changed notes are tinted `tertiary`.
- **BarFillMeter** under the caret's bar.
- **Play while editing:** the existing play button plays the draft (synth) or plays it against the original song using the tab timing from prompt B.
- **ReviewPublishSheet** (V8): a summary, before/after mini scores of the changed bars, a "What changed?" field, and the Songsterr account.
- **SongsterrSignInSheet:** an in-app browser for Songsterr sign-in, used once (cookies kept by WebView). On Publish: `TabExport` builds the file → it's submitted through Songsterr's own new-version / upload page for this song → on success, `fetchRevisions` is called fresh (bypassing E17's 24 h cache) → `chooseRevision(new)` (E18) → `DraftStore.delete`.

**Motion:**
- Toolbar ↔ edit bar and practice panel ↔ edit tools: `AnimatedContent` keyed on `editMode`. Enter is `fadeIn` + `slideInVertically(it / 8)` with `tween(DurationMedium2, EmphasizedDecelerate)`. Exit is `fadeOut` with `tween(DurationShort4, EmphasizedAccelerate)`. Both swaps share one duration (they move together).
- Sheet page change (PARTS ↔ EDIT): a fade-through with the same tokens. The outgoing page fades early by design.
- Caret x: `animateFloatAsState(motionScheme.fastSpatialSpec())` (E25), interruptible, continues from the current x.
- Pad press: `graphicsLayer` scale to 0.94 and back with `motionScheme.fastEffectsSpec()` (no overshoot). The note appearing in the score uses a 0→1 alpha with the effects spec.
- Variant popup and selection bar: enter `scaleIn(0.9)` + `fadeIn` with `tween(DurationShort4, EmphasizedDecelerate)`. Exit `fadeOut` with `tween(DurationShort3, EmphasizedAccelerate)`.
- The review and sign-in sheets are `ModalBottomSheet`s that dismiss with `sheetState.hide()` then `onDismiss` (same pattern as E5).
- Reduced motion: at animator scale 0, everything snaps. The caret jumps. Haptics stay.

**Accessibility:** pads ≥ 64dp with labels like "Snare. Double-tap to add at bar 5, beat 2". The caret position is announced on move. The selection bar actions have labels. Undo/redo have custom actions on the score. Contrast: pads use container roles, the caret uses the accent with a 2dp outline in dark mode.

**Don't:** Edit guitar or bass in phase 1 (rows say "Soon"). Show pitched-only tools on drums. Serialise from the lossy model. Hard-code hex, dp corners or sp in new files (use `MaterialTheme.shapes` and type roles). Add more to `TabPracticeController` than an `editMode` flag and a hook (E18 is already 1450 lines).

**Acceptance criteria**
- [ ] Edit → EDIT page → Drums opens the editor. The pads replace the practice tiles and the edit bar replaces the toolbar.
- [ ] Tapping pads adds notes at the caret, auditions them and moves the caret forward. Chord mode stacks notes. Long-press shows variants.
- [ ] Bars always add up (meter + automatic rests). Undo and redo walk the whole history.
- [ ] Killing the app mid-edit and reopening offers to restore the draft.
- [ ] Round trip: with no edits, the exported JSON equals the original CDN JSON (byte-equal after normalising).
- [ ] Review shows the changed bars. Publish creates the next version on Songsterr, the app switches to it and the draft file is gone.
- [ ] No clef, key or accidental tools appear on drums.
- [ ] Animator scale 0× / 0.5×, light/dark, album-art colours, TalkBack and landscape all checked.
- [ ] `gradlew.bat assembleDebug` passes.

### Plan C (phased)

**Phase 0: Spike (do this first, about a day)**
0.1 By hand, in a browser: sign in to Songsterr, open a song, find the "new version" / upload path, and note **which file types it accepts** (Guitar Pro .gp / .gp5 / .gpx, or others) and whether it can target an existing song. This decides `TabExport`. → Done when it's written down in the project.
0.2 Prove lossless round trip: download a drum part's raw JSON, `trackFrom` → patch nothing → re-emit raw → diff = 0 (E15). → Done when a unit test passes.

**Phase 1: Editor core (drums)**
1. `TabDraft` + `DraftStore` (raw JSON, dirty parts, autosave, delete). New `data/songsterr/edit/`. Done when there are tests for save, restore and delete.
2. `DrumEditOps` with bar-filling rests and inverse ops for undo (fields from RevisionModels, E16). Done when there are unit tests for insert/delete/duration/tie/tuplet and bar sums.
3. EDIT page in `InstrumentsSheet` (replaces A's placeholder; E5) with the fade-through tokens. Done when picking Drums sets `editMode` + the active part.
4. `TabEditController` (caret, selection, palette, duration, chord, undo) that re-renders through `TabParser.toRenderModel` (E16). Done when an edit updates the score within one frame.
5. `EditTopBar` + swap with the lyrics toolbar (`LyricsSheet.kt`) using `AnimatedContent` tokens.
6. `EditToolsPanel` (rows A and B, drum-only chips) swapped into the tools area (E24) with the same tokens.
7. `DrumPadBoard` (kit layout, mini staff, audition via E21, haptics, chord window). Done when every articulation in E16 is reachable (direct or variant).
8. Score edit gestures in `TabNotationView` (tap → caret via E20; keep seek when not editing; E19).
9. `BarFillMeter`, changed-note tint, play-the-draft.

**Phase 2: Pro tools**
10. `PadVariantPopup` (long-press, slide to pick).
11. Range select + `SelectionActionBar` (delete, copy, cut, paste, repeat ×N, Swap piece).
12. `NoteLoupe` for dragging notes vertically, naming the piece.
13. Palettes: Accents, Dynamics, Bar (time signature, insert/delete), Repeats (start/end, ×N, endings, marker), Text.

**Phase 3: Review & publish**
14. `ReviewPublishSheet` (diff of dirty parts by bar, message field).
15. `SongsterrSignInSheet` (WebView, cookie persistence, "signed in as").
16. `TabExport` in the format chosen in 0.1 (likely a Guitar Pro writer, since none exists, E23), built from the draft's raw parts plus the untouched parts of the base version.
17. Submit through Songsterr's page with the WebView session. On success: fresh `fetchRevisions` (skip the cache, E17) → `chooseRevision` (E18) → delete the draft. On failure: keep the draft and show the error with Retry.

**Phase 4: Better-than extras**
18. Record mode (V9): pads or e-kit (E22) during playback with count-in, quantise to the chosen grid, each take one undo step.
19. Landscape layout (score 60 / tools 40).
20. Guitar/bass editing on the same core (fret pad + string lanes), a separate prompt later.

**Verification (each phase):** build command; enter/exit edit mode; interrupt the swap mid-animation; back gesture in the editor (asks to keep the draft); animator scale 0× and 0.5×; light, dark and album-art colours; TalkBack through pads and caret; the round-trip test; kill and restore.

**Risks**
- **Songsterr publishing** depends on their site's flow (E14: no API). It can change, and it needs the user's account. Keep the draft until success, and keep "Export file" as a fallback.
- **Lossy parse** (E15): any write path that goes through `RevisionTrack` will silently drop data. Code review rule: only patch raw JSON.
- **TabPracticeController size** (E18): put edit logic in `TabEditController`.
- **Tap conflicts** in `TabNotationView` (E19): taps must not seek while editing.
- **Practice panel motion** uses hard-coded springs/tweens (E12). The edit swap uses tokens, so the two may feel slightly different (possible follow-up to align them).

---

## 7. Assumptions (not verified; the step each one affects)
- Songsterr lets a signed-in user submit a new version of an existing song through its site, as far as I know. To be confirmed in step 0.1 (affects 15-17).
- The accepted upload format is probably Guitar Pro, so a writer is needed (E23). Confirmed in step 0.1 (affects 16).
- Drum rolls/tremolo: not visible in RevisionModels for drums, so left out of phase 1 palettes (affects 13).
- "Older version" moves into the pill title as a suffix rather than being dropped entirely (affects A2).
- Closing the Instruments sheet by swipe or scrim is enough once the back arrow becomes Edit (affects A4).
- Tab timing and lyrics sync are mutually exclusive above the play row (affects B5).
