# PixelPlayer — 4 verified prompts + plans (2026-10-01)

Queue de-dupe · All-in-one queue prompt · Instrument sounds download + styles · Face-to-face on the tabs page

Paths are relative to `app/src/main/java/com/theveloper/pixelplay/`.

---

## 1. Evidence

**Queue (prompt A)**
- E1: `presentation/viewmodel/PlaybackDispatchStateHolder.kt:1048` – `addSongToQueue` builds a new item and `controller.addMediaItem(mediaItem)`. It never checks whether the song is already queued.
- E2: `PlaybackDispatchStateHolder.kt:1058-1073` – `addSongNextToQueue` does `controller.addMediaItem(insertionIndex, mediaItem)` at `currentMediaItemIndex + 1`. It never checks for an existing copy, so a queued song gets a second copy.
- E3: `PlaybackDispatchStateHolder.kt:1080-1100` – `addSongSoon` has the same insert-only behaviour, through `playSoonInsertionIndex(...)`.
- E4: `presentation/viewmodel/PlayerViewModel.kt:800-810` – `performSongQuickAction`: `NEXT -> addSongNextToQueue`, `SOON -> addSongSoon`, `QUEUE -> addSongToQueue`. Every song action sheet goes through this.
- E5: `presentation/components/QueueBottomSheet.kt:1031,1157,1243` – queue rows use `onMoreOptionsClick = { onSongInfoClick(song) }`, which opens the same action sheet (E4).
- E6: `PlayerViewModel.kt:2330-2346` – `reorderQueueItem` re-tags the moved item with `QueueEntryMetadata.read(moved).copy(pinned = true).attach(moved)`, then calls `controller.moveMediaItem(fromIndex, toIndex)`. This is the existing safe move.
- E7: `PlaybackDispatchStateHolder.kt:1055` – "Queue UI is synced via onTimelineChanged listener", so the UI follows controller changes.

**Queue prompt (prompt B)**
- E8: `QueueBottomSheet.kt:1856-1940` – `QueueMixPromptField`: hint `"Song or artist…"`, send icon, ✕ "Stop steering the mix".
- E9: `QueueBottomSheet.kt:1500-1504` – `onSubmitPrompt = { viewModel.steerMixWithPrompt(text) }`.
- E10: `PlayerViewModel.kt:3058-3085` – `steerMixWithPrompt` → `dailyMixStateHolder.resolveMixPrompt(query)`. When nothing matches it shows the toast `Couldn't find "$query"`. Otherwise it runs `steerMixTowards(matches)` and starts SMART or calls `refreshFuture(keepAutomatic = 1)`.
- E11: `data/AdaptiveMix.kt:468-495` – `resolvePrompt` only ranks songs by **artist/title text** (`artist == needle`, `title == needle`, `contains`), with YouTube search as the fallback. It doesn't understand tempo, energy, mood, genre, era or country.
- E12: `data/AdaptiveMix.kt:451-454` – `steerTowards(songs)` keeps `songs.take(MAX_PROMPT_SEEDS)`. `MAX_PROMPT_SEEDS = 5` (`:954`).
- E13: `presentation/library/MusicVibeFilters.kt:21-37` – `VibeFilter` already has `genres`, `words`, `bpm`, `energy`, `valence`, `minDanceability`, `minAcousticness`, `minInstrumentalness`, `prefersMinor` and `years`.
- E14: `MusicVibeFilters.kt:189-198` – `aliases` map loose words to presets (`"gym"→workout`, `"sad"→melancholy`, `"country"→folk`, `"energy"→hype`, …).
- E15: `MusicVibeFilters.kt:304-333` – `customScore` already splits a query into tokens, matches preset words, tags, title/album and artist, and decades (`(?:19|20)?(\d)0s`). It's only used for custom vibe filters, never by the queue prompt.
- E16: `data/ai/GeminiNanoClient.kt:57-86` – `generateContent` is a **mock**: `simulateAicoreExecution` returns random song IDs from the prompt. No real Gemini Nano call is wired up.

**Instrument sounds (prompt C)**
- E17: `data/soundfont/SoundFontStore.kt:32-34` – `DOWNLOAD_URL = "https://github.com/KinsBand/PixelPlayer/releases/download/soundfont-generaluser-gs-2.0.3/GeneralUser-GS.sf2"`, with SHA-256 and `SIZE_BYTES = 32_319_396`.
- E18: checked live today: `github.com/KinsBand/PixelPlayer/releases/tag/soundfont-generaluser-gs-2.0.3` → **404**, and the repo's Releases page says "There aren't any releases here". The download ends in `State.Failed("Server replied 404")` (`SoundFontStore.kt:~127,158`).
- E19: `presentation/components/tabs/TabPracticePanel.kt:1125-1150` – a separate "Real guitar & bass strings" Switch row (`controller.toggleRealStrings()`), shown when `!controller.usesSampledSounds`.
- E20: `TabPracticePanel.kt:1261-1314` – `InstrumentSoundsOption`: "Real instrument sounds". It downloads on tap, then becomes a Switch (`toggleSampledSounds`).
- E21: `presentation/components/tabs/TabPracticeController.kt:172,178,700-718` – prefs `real_strings` and `sampled_sounds` (both default true). `usesSampledSounds = sampledSounds && SoundFontStore.fontOrNull() != null`.
- E22: `TabPracticeController.kt:1147-1165` – the Synth build picks SoundFont samples (`StringSynthPlayer.createSampled`), then modelled strings (`StringSynthPlayer.create`), then plain MIDI (`buildFile(false)`).
- E23: `data/soundfont/SampledScoreEngine.kt:195-207` – `programFor(tone)` maps string tones to GM programs 24-36. `SoundFontSynth.kt:78,83` handles MIDI `0xC0` with `programChange(ch, program)`, and `startNote(bank, program, …)` is at `:181`.
- E24: `data/soundfont/SoundFont.kt:51-53` – `preset(bank, program)` falls back to GM bank 0. The full GM bank is loaded, so square lead, saw lead, synth bass and so on are available.

**Face-to-face on tabs (prompt D)**
- E25: `presentation/components/LyricsSheet.kt:754-761` – `splitActive = performanceView == PerformanceView.Lyrics && (faceToFaceHold || …)`, so face-to-face is **lyrics-page only**.
- E26: `LyricsSheet.kt:2217-2240` – the "Show Controls" `FilledIconButton` (48dp, `KeyboardArrowUp`) appears while `immersiveMode && !splitActive`. It brings the controls deck back.
- E27: `LyricsSheet.kt:2244-2280` – the face-to-face shortcut: `canOfferSplit` needs `performanceView == PerformanceView.Lyrics`. It's a `FilledTonalIconButton` 36dp with `Icons.Rounded.ScreenRotation`, "Face-to-face lyrics", placed `.offset(x = overlayShiftX + 52.dp).padding(bottom = 38.dp)` (just right of the arrow). Enter/exit: `fadeIn() + scaleIn(0.6f) + slideInVertically`.
- E28: `LyricsSheet.kt:729-736` – `exitFaceToFace()`: haptic, `faceToFaceHold = false`, `showControlsNow()`.
- E29: `LyricsSheet.kt:1453-1525` – the Instruments page shows `InstrumentsPerformanceView(controller = practice, …)`. In **Lyrics + Tab** mode (`lyricsWithTab`) it adds `lyricsPaneContent` above it. The split animates with `MotionTokens.DurationMedium4 / EmphasizedDecelerate` in, `DurationMedium1 / EmphasizedAccelerate` out, and `snap()` for reduced motion.
- E30: `LyricsSheet.kt:2017-2022` – the lyrics split view enters with `tween(420, LinearOutSlowInEasing)` and exits with `tween(220, FastOutLinearInEasing)`. These are not MotionTokens.
- E31: `presentation/components/lyrics/SplitFaceLyricsView.kt:5-20` – portrait layout: content turned 180° for the top person, a centre divider with play/pause, upright content for the bottom person, and a mini song bar at each far end. In landscape, the left half is the right half turned 180°.
- E32: `ui/theme/MotionTokens.kt` – `EmphasizedDecelerate`, `EmphasizedAccelerate`, `Emphasized`, `DurationMedium1=250`, `DurationMedium4=400`, `DurationShort4=200`.

Not read: `SplitFaceLyricsView` beyond its header, `InstrumentsPerformanceView` internals, `ScoreRenderer`. The plans include steps to confirm them.

---

## Prompt A — Play next / Add to queue never duplicate

```
Goal: Pressing Play next, Play soon or Add to queue on a song that's already in Up next moves that
copy instead of adding a second one.

Scope: PlaybackDispatchStateHolder.addSongNextToQueue / addSongSoon / addSongToQueue (E1-E3).
Queue rows reach these through performSongQuickAction (E4, E5).

Current behaviour: all three always call controller.addMediaItem, so a queued song shows up twice:
once at the new spot and once at its old spot (E1-E3).

Spec:
- Add a private helper findUpcomingIndex(controller, songId): the first index > currentMediaItemIndex
  whose mediaId == songId, or -1. Only look ahead of the current song: history and the playing song
  are left alone.
- Play next: if found, re-tag that item as tier PRIORITY, pinned = true (same re-tag pattern as
  reorderQueueItem, E6), then moveMediaItem(found, current + 1). Otherwise insert as today.
- Play soon: if found, move it to playSoonInsertionIndex(...). Work that index out with the found
  item ignored, so the move doesn't count itself as a priority item.
- Add to queue: if found, re-tag as tier SESSION, pinned = true, and move it to mediaItemCount - 1.
  Otherwise append as today.
- If the target index equals the found index, only re-tag it (no move).
- Remote/cast playback: keep whatever path runs today. No new behaviour there.
- The UI follows through onTimelineChanged (E7). No extra UI state.
- Toast: "Moved to play next" / "Moved to end of queue" when a move happened. Keep the existing
  toasts when it was inserted.
- Continuous mix: keep calling continuousMixRuntime.noteQueuedByUser(song) as today (PlayerViewModel
  3256-3280).

Motion: none new. The queue list already reacts to timeline changes. Rows should use
Modifier.animateItem() so a moved row slides instead of jumping (confirm in QueueBottomSheet).

Don't: change friends' addFriendSongNext semantics beyond de-dupe; touch history items; add a new
queue model.

Acceptance criteria:
- [ ] Song X is 6th in Up next → Play next → X is right after the current song, and the queue has one X.
- [ ] Same with Play soon → X lands at its Play-soon slot, still one copy.
- [ ] X is 2nd → Add to queue → X is last, one copy.
- [ ] X not queued → all three behave exactly as before.
- [ ] X is the playing song → Play next still inserts a replay copy (unchanged).
- [ ] A running Smart Mix doesn't swap out the moved song (pinned = true).
- [ ] gradlew assembleDebug passes.
```

### Plan A
1. Add `findUpcomingIndex(controller, songId)` – `PlaybackDispatchStateHolder.kt` (E2) – done when: it returns -1 for history items or the current item.
2. Add a private `moveExisting(controller, from, to, tier)` that re-tags with `QueueEntryMetadata(...).attach` / `copy(pinned = true)` like E6, then calls `moveMediaItem` – same file – done when: it's a no-op move when `from == to`.
3. Use it in `addSongNextToQueue` (E2), with the target `current + 1` – done when: AC1 passes.
4. Use it in `addSongSoon` (E3), working out the index with the found item excluded – done when: AC2 passes.
5. Use it in `addSongToQueue` (E1), with the target `mediaItemCount - 1` – done when: AC3 passes.
6. Return a moved/inserted result and emit the "Moved…" toast in `PlayerViewModel.addSongNextToQueue / addSongSoon / addSongToQueue` (3256-3280) – done when: the toast text matches the spec.
7. Confirm queue rows use `animateItem()` in `QueueBottomSheet.kt`. Add it if missing – done when: the moved row slides.
8. **Verify**: build. Run AC1-AC6 manually, with and without a running mix, at animator scale 0x and 1x.

**Risks:** if Play soon's index is worked out with the found item still counted, it lands one slot off (E3). Moving a non-pinned automatic mix item and re-tagging it takes it out of the mix's re-plan pool. That's intended, but check that `MixQueueMetadata.automatic` flags are cleared (PlayerViewModel 3542 removes automatic items by flag).

---

## Prompt B — All-in-one queue prompt (tempo, energy, mood, genre, era, country, artist, "songs like")

```
Goal: The queue options prompt box understands plain-English requests that combine anything, e.g.
"upbeat 80s rock like Queen", "slow sad acoustic", "fast Aussie hip hop", "songs like Mr Brightside
but chiller". It builds the mix from all the parts together, not a single mood.

Scope: QueueMixPromptField hint (E8), PlayerViewModel.steerMixWithPrompt (E10), a new pure parser
data/MixPromptParser.kt, AdaptiveMix.resolvePrompt + steer (E11, E12). Reuse MusicVibeFilters
scoring (E13-E15).

Current behaviour: resolvePrompt only matches artist/title text (E11). "upbeat 80s rock" finds
nothing → toast "Couldn't find …" (E10). Only up to 5 seed songs steer the mix (E12).

Spec:
- MixPromptParser.parse(text): PromptIntent, pure Kotlin, on-device, no network:
  - tempo: "slow/chill/mellow" / "mid-tempo" / "fast/upbeat/uptempo" / an explicit number "120 bpm"
    → bpm range
  - energy: "low/calm/soft" ↔ "high/hype/energetic/heavy" → energy range
  - mood: preset words + E14 aliases (sad, happy, angry, romantic…) → valence / prefersMinor
  - genre: any word or phrase that matches preset genres (E13 lists) or song genre tags
  - era: decades ("80s", "1990s", "2000s"), "old/oldies/retro" vs "new/recent", explicit years
    → years range (reuse the E15 decade regex)
  - country/region: words like "aussie/australian, uk/british, k-pop/korean, latin, french…"
    → genre/tag words. Use song metadata only when the tags have it. No new data source.
  - artists: "like <X>", "by <X>", or tokens that match a library artist (via resolvePrompt's
    library list, E11)
  - songs: "like <song title>", "songs like <title>" → seed songs (resolvePrompt title match)
  - modifiers: "but chiller / but faster / more X / less Y" shift the ranges from the seed songs'
    analysed features
  - leftover words stay as free-text words (customScore token matching, E15)
- Scoring: build one combined VibeFilter-like profile from the intent (every field optional). Score
  candidates with the existing MusicVibeFilters scoring plus a bonus for artist/seed affinity. A
  song doesn't need to match every part. Score is the sum, so combined requests rank songs that
  match more parts higher.
- Seeds: steerMixTowards gets the top-scored songs. Raise the prompt seed cap for prompt steers
  only (MAX_PROMPT_SEEDS stays 5 for "More like this"). The mix keeps re-planning as today (E10).
- Online top-up: only when the library has too few matches. Search YouTube with the artist/song
  parts (as resolvePrompt does today), never with the vibe words.
- Gemini Nano: NOT used. GeminiNanoClient is a mock that returns random IDs (E16), so it would
  make results worse and slower. Leave a single hook (an interface PromptIntentProvider with the
  local parser as the only implementation) so a real on-device model can be dropped in later.
- UI: hint becomes "Tempo, mood, genre, era, artist…". After submit, the toast lists what was
  understood: "Smart Mix · upbeat · 80s · rock · like Queen". The active-prompt hint shows the full
  prompt (E8). Keep the ✕ (stop steering) and send buttons as they are.
- Nothing understood and no matches → keep "Couldn't find …".

Motion: none new. The field and toast already exist.

Accessibility: the hint text is the field's label for TalkBack, and the send/✕ content
descriptions stay (E8).

Don't: call any network AI; change the vibe buttons row; add new token/color files; hardcode sp.

Acceptance criteria:
- [ ] "Queen" still works exactly as today (artist path).
- [ ] "upbeat 80s rock" → mix of mostly 1980-89 rock with energy/bpm in the upbeat range; toast lists
      all three parts.
- [ ] "slow sad acoustic like Bon Iver" → combines tempo + mood + genre + artist.
- [ ] "songs like <a title in library>" seeds from that song.
- [ ] "but chiller" after a song prompt lowers energy compared with the same prompt without it.
- [ ] Works in airplane mode (library only).
- [ ] MixPromptParser unit tests cover each part + three combined prompts.
- [ ] gradlew assembleDebug passes.
```

### Plan B
1. Read `MusicVibeFilters.score(...)` / `buildMix(...)` and `AdaptiveMix.next` to confirm a filter can be scored without being a preset – (E13, E15) – done when: the entry point is named in a code comment.
2. Create `data/MixPromptParser.kt` with `PromptIntent` (all fields optional) and `parse()`, covering tempo, energy, mood (with the E14 aliases made `internal`), genre, era (E15 regex), country words, "like/by" phrases, "but/more/less" modifiers and leftovers – done when: unit tests pass.
3. Add `PromptIntent.toFilter()` → `VibeFilter` (E13) with `query` = leftover words – done when: tests show "upbeat 80s rock" sets bpm, energy, years and genres.
4. Add `AdaptiveMix.resolvePromptIntent(intent)`: library pool (same as E11), resolve artist/song seeds with the existing rank, score the pool with the filter + affinity, return the top N. YouTube top-up only for artist/song parts – done when: AC1-AC4 pass offline.
5. Add a separate prompt seed cap in `steerTowards` for prompt steers (E12) – done when: "More like this" still uses 5.
6. Point `DailyMixStateHolder.resolveMixPrompt` → parse + `resolvePromptIntent`, and return the understood labels as well – done when: the "Queen" path is unchanged.
7. In `steerMixWithPrompt` (E10), build the toast from the labels – done when: the toast matches the spec.
8. Add the `PromptIntentProvider` interface, with the local parser bound in Hilt as the only implementation – done when: nothing references `GeminiNanoClient`.
9. Change the `QueueMixPromptField` hint text (E8) – done when: TalkBack reads the new hint.
10. **Verify**: build. Run AC1-AC6 on device, including airplane mode. Unit tests pass.

**Risks:** songs with thin metadata (streaming) have no bpm/energy, so they rank low on tempo prompts. That's acceptable, and the genre/artist parts still match. Country words only work where tags carry them. "rock" as a word inside titles (customScore word match, E15) can over-match, so keep the word bonus below the genre bonus.

---

## Prompt C — Instrument sounds: working download, one "Real instruments" option, and sound styles

```
Goal: The instrument sounds download works. The two toggles become one "Instrument sounds" option.
After downloading, the user picks a sound style: Real, 8-bit, Synth, or Modelled strings.

Scope: SoundFontStore (E17, E18), TabPracticePanel instrument rows (E19, E20),
TabPracticeController sound prefs + Synth build (E21, E22), SampledScoreEngine program mapping
(E23, E24).

Current behaviour: the download URL 404s (E18), so it always fails. Two rows: "Real instrument
sounds" (download, then Switch) and "Real guitar & bass strings" (Switch) (E19-E21).

Spec:
- Hosting: point DOWNLOAD_URL at Trai's own GitHub release (repo + tag supplied by Trai). Keep the
  SHA-256 + size check (E17). If the uploaded file differs, update SHA256/SIZE_BYTES from the real
  file.
- Remove the "Real guitar & bass strings" row (E19) and the Switch in InstrumentSoundsOption (E20).
- One row, "Instrument sounds":
  - Missing/Failed: shows the download action (as today). Failed shows the real reason + "Tap to try
    again" (as today).
  - Downloading: progress (as today).
  - Ready: subtitle "Downloaded · <style>". Below it, a SingleChoiceSegmentedButtonRow (M3) with Real
    · 8-bit · Synth · Strings. "Strings" = the modelled strings (no samples). Offer a "Delete
    (31 MB)" text button that calls SoundFontStore.delete.
  - Before download: the style choice is hidden, and Synth plays modelled strings (today's fallback,
    E22).
- Controller: replace the two booleans with enum SoundStyle { REAL, CHIP, SYNTH, STRINGS }, saved
  in prefs "sound_style". Migrate: sampled_sounds=false && real_strings=true → STRINGS;
  sampled_sounds=false && real_strings=false → STRINGS; otherwise → REAL.
  usesSampledSounds = style in {REAL, CHIP, SYNTH} && fontOrNull() != null.
- Styles map GM programs in SampledScoreEngine (E23, E24) through one programMap(style, program,
  isBass, isDrums):
  - REAL: unchanged (programFor + the file's own programs).
  - CHIP (8-bit): melodic → 80 (Square Lead), bass → 38 (Synth Bass 1), drums unchanged (GM kit).
  - SYNTH: melodic → 81 (Saw Lead), bass → 39 (Synth Bass 2), pads/keys → 89 (Warm Pad).
  - Apply it in both string voices (startNote program) and MIDI programChange (SoundFontSynth:83).
    Articulation (bends, slides) keeps working because it is pitch-based.
- Changing style while playing restarts the Synth the way onSampledSoundsReady does (E21).
- Theme: M3 color roles via the existing contentColor/accentColor params. New code uses
  MaterialTheme.typography + MaterialTheme.shapes. Don't extend the existing hardcoded
  18.dp/14.sp in this file.

Motion:
- Style row appears when Ready: AnimatedVisibility expandVertically + fadeIn,
  tween(DurationMedium2, EmphasizedDecelerate). Leaving (deleted): shrinkVertically + fadeOut,
  tween(DurationShort4, EmphasizedAccelerate).
- Segmented selection: the M3 default indicator animation.
- Interruption: a delete during the enter animation reverses from the current value.
- Reduced motion (animator scale 0): snap.

Accessibility: segment labels are text (no icon-only), each ≥48dp tall; the row's state is in its
semantics ("Instrument sounds, downloaded, style Real").

Don't: bundle the .sf2 in the APK; add new styles beyond these four; change ORIGINAL vs SYNTH
(TabSound).

Acceptance criteria:
- [ ] Fresh install: tap → download completes, hash passes, Ready.
- [ ] Only one instrument-sounds row; no "Real guitar & bass strings" switch.
- [ ] Each of Real/8-bit/Synth/Strings audibly changes guitar, bass and keys; drums stay a kit.
- [ ] Style switch mid-playback restarts at the same position.
- [ ] Existing users keep an equivalent sound after the update (migration).
- [ ] Delete → back to Missing; Synth falls back to modelled strings.
- [ ] gradlew assembleDebug passes.
```

### Plan C
0. **Manual (Trai):** create a release on your repo (e.g. tag `soundfont-generaluser-gs-2.0.3`) and upload `GeneralUser-GS.sf2`. Send me the repo/owner name.
1. Update `DOWNLOAD_URL` (and SHA/size if needed) – `SoundFontStore.kt:32-35` (E17) – done when: AC1 passes on device.
2. Add `enum SoundStyle` + the `soundStyle` state + pref migration; derive `usesSampledSounds` from it; remove `toggleRealStrings`/`toggleSampledSounds` – `TabPracticeController.kt:172-178,700-718` (E21) – done when: it compiles and the migration table holds.
3. Update the Synth build (E22): STRINGS → modelled path; REAL/CHIP/SYNTH → sampled path with the style passed to `StringSynthPlayer.createSampled` – `TabPracticeController.kt:1147-1165` – done when: each style reaches the right engine.
4. Thread the style into `SampledScoreEngine` and add `programMap(...)`, applied at the string-voice `startNote` (E23) and the MIDI program change (`SoundFontSynth.kt:78-83`) – done when: AC3 passes.
5. Replace the two rows with a single `InstrumentSoundsOption` holding the segmented style row + Delete – `TabPracticePanel.kt:1125-1150, 1261-1314` (E19, E20) – done when: AC2 and AC6 pass.
6. Add the enter/exit motion and reduced-motion snap from the spec – same file, tokens from E32 – done when: it checks out at animator scale 0x and 0.5x.
7. Add semantics/TalkBack labels – same file – done when: TalkBack reads the state.
8. **Verify**: build; AC1-AC6; light/dark + album-art colors on the panel.

**Risks:** `NotationAuditionPlayer.play(..., font)` (E-listing, `NotationAuditionPlayer.kt:50`) also uses the font. Decide whether demos follow the style (default: yes, through the same `programMap`). The removed prefs may be read elsewhere, so grep for `real_strings`/`sampled_sounds` before deleting. GitHub release download links redirect, and the existing redirect loop handles that (`SoundFontStore` hops ≤5).

---

## Prompt D — Face-to-face on the tabs page (Tab and Lyrics + Tab)

```
Goal: On the Instruments (tabs) page in immersive mode, the same face-to-face shortcut as on the
lyrics page (same icon and spot) opens a face-to-face split of whatever the tabs page is showing:
the tab, or Lyrics + Tab.

Scope: LyricsSheet.kt only: the shortcut (E27), split state (E25), and a new TabFaceToFaceView
next to SplitFaceLyricsView (E31).

Current behaviour: splitActive and canOfferSplit both need PerformanceView.Lyrics (E25, E27). The
tabs page only gets the "Show Controls" arrow when immersive (E26).

Spec:
- Shortcut: when performanceView == Instruments && immersiveMode && !tabSplitActive, show the same
  FilledTonalIconButton (36dp, Icons.Rounded.ScreenRotation, accentColor 0.22 container),
  same offset (overlayShiftX + 52.dp, bottom 38.dp), right of the Show Controls arrow (E26, E27).
  Content description "Face-to-face tab".
- State: a session-only rememberSaveable tabFaceToFace (not the lyrics SPLIT_FACE_VIEW pref). The
  lyrics face-to-face stays as it is.
- Layout (mirrors E31): one half is turned 180° (graphicsLayer rotationZ = 180f; pointer input
  follows the layer), the other upright. A centre divider has the play/pause button. Landscape
  uses left/right halves like E31.
  - Tab mode: each half is InstrumentsPerformanceView on the same controller (cursor, loop and
    speed shared).
  - Lyrics + Tab mode: each half is the existing Lyrics + Tab column (E29), so both people see the
    same thing.
  - Switching Lyrics + Tab on/off while in face-to-face updates both halves.
- Exit: same as lyrics (E28). Tapping the centre song pill/play area's "leave" action, or back,
  gives a haptic, sets tabFaceToFace = false and calls showControlsNow(). Leaving the Instruments
  page also exits.
- Colors: existing onBackgroundColor/accentColor, M3 roles. Shapes: MaterialTheme.shapes.

Motion:
- Shortcut enter/exit: reuse E27's specs exactly (consistency with the lyrics shortcut).
- Split enter: fadeIn + scaleIn(0.97f), tween(DurationMedium4, EmphasizedDecelerate).
  Exit: fadeOut + scaleOut(0.98f), tween(DurationMedium1, EmphasizedAccelerate). Same tokens as
  E29, not the hardcoded 420/220 of E30.
- Interruption: toggling during enter reverses from the current alpha/scale (AnimatedVisibility).
- Reduced motion: snap (same reducedMotion flag E29 uses).

Accessibility: shortcut 36dp visual inside a ≥48dp touch target; each half gets paneTitle
("Tab, facing you" / "Tab, facing across"); the centre play/pause keeps its label.

Don't: change the lyrics face-to-face or its pref; restyle E30's timings (follow-up); add a
setting.

Acceptance criteria:
- [ ] Tabs page, immersive: the rotation button sits right of the arrow, same as on lyrics.
- [ ] Tap → tab shown twice, top half upside-down, cursor moving in both.
- [ ] With Lyrics + Tab on → both halves show lyrics over tab.
- [ ] Taps/scroll in the rotated half work the right way round.
- [ ] Exit returns to normal immersive tabs; the lyrics page face-to-face is unchanged.
- [ ] Portrait + landscape; animator 0x and 0.5x; light/dark; album colors.
- [ ] gradlew assembleDebug passes.
```

### Plan D
1. Read `SplitFaceLyricsView.kt` fully and `InstrumentsPerformanceView` to confirm that two instances can share one controller (E29, E31) – done when: shared vs per-instance state (scroll, gestures) is listed.
2. Add `tabFaceToFace` state + `tabSplitActive = performanceView == Instruments && tabFaceToFace`. Reset it on leaving Instruments – `LyricsSheet.kt` near 754 (E25) – done when: the lyrics `splitActive` is untouched.
3. Make the Show Controls arrow condition (E26) also hide while `tabSplitActive` – done when: no arrow shows over the split.
4. Add the tabs shortcut as a second `AnimatedVisibility`, with the same modifiers and specs as E27 – done when: AC1 passes.
5. Create `presentation/components/tabs/TabFaceToFaceView.kt`: takes `content: @Composable () -> Unit` for one half, and lays out the rotated/upright halves + centre play/pause + leave, mirroring E31 – done when: it previews in both orientations.
6. Wire it into LyricsSheet beside the lyrics split (E30's spot), passing the Tab-only or Lyrics + Tab column from E29 as `content` – done when: AC2 and AC3 pass.
7. Add the enter/exit tokens, interruption and reduced motion from the spec – done when: it checks out at 0x and 0.5x.
8. Wire exit through the back handler + leave action, reusing the `exitFaceToFace` pattern (E28) without touching the pref – done when: AC5 passes.
9. Add paneTitle semantics and touch target – done when: TalkBack announces both halves.
10. **Verify**: build; AC1-AC6.

**Risks:** two live score renderers double the drawing cost (`ScoreRenderer`), so watch for jank on the cursor. If it shows, draw the second half from a shared layer. Gesture handlers inside `InstrumentsPerformanceView` (the `onUserInteraction` immersive tap, E29) must not bring the controls back while in the split. The `overlayShiftX` offset is shared with the lyrics shortcut, so both can't show at once (different pages, so that's fine).

---

## 4. Assumptions
- A: if the song is the one playing now, Play next still inserts a replay copy (only upcoming items are de-duped). Affects A1/A5.
- B: Gemini Nano is not used, because the in-app client is a mock (E16). You said to use it only if it's faster and better, and right now it isn't. Affects B8.
- B: country/region matching only works where song genre/tags carry it (no new metadata source).
- C: the four style names (Real, 8-bit, Synth, Strings) and the GM programs chosen (80/81/38/39/89) are defaults. Changing them only touches `programMap`.
- C: the notation demos follow the selected style too.
- D: "Lyrics + Tab is already implemented" was read to mean that face-to-face mirrors whatever the tabs page shows, so both people see the same view (each half = the current Tab or Lyrics + Tab view). Affects D6.
- D: the "options/tools deck button" is the immersive **Show Controls** arrow that brings the controls deck back (E26), so the shortcut sits right of it, as on lyrics.
- D: tab face-to-face is session-only, with no new setting.

**Follow-up (not in scope):** the lyrics face-to-face enter/exit uses hardcoded `tween(420/220)` with non-token easing (E30). Move it to MotionTokens to match the tabs version.
