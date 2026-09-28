# Synced-lyrics animation styles: prompts and plans

The feature covers five areas, so it's split into five prompts. Run them **in order on one branch**. Prompts 3–5 each depend on 1 and 2. The branch isn't release-ready until prompt 5 passes, because until then presets not yet implemented render as Clean.

| # | Prompt | Depends on |
|---|---|---|
| 1 | Style model, persistence, shared renderer hooks, timing tiers, reduced motion, and the **Clean** preset | — |
| 2 | Settings row, the **Animation style** screen and the live **Preview** | 1 |
| 3 | Fill and focus presets: **Karaoke, Acoustic, Soft** | 1, 2 |
| 4 | Word-decoration presets: **Glass, Flow, Neon, Cinematic** | 1, 2 |
| 5 | Motion and data presets: **Playful, Performance**, plus the full-feature verification pass | 1–4 |

Paste the **Shared context** block above each prompt.

---

## Shared context (paste above every prompt)

```markdown
## Shared context: PixelPlayer lyrics animation styles

App: Android, Kotlin, Jetpack Compose, Material 3 (1.5 alpha), MVVM + Hilt.
Colours: album-art colour scheme (`LocalMaterialTheme` / `ThemeStateHolder.activePlayerColorSchemePair`); lyric colours come only from `lyricsSheetColors(colorScheme)` → `LyricsSheetColors` and the contrast helpers `preferredContrastColor` / `resolveBrightWarmColor` in `presentation/components/LyricsSheet.kt`. M3 colour roles only, no hex.
Type: `MaterialTheme.typography` roles; lyric text style = the existing `lyricsTextStyle` (+ `SongExpressionProfile.applyTo`). No raw sp.
Shape: `MaterialTheme.shapes` / app shape tokens, no hardcoded dp corners.
Motion: `com.theveloper.pixelplay.ui.theme.MotionTokens` only. Easings: Emphasized, EmphasizedDecelerate, EmphasizedAccelerate, Standard, StandardDecelerate, StandardAccelerate. Durations: DurationShort1–4 (50/100/150/200), DurationMedium1–4 (250/300/350/400), DurationLong1–2. Springs: `MotionScheme.expressive()` spatial/effects specs (already used in `RecentlyPlayedRangeSelector.kt`). No new easing curves.

Existing lyric engine (reuse, don't rebuild):
- `lyrics/SmoothLyricsFill.kt`: `LyricsClock` / `rememberLyricsClock` is the ONE frame-interpolated position (freezes on pause, jumps on seek > 450 ms, never runs backwards); read `clock.now()` only in draw/layout lambdas. `SmoothLyricLine` (sung mask, feather, lifts via `liftedPieces` + `drawWithLifts`), `LyricLineLayers` (reserve/rest/active layers that always wrap identically), `wordFillChars`, `buildLyricWordLayout`, `xForChars(rtl)`, `LocalLyricsHighlightMode`.
- `lyrics/LyricExpression.kt`: `LyricMotion` (+ `Default`, `Still`), `LocalLyricMotion`, `SongExpressionProfile` (adaptive typography; `intensityAt()` is non-zero only when an audio-analysis envelope exists), `buildLineExpression`.
- `LyricsSheet.kt`: `SyncedLyricsList` (takes `playbackPositionFlow`, `positionOverrideMs`, `autoscrollAnimationSpec`, `relayoutKey`), `LyricLineRow`, `PlainLyricsLine`, `resolveCurrentLineIndex`, `resolveLineSingEndMs`, `systemAnimationsOff` (~L755) and the `LocalLyricMotion provides …` block (~L1001), `resolvedAutoscrollSpec` (~L559), `isLandscape` layouts.
- `lyrics/SplitFaceLyricsView.kt` and `player/AlbumCoverLyricsOverlay.kt` also render through `SmoothLyricLine` / `LyricsClock`.
- Timing data (`data/model/Lyrics.kt`): `SyncedLine(time, endTime?, words?, voiceId?, songPart?)`, `SyncedWord(time, endTime?, phonemes?)`, `SyncedPhoneme(time, endTime, characterStart, characterEnd (UTF-16), soundType, alphabet)`. Estimated letters from `data/lyrics/LetterTimingEstimator.kt` are tagged `alphabet = "estimated-grapheme"`, `soundType = "estimated"`.
- `data/lyrics/LyricsHighlightMode.kt`: user's granularity choice AUTO / WORD / PHONEME / LINE (set in `subcomps/LyricsMoreBottomSheet.kt`).
- Prefs: `presentation/components/LyricsDisplayPrefs.kt` (`LyricsDisplayPrefs`, `LyricsDisplayPrefKeys`, `lyricsDisplayPrefsFlow`, `rememberLyricsDisplayPrefs`, `Context.editLyricsDisplayPrefs`).
- Settings: Settings → "Player & Lyrics" (`SettingsCategory.LYRICS`) → `screens/settings/NowPlayingSettings.kt` → `LyricsSettingsContent` in `screens/settings/LyricsSettings.kt` ("Lyrics display" subsection). Settings rows are `SettingsItem(title, subtitle, leadingIcon, trailingIcon, trailingContent, settingKey, onClick)`. Dedicated sub-screen pattern = `PaletteStyleSettingsScreen` (route in `navigation/Screen.kt`, composable in `navigation/AppNavigation.kt` wrapped in `ScreenWrapper`, listed in `MainActivity` `routesWithHiddenNavigationBar`, searchable via `presentation/settings/SettingsRegistry.kt`).
- Settings strings: `res/values/strings_settings.xml`.

Global rules for every prompt:
- Playback position is the source of truth. A lyric becomes active at its timestamp, even if a transition is still settling; highlighting never waits for scrolling.
- No second animation clock. Timed progress derives from `LyricsClock.now()`; decorative settles use Compose animations keyed to state changes.
- No per-frame recomposition: animated values are read in `graphicsLayer` / `drawBehind` / `drawWithContent`.
- Layout stability: effects never change measured text layout or wrapping during playback.
- Build: cmd.exe /c "set JAVA_HOME=C:\Program Files\Android\Android Studio2\jbr&& gradlew.bat assembleDebug"
```

---

## Prompt 1: Foundation and the Clean preset

### 1. Improved prompt

```markdown
**Goal:** Add a persisted lyric animation style with ten presets (default Clean) and one shared style spec that every synced-lyrics surface reads, with timing-tier fallback and reduced motion, and implement Clean.

**Scope:** Renderer and prefs only, with no settings UI (that's prompt 2).
New: `presentation/components/lyrics/LyricAnimationStyle.kt`, `presentation/components/lyrics/LyricTimingTier.kt`.
Edit: `LyricsDisplayPrefs.kt`, `LyricsSheet.kt` (`SyncedLyricsList`, `LyricLineRow`, `resolvedAutoscrollSpec`, the `CompositionLocalProvider` at ~L1001), `lyrics/SmoothLyricsFill.kt` (`SmoothLyricLine`, `LyricLineLayers`, `liftedPieces`), `lyrics/SplitFaceLyricsView.kt`, `player/AlbumCoverLyricsOverlay.kt` (only through the shared renderer), `res/values/strings_settings.xml`.

**Current problem:** Synced lyrics have one fixed look: word lift + bloom from `LyricMotion`, and a bouncy autoscroll spring gated by the "Animated lyrics" tweak. There's no style choice. `liftedPieces` splits letter effects on UTF-16 indices, which can break graphemes (emoji, combining marks).

**Spec:**
- `enum class LyricsAnimationStyle(val key: String, @StringRes titleRes, @StringRes descriptionRes)` in exactly this order, with these exact strings:
  1. CLEAN "Clean": "Smooth scrolling with precise word highlighting."
  2. SOFT "Soft": "Gentle focus changes and a feathered highlight."
  3. GLASS "Glass": "A frosted highlight that follows each word."
  4. KARAOKE "Karaoke": "Clear colour progression for singing along."
  5. FLOW "Flow": "Fluid highlights with gently elastic movement."
  6. CINEMATIC "Cinematic": "Slow light sweeps and elegant line transitions."
  7. PLAYFUL "Playful": "Small word pops and lively letter waves."
  8. ACOUSTIC "Acoustic": "Warm ink-like text with a drawn underline."
  9. NEON "Neon": "Glowing words with a travelling light band."
  10. PERFORMANCE "Performance": "Expressive phrase movement and vocal accents."
  `fromKey(null or unknown) = CLEAN`.
- Persistence: `LyricsDisplayPrefKeys.ANIMATION_STYLE = stringPreferencesKey("lyrics_animation_style_v1")`, field `animationStyle` on `LyricsDisplayPrefs`, read in `lyricsDisplayPrefsFlow`, default CLEAN.
- `@Immutable data class LyricStyleSpec` (plain values only, read in draw lambdas), built by `LyricsAnimationStyle.spec()`:
  - `lineHandover`: kind (EASE_OUT | SPRING | CROSSFADE) + nominal duration token + easing token.
  - `lineEmphasis`: activeScale, activeLiftFraction, neighbourBlur (0 = off), neighbourAlphaFloor, nextLineBoost, band (Boolean).
  - `word`: FILL | SPOTLIGHT | CAPSULE | LIQUID | HALO | UNDERLINE | GLOW | POP (+ per-kind amounts).
  - `letter`: NONE | LIGHT_SWEEP | WEIGHT_PASS | SHIMMER | WAVE | INK_RICHNESS | ILLUMINATE | VOWEL_GLOW.
  - `motion: LyricMotion` (the lift/scale caps for this preset).
  - `overdraw`: extra top/bottom/side drawing room as fractions of font size (max of lift, scale growth, glow radius).
  Provide it with `LocalLyricAnimationStyle` (a `staticCompositionLocalOf`). In this prompt, every preset except CLEAN returns `CLEAN.spec()` with a `// TODO(prompt 3/4/5)` marker.
- Three state colours from `lyricsSheetColors` (new `LyricStateColors(upcoming, active, completed)` in `LyricAnimationStyle.kt`):
  - active = `lyricHighlight`;
  - completed = a blend between `lyricHighlight` and `content` (quieter than active), passed through `preferredContrastColor` against `container` to reach ≥ 4.5:1;
  - upcoming = `content` at reduced emphasis, kept ≥ 3:1 against `container` (lyric text is large text).
  Verify with very light, very dark and greyscale artwork.
- Timing tier per line (`LyricTimingTier.kt`): `UNTIMED` (plain lyrics, which keep the existing `PlainLyricsLine` path unchanged), `LINE`, `WORD`, `SUBWORD`.
  - LINE when `words` is null/empty or the highlight mode is LINE.
  - WORD when words carry times (a word's end = `endTime` ?: next word start ?: `resolveLineSingEndMs`).
  - SUBWORD only when a word has phonemes whose `alphabet != "estimated-grapheme"`.
  - Effective tier = min(available tier, the cap implied by `LyricsHighlightMode`). Word and letter effects are off below their tier: a line-only song gets line activation and line emphasis only, with no guessed word progress. The existing PHONEME-mode estimator stays as the user's explicit choice, but never counts as SUBWORD for preset effects.
- Timing rules (starting values, never added to playback):
  - progress effects (fills, underlines, sweeps) = the timed unit's full duration, from `clock.now()`;
  - line handover nominal DurationMedium1…DurationMedium4, clamped to ≤ 40 % of the time until the next line starts, and snapped when that is below DurationShort2;
  - word accents nominal DurationShort2…DurationShort3, clamped to ≤ 50 % of the word's duration.
  Add one pure helper `boundedDuration(nominalMs, availableMs, fraction, floorMs)` and use it everywhere.
- Layout stability:
  - Scale uses `graphicsLayer` with `transformOrigin` at the alignment edge (left / centre / right from `LyricsAlignment`). Weight changes never re-measure: they're drawn as a stroke overlay on the same `TextLayoutResult`.
  - `LyricLineRow` reserves `spec.overdraw` as internal padding; nothing between the row and the text clips to bounds.
  - Add the style to `SyncedLyricsList`'s `relayoutKey`, so a style change re-centres once.
- Grapheme safety: all letter-level effects iterate `android.icu.text.BreakIterator.getCharacterInstance()` boundaries (fix `liftedPieces` too), and keep using `xForChars(…, rtl)` for RTL.
- Clean:
  - lines: autoscroll tween, MotionTokens.EmphasizedDecelerate, nominal DurationMedium4 through `boundedDuration`, no overshoot;
  - words: continuous sung-mask fill in `active` across the word's duration, with a partial-letter reveal through real glyph shapes (the existing `drawSungMask` path, small feather);
  - letters: stationary (`LyricMotion` with zero lifts and scale);
  - completed words: `completed` colour.
- Autoscroll: `resolvedAutoscrollSpec` comes from the spec, not from `useAnimatedLyrics`. The "Animated lyrics" tweak and the blur toggle/strength keep all their other current effects (parallax, distance blur).
- Expressive typography: typography spans are unchanged. The song's `LyricMotion` scales the preset's motion only, clamped to the preset's caps, so a preset with zero lift stays at zero.
- Apply through `LocalLyricAnimationStyle` in the lyrics sheet (portrait + landscape), the split view (both halves), and the cover overlay (colour/fill/word/letter treatment only; its own line layout is unchanged).

**Motion:**
- Enter (line gains emphasis): tween, MotionTokens.EmphasizedDecelerate, handover duration from `boundedDuration`.
- Exit (line → completed): tween, MotionTokens.EmphasizedAccelerate, ~65 % of the enter duration.
- Transition: timed fill is a pure function of `clock.now()`, never an animation.
- Interruption:
  - seek: the clock jumps and every state resolves from the new position on the next frame, with no replay of skipped words or lines; an in-flight scroll is cancelled and restarted from its current offset;
  - pause/stall: the clock freezes, so progress freezes;
  - speed change: handled by the clock;
  - style change: the next frame draws the new spec at the current position, with no replayed entry;
  - manual scroll: existing behaviour and "return to current lyric" are unchanged.
- Reduced motion (`systemAnimationsOff`): lifts, springs, waves and travelling decorations are replaced by stable highlighting; handovers become a crossfade of DurationShort3 or less; timed fills stay (they're timing, not decoration).

**Accessibility:** Decorations are draw-only, with no semantics changes. The contrast targets above are met. Touch targets are unchanged.

**Don't:** add a second clock or ticker, recompose per frame, add easings/hex/sp/dp corners, change `PlainLyricsLine`, change gestures or manual-scroll behaviour, remove or rename existing prefs, or add settings UI.

**Acceptance criteria:**
- [ ] Enum order, keys and strings match the list exactly; unknown/missing pref → CLEAN.
- [ ] Style persists across app restart (verified by writing the pref manually).
- [ ] Clean: no lifts or bounce; the fill advances continuously, including partial letters; completed words are quieter than the active word.
- [ ] A line-only song shows line activation only; an untimed song is static.
- [ ] Seek forward/back resolves the right line and word within one frame, with no replay; pause freezes the fill.
- [ ] Emoji / combining-mark / RTL lines render without broken glyphs.
- [ ] Sheet portrait + landscape, split view and cover overlay all use the spec.
- [ ] Animator scale 0: no lifts or springs, fills still advance.
- [ ] Build command passes; existing unit tests pass.
```

### 2. Plan

```markdown
1. Read the engine: `SmoothLyricsFill.kt`, `LyricExpression.kt`, `LyricsSheet.kt` (L470–600, L740–770, L995–1010, L2261–2900), `SplitFaceLyricsView.kt`, `AlbumCoverLyricsOverlay.kt`. Note every place `LyricMotion`, `useAnimatedLyrics` and `resolvedAutoscrollSpec` are used - no files changed - done when: a list of call sites exists in the PR description.
2. Add the `LyricsAnimationStyle` enum + 20 strings - `lyrics/LyricAnimationStyle.kt`, `strings_settings.xml` - exact strings from Spec - done when: a unit test asserts order, keys and `fromKey` fallback.
3. Add the pref key, field and flow read - `LyricsDisplayPrefs.kt` - key `lyrics_animation_style_v1`, default CLEAN - done when: a unit test on `lyricsDisplayPrefsFlow` returns CLEAN for empty prefs and the stored value otherwise.
4. Add `LyricStyleSpec`, `LocalLyricAnimationStyle`, `CLEAN.spec()` and the other nine → Clean with TODOs - `LyricAnimationStyle.kt` - Clean uses MotionTokens.EmphasizedDecelerate / DurationMedium4 - done when: it compiles and each preset returns a spec.
5. Add `LyricStateColors` from `lyricsSheetColors` - `LyricAnimationStyle.kt` (+ read `LyricsSheet.kt` helpers) - `preferredContrastColor`, ≥ 4.5:1 active/completed, ≥ 3:1 upcoming - done when: a unit test with white, black and mid-grey containers meets the ratios.
6. Add the timing-tier resolver + `boundedDuration` - `lyrics/LyricTimingTier.kt` - rules from Spec - done when: unit tests cover line-only, word, measured-phoneme, estimated-grapheme (→ WORD), highlight mode LINE, and short gaps (snap).
7. Provide the style: read prefs in `LyricsSheet`, provide `LocalLyricAnimationStyle` next to `LocalLyricMotion` (~L1001), and make `LocalLyricMotion` = preset caps scaled by the expression profile, or `LyricMotion.Still` when animations are off - `LyricsSheet.kt` - done when: changing the pref (debug) switches the look live.
8. Drive autoscroll from the spec through `boundedDuration`, and add the style to `relayoutKey` - `LyricsSheet.kt` (`resolvedAutoscrollSpec`, `SyncedLyricsList`) - done when: Clean scroll has no overshoot and highlighting activates before the scroll ends.
9. Apply state colours + tier gating in `LyricLineRow` / `LyricLineLayers` / `SmoothLyricLine`, with no fill below WORD tier - `LyricsSheet.kt`, `SmoothLyricsFill.kt` - done when: a line-only LRC shows no word fill.
10. Reserve overdraw padding in `LyricLineRow` and check for clipping ancestors; add scale `transformOrigin` by alignment - `LyricsSheet.kt` - done when: the largest text size + immersive shows no clipped glyphs.
11. Make letter effects grapheme-safe - `SmoothLyricsFill.kt` (`liftedPieces`, any per-char loop) - `BreakIterator.getCharacterInstance()` - done when: a unit test on "👩‍👩‍👧 é a" (with a combining mark) yields whole clusters.
12. Split view + cover overlay read `LocalLyricAnimationStyle` (the overlay keeps its own line layout) - `SplitFaceLyricsView.kt`, `AlbumCoverLyricsOverlay.kt` - done when: both halves and the overlay show the Clean look.
13. Verify (below).

**Verification:** Run the build command and unit tests. Then check manually, one check per acceptance criterion:
- a word-timed TTML song, a line-only LRC song and a plain-text song;
- seek ±30 s while playing and paused; playback speed 0.5× / 2×;
- animator scale 0× and 0.5×;
- light, dark and near-greyscale artwork;
- portrait, landscape and split view;
- back gesture from the sheet mid-scroll;
- an emoji/RTL/combining-mark lyric.

**Risks:**
- Default look changes for existing users (lifts → Clean).
- Removing the `useAnimatedLyrics` gate from autoscroll may surprise tweak users.
- Overdraw padding shifts row heights, which affects autoscroll centring.
- Reading prefs in `LyricsSheet` adds a recomposition source; keep the provider above the list, not inside rows.
- The grapheme change touches `liftedPieces`, which PHONEME mode also uses.
```

---

## Prompt 2: Settings row, Animation style screen and live Preview

### 1. Improved prompt

```markdown
**Goal:** Let users pick the lyric animation style from Settings → Player & Lyrics → Lyrics display → Animation style, on a dedicated screen with a pinned live Preview that uses the real lyric renderer.

**Scope:**
New: `presentation/screens/LyricsAnimationStyleScreen.kt`, `presentation/components/lyrics/LyricsAnimationPreview.kt`, `presentation/components/lyrics/LyricsPreviewSample.kt`.
Edit: `navigation/Screen.kt`, `navigation/AppNavigation.kt`, `MainActivity.kt` (`routesWithHiddenNavigationBar`), `presentation/settings/SettingsRegistry.kt`, `screens/settings/LyricsSettings.kt`, `strings_settings.xml`.
Mirror `PaletteStyleSettingsScreen` for the scaffold, top bar and back behaviour.

**Current problem:** The style exists (prompt 1) but can't be chosen or previewed.

**Spec:**
- Row in the "Lyrics display" subsection of `LyricsSettingsContent`, using `SettingsItem`:
  - title "Animation style";
  - subtitle "Choose how synced lyrics move and highlight";
  - `trailingContent` = current preset title (`bodyMedium`, `primary`) + chevron (`onSurfaceVariant`), as in the palette row;
  - leading icon in the same style as sibling rows (`secondary` tint);
  - `settingKey = "lyrics_animation_style"`.
  `onClick` → `navController.navigateSafely(Screen.LyricsAnimationStyle.route)`. `navController` is already passed from `NowPlayingSettings`.
- Route `Screen.LyricsAnimationStyle("lyrics_animation_style")`, registered like `PaletteStyle` (`ScreenWrapper`, `onBackClick = popBackStack`), added to `routesWithHiddenNavigationBar`, plus a `SettingEntry(key = "lyrics_animation_style", categoryId = "lyrics", route = …, keywords = ["lyrics", "animation", "karaoke", "highlight", "style"])`.
- Screen titled "Animation style", with the same top bar as `PaletteStyleSettingsScreen`. Layout via `BoxWithConstraints`:
  - Portrait: `Column { PreviewCard; LazyColumn(weight 1f) }`. The preview is outside the list, so it stays visible while the list scrolls. Preview height = min(3 lyric lines at the current lyric text style, 30 % of maxHeight).
  - Landscape (maxWidth > maxHeight): `Row { PreviewCard(weight 1f, fill height); LazyColumn(weight 1f) }`.
- PreviewCard:
  - wrapped in `MaterialTheme(colorScheme = album scheme from activePlayerColorSchemePair for the current dark/light)`, falling back to the app scheme when there's no song;
  - container `lyricsSheetColors(...).container`, shape `MaterialTheme.shapes.large`;
  - a "Preview" label chip at top-start (`labelMedium`, `onPrimaryContainer` on the `surfaceSecondary` role);
  - the lyrics use the same `lyricsTextStyle` / font prefs as the player.
- Preview content (same renderer):
  - It calls `SyncedLyricsList` with `LocalLyricAnimationStyle` provided for the *currently selected* style, `onLineClick = {}`, `onSeekTo = null`, and input blocked by a pointer-consuming overlay. Semantics: `clearAndSetSemantics { contentDescription = "Preview of <style> lyric animation" }`.
  - Source A, when the current song has synced lyrics: the player's lyrics, `playbackPositionFlow` and sync offset. It follows real playback (it freezes when paused), and the list keeps the current line centred.
  - Source B, otherwise: `LyricsPreviewSample` with a local `MutableStateFlow<Long>` advanced by one `withFrameNanos` loop that runs only while the screen is RESUMED. It is silent and never touches the player. It loops at 7600 ms.
  - Switching A ↔ B (lyrics load while open) crossfades (Motion below).
- Sample (explicit timings, ms). The words are built so that `buildLyricWordLayout` maps them (trimmed tokens):
  - L1 t=0 end=1500 "We run through the city": We 0–200, run 220–420, through 440–700, the 720–840, city 860–1500.
  - Pause 1500–2400 (phrase gap).
  - L2 t=2400 end=5000 "and we hold on": and 2400–2560, we 2580–2740, hold 2760–3100, on 3120–5000 (held word).
  - L3 t=5300 end=6600 "to the light": to 5300–5460, the 5480–5620, light 5640–6600 (fast handover, 300 ms gap).
  - Rest 6600–7600, then loop to 0 (seek-like jump, no replay).
  - No phonemes. Performance previews its word-level fallback honestly.
- Preset list:
  - `Modifier.selectableGroup()`; the ten rows come from the enum order;
  - each row is `selectable(selected, role = Role.RadioButton)` on the whole row, with min height ≥ 56 dp (≥ 48 dp target);
  - `RadioButton(onClick = null)` + title (`titleMedium`, `onSurface`) + description (`bodyMedium`, `onSurfaceVariant`);
  - static rows, with no per-row animation.
- Selecting: set local `selected` immediately (the radio updates on the same frame), then `context.editLyricsDisplayPrefs { it[ANIMATION_STYLE] = key }`. Stay on the screen. Never call any player method, so song, position, queue and play state are untouched. Back → the Lyrics display row shows the new name via `rememberLyricsDisplayPrefs`.

**Motion:**
- Screen push/pop: the existing NavHost transitions (`navigation/Transitions.kt`, DurationMedium3); add no custom ones.
- Radio: M3 `RadioButton`'s built-in animation.
- Preview style change: snap at the current position (prompt 1 rule), no crossfade.
- Preview source enter: fade in, tween, MotionTokens.EmphasizedDecelerate, DurationMedium1. Exit: fade out, tween, MotionTokens.EmphasizedAccelerate, DurationShort3.
- Interruption: back gesture mid-source-crossfade just pops (the preview is disposed); rapid taps on rows always leave the last tap selected; the sample loop pauses when not RESUMED and resumes from its last time.
- Reduced motion: source switch is instant; the preview still plays with the reduced-motion treatment from prompt 1.

**Accessibility:** Rows are 48 dp+ with radio role and selected state announced. The preview is announced once via its content description. Label contrast ≥ 4.5:1. Rows are readable at font scale 1.3.

**Don't:** run animations in rows, create a second renderer or fake preview, start/seek/pause the player, change other settings rows, add a new nav transition, or hardcode colours/sizes.

**Acceptance criteria:**
- [ ] Settings → Player & Lyrics → Animation style row shows the title, subtitle and current preset; settings search finds it.
- [ ] Screen lists the ten presets in order with the exact names and descriptions; whole-row tap; single selection.
- [ ] Selection updates instantly, persists after restart, and the screen stays open.
- [ ] Preview is pinned in portrait and landscape, labelled "Preview", and uses the real `SyncedLyricsList`.
- [ ] With a synced song playing, the preview follows it; paused → frozen; no playback change on any tap.
- [ ] With no synced song, the sample loops silently and shows a line change, short words, the held "on" and a pause.
- [ ] Back shows the new name in the row.
- [ ] Build command passes.
```

### 2. Plan

```markdown
1. Read `PaletteStyleSettingsScreen.kt`, `SettingsComponents.kt` (`SettingsItem`), `NowPlayingSettings.kt`, `LyricsSettings.kt` (Lyrics display subsection), `AppNavigation.kt` (~L306), `MainActivity.kt` (~L715), `SettingsRegistry.kt` (~L150) - no change - done when: the pattern is noted.
2. Add the route + composable + hidden-nav entry + registry entry - `Screen.kt`, `AppNavigation.kt`, `MainActivity.kt`, `SettingsRegistry.kt` - existing NavHost transitions - done when: navigating to the route shows an empty scaffold with a working back.
3. Add the settings row with trailing value - `LyricsSettings.kt`, `strings_settings.xml` - `SettingsItem`, `bodyMedium`/`primary` - done when: the row shows "Clean" on a fresh install and navigates.
4. Build the screen scaffold + portrait/landscape split + pinned preview slot - `LyricsAnimationStyleScreen.kt` - `BoxWithConstraints`, `shapes.large` - done when: rotating keeps the preview visible and the list scrolls independently.
5. Build the preset list with selectable rows + immediate local selection + DataStore write - `LyricsAnimationStyleScreen.kt` - `selectableGroup`, `Role.RadioButton` - done when: taps update instantly and survive restart.
6. Add the sample data + silent lifecycle-aware sample ticker - `LyricsPreviewSample.kt` - exact timings from Spec - done when: a unit test checks word coverage via `buildLyricWordLayout` and the loop length is 7600 ms.
7. Build the preview composable: source A/B selection, album `MaterialTheme`, "Preview" label, input blocker, semantics, source crossfade - `LyricsAnimationPreview.kt` - MotionTokens.EmphasizedDecelerate/DurationMedium1 in, EmphasizedAccelerate/DurationShort3 out - done when: it follows a playing song and falls back to the sample when lyrics are absent.
8. Verify (below).

**Verification:** Run the build. Then check manually:
- the settings path and settings search;
- the exact list text;
- tap every row (instant, stays open);
- kill and relaunch → selection kept;
- a playing synced song (follows, pause freezes, position/queue unchanged after taps);
- no song / plain lyrics → sample;
- portrait + landscape;
- back gesture mid-crossfade;
- animator scale 0× / 0.5×;
- light/dark and several artworks;
- TalkBack on the rows and the preview; font scale 1.3.

**Risks:**
- `SyncedLyricsList` autoscroll inside a short preview may fight its `highlightZoneFraction`; pass values that centre within the card.
- Collecting the player position flow on a settings screen keeps position updates alive; scope it to RESUMED.
- The album scheme is null with no song; the fallback must not crash.
- The hidden-nav route list is easy to forget.
```

---

## Prompt 3: Karaoke, Acoustic, Soft

### 1. Improved prompt

```markdown
**Goal:** Implement the Karaoke, Acoustic and Soft presets as distinct `LyricStyleSpec`s on the shared renderer.

**Scope:** `lyrics/LyricAnimationStyle.kt` (specs), `lyrics/SmoothLyricsFill.kt` (word/letter treatments UNDERLINE, INK_RICHNESS, SPOTLIGHT, LIGHT_SWEEP), `LyricsSheet.kt` (`LyricLineRow` line emphasis: nextLineBoost, neighbourBlur). No settings changes.

**Current problem:** These three presets render as Clean (prompt 1 TODOs).

**Spec:**
- Karaoke:
  - lines: current line full emphasis; the next line gets `nextLineBoost` (clearly above other upcoming lines, below active), inside the existing list with all context kept, not a two-line screen; handover tween MotionTokens.EmphasizedDecelerate, nominal DurationMedium1 via `boundedDuration`, starting promptly at the line's timestamp; no other movement during a phrase.
  - words: upcoming = `upcoming`, active = the fill in `active` across the exact word duration, completed = fully filled `active` for the rest of the line; when the line ends it settles to `completed`.
  - letters: the continuous fill edge (existing sung mask, small feather); no lifts.
- Acoustic:
  - lines: brightness spotlight (active full, others `neighbourAlphaFloor`); scroll tween MotionTokens.StandardDecelerate, nominal DurationMedium4.
  - words: an underline drawn under the active word from its start x to its end x in proportion to word progress (clock-driven, RTL-aware via `xForChars`). Stroke width ≈ 0.06 × font size, round cap, colour `warmHighlight` from `LyricsSheetColors` (already derived from tertiary, so it is warm when the palette allows and never a fixed brown). Placed within the reserved bottom overdraw.
  - letters: colour richness: interpolate from a desaturated version of `warmHighlight` (contrast kept ≥ 3:1) to full `warmHighlight` across the word's progress.
  - transitions: the completed underline fades out (effects spec below) while a fresh stroke starts at the next word's timestamp; no texture.
- Soft:
  - lines: active line sharp; neighbours use the existing `lyricsBlurRadius` capped to a small maximum, only when the blur toggle is on and API ≥ 31, else alpha only; neighbour alpha floor keeps them readable.
  - words: a spotlight: a feathered radial alpha lift centred on the fill edge within the active word (feather ≥ 1.5 × the existing `featherPx`), so there's no hard edge.
  - letters: a diffuse light sweep inside the glyphs (gradient brush clipped to the text layer, progress-driven); no letter movement.
  - transitions: outgoing and incoming emphasis crossfade (tween MotionTokens.Emphasized, DurationMedium3 via `boundedDuration`); the active boundary switches at the timestamp even while light fades.
- Tier fallback: at LINE tier, Karaoke/Acoustic/Soft show only their line treatment, with no underline, fill or spotlight.

**Motion:**
- Enter: line emphasis tween MotionTokens.EmphasizedDecelerate (durations above).
- Exit: tween MotionTokens.EmphasizedAccelerate at ~65 %.
- Decorative fades (underline out, spotlight out): `MotionScheme.expressive().fastEffectsSpec()` (no overshoot), bounded ≤ 50 % of the next word.
- Interruption: seek/style change resolves instantly from the clock (a completed underline is simply absent after a seek); pause freezes underline, fill and sweep.
- Reduced motion: line scroll crossfade ≤ DurationShort3; the spotlight becomes a static brighter word; the sweep is off; underline progress and the fill stay.

**Accessibility:** Contrast per the prompt 1 state colours; the desaturated ink start stays ≥ 3:1.

**Don't:** redesign the lyric list into two lines, add textures, use fixed warm colours, blur beyond the cap, or add a new clock.

**Acceptance criteria:**
- [ ] Each of the three is visibly distinct from Clean and from each other in the Preview and the player.
- [ ] Karaoke: next line is clearly anticipated; completed words stay filled; advances promptly.
- [ ] Acoustic: the underline tracks the word exactly, fades, and restarts; warm only when the palette allows.
- [ ] Soft: neighbours stay readable; no hard edges or flashes; blur is off when the toggle is off or API < 31.
- [ ] Line-only songs show line treatment only.
- [ ] Build passes.
```

### 2. Plan

```markdown
1. Add line-emphasis support for `nextLineBoost` and capped `neighbourBlur` - `LyricsSheet.kt` (`LyricLineRow`, uses `distanceFromCurrent`) - existing `lyricsBlurRadius` - done when: setting the fields on a debug spec changes the next/neighbour lines.
2. Karaoke spec - `LyricAnimationStyle.kt` - EmphasizedDecelerate/DurationMedium1 - done when: Preview shows fill + next-line anticipation.
3. Draw the UNDERLINE word treatment (progress, RTL, bottom overdraw) + completed fade - `SmoothLyricsFill.kt` - `fastEffectsSpec` - done when: the underline ends exactly at word end on the held "on".
4. Add the INK_RICHNESS letter treatment - `SmoothLyricsFill.kt` - `warmHighlight` lerp - done when: colour deepens across the word; contrast test passes.
5. Acoustic spec - `LyricAnimationStyle.kt` - StandardDecelerate/DurationMedium4 - done when: distinct in the Preview.
6. Add the SPOTLIGHT word treatment + LIGHT_SWEEP letter treatment - `SmoothLyricsFill.kt` - feather ≥ 1.5× `featherPx` - done when: no hard edge at any zoom.
7. Soft spec + crossfade handover - `LyricAnimationStyle.kt` - Emphasized/DurationMedium3 - done when: distinct in the Preview.
8. Add reduced-motion variants for all three - `LyricAnimationStyle.kt` - done when: animator 0× shows static emphasis + fills.
9. Verify (below).

**Verification:** Run the build. Then check each preset with:
- a word-timed song, a line-only song and the sample;
- a fast rap passage (bounded durations);
- seek, pause, 2× speed;
- light/dark/greyscale art;
- portrait/landscape/split;
- blur toggle off; API < 31 emulator;
- animator 0× / 0.5×.

**Risks:**
- Blur on many rows is costly; cap to visible neighbours ±2.
- Underline overdraw may overlap the translation/romanization lines under a row; check with both enabled.
- Gradient brushes allocated per frame; cache them per layout.
```

---

## Prompt 4: Glass, Flow, Neon, Cinematic

### 1. Improved prompt

```markdown
**Goal:** Implement the four word-decoration presets (Glass, Flow, Neon, Cinematic) on one shared active-word geometry layer.

**Scope:** New `lyrics/ActiveWordGeometry.kt`; edit `lyrics/LyricAnimationStyle.kt`, `lyrics/SmoothLyricsFill.kt` (CAPSULE, LIQUID, GLOW, HALO, WEIGHT_PASS, SHIMMER, ILLUMINATE), `LyricsSheet.kt` (line band, line scale).

**Current problem:** These presets render as Clean.

**Spec:**
- `ActiveWordGeometry`: from the cached `TextLayoutResult` + `LyricWordLayout`, the per-visual-line bounding rects of the active and previous word (a wrapped word yields one rect per visual line), cached per layout, and read in draw only. It exposes `handoverProgress` (0…1 since the active word's start, over the bounded accent duration) and a `sameVisualLine` flag.
- Glass:
  - lines: active scale 1.025 (`graphicsLayer`, alignment origin, no re-measure).
  - words: a capsule (fully rounded, `CircleShape`) behind the active word, padded ≈ 0.3 em horizontally and kept inside the line box vertically. Fill = `accent` at low alpha over the container; edge = hairline `content` at low alpha. The word's text colour vs the blended capsule colour is kept ≥ 4.5:1 via `preferredContrastColor`.
  - "Blurred backdrop where supported": the lyrics have no cheap backdrop sampling, so use the tinted translucent capsule; do not add per-word RenderEffect layers.
  - letters: text crisp above the capsule, with a faint progress-driven inner-light sweep.
  - movement: capsule x/width tween MotionTokens.Emphasized, DurationShort4 via `boundedDuration`, no bounce. On a wrap (`!sameVisualLine`): fade out (EmphasizedAccelerate, DurationShort3) and fade in at the new rect (EmphasizedDecelerate, DurationShort4), never a diagonal drag. The capsule is drawn behind the text and never over it.
- Flow:
  - lines: handover with `MotionScheme.expressive().defaultSpatialSpec()` (small, quickly damped overshoot); when the gap to the next line < DurationMedium4 use `fastSpatialSpec()`; when < DurationMedium1 use a tween (MotionTokens.EmphasizedDecelerate) with no overshoot, so motion never accumulates.
  - words: a liquid highlight: for the first part of `handoverProgress` the shape spans previous∪current word (joined with rounded ends), then contracts to the current word (tween MotionTokens.Emphasized, bounded accent duration). There's no connection across a wrap or a horizontal gap > 3 × space width: it relocates like Glass instead.
  - letters: WEIGHT_PASS: a narrow band of optical emboldening (stroke overlay on the same layout, width ≤ 0.04 × font size) that passes through the active word with progress, with no advance/spacing change.
- Neon:
  - lines: a broad, faint vertical-gradient band (`accent`, low alpha) behind the active line, moving to the new line (tween MotionTokens.Emphasized, handover duration).
  - words: glow = `Shadow(accent, blurRadius ≤ 0.35 × font size)` on the highlight layer only; the previous word keeps a residual glow that fades with `fastEffectsSpec()` within DurationShort3. The glow spread cap keeps neighbours distinct.
  - letters: ILLUMINATE: the sung part glows progressively with a sharp fill core drawn on top; upcoming letters subdued; completed letters = `completed` + a small, quiet glow.
  - no flicker, strobe or repeated flashes, ever.
- Cinematic:
  - lines: emphasis crossfade between consecutive lines while repositioning (tween MotionTokens.Emphasized, nominal DurationMedium4 via `boundedDuration`); context kept.
  - words: a soft radial halo behind the active word (`accent`, low alpha) transfers from the previous word: the outgoing halo drops below completed-state intensity within DurationShort3, so the previous word never looks active.
  - letters: SHIMMER: a low-intensity gradient band travelling through the active word with its progress (held words = slow travel across the full duration).
  - no abrupt scale, no bright flares.
- Overdraw: each spec declares its glow/halo/capsule room (prompt 1 `overdraw`).
- Tier fallback: at LINE tier only the line treatments (Glass scale, Flow spring, Neon band, Cinematic crossfade) apply.

**Motion:** as specified per preset. Enter uses decelerate, exit uses accelerate at ~65 %, and effects (alpha/glow) use `fastEffectsSpec()` with no overshoot.
- Interruption: word changes retarget the capsule/liquid from its current value (Animatable `animateTo`, no snap-back); seek/style change snaps the geometry to the resolved word; pause freezes progress-driven parts, and in-flight settles finish.
- Reduced motion: Glass capsule jumps with a DurationShort3 crossfade; Flow uses a tween with no liquid stretch or weight pass; Neon band jumps, glow is static on the active word, no residual; Cinematic uses a DurationShort3 crossfade with no shimmer travel (static halo).

**Accessibility:** Text over the capsule/halo ≥ 4.5:1; decorations are draw-only.

**Don't:** use backdrop blur layers per word, bounce the capsule, connect liquid across wraps, flicker, glow wide enough to merge words, or re-measure text for weight.

**Acceptance criteria:**
- [ ] The four presets are visibly distinct from each other and from prompts 1/3.
- [ ] The Glass capsule never covers text and fades (not slides) across wraps.
- [ ] Flow overshoot is small and disappears in fast passages; the weight pass never moves neighbours.
- [ ] Neon: no flashes; neighbours stay distinct; the residual glow is gone before the next word.
- [ ] Cinematic: the previous word never reads as active; the held word's shimmer spans its full duration.
- [ ] Build passes.
```

### 2. Plan

```markdown
1. Add `ActiveWordGeometry` (rects per visual line, cache, handover progress, `sameVisualLine`) - `lyrics/ActiveWordGeometry.kt` - done when: a unit test with a wrapped word returns 2 rects; RTL rects are correct.
2. Add a line-level band + scale hook in `LyricLineRow` - `LyricsSheet.kt` - `graphicsLayer`, alignment origin - done when: a debug spec shows the band and scale without re-wrapping.
3. Glass: CAPSULE draw + retarget + wrap fade + sweep + spec - `SmoothLyricsFill.kt`, `LyricAnimationStyle.kt` - Emphasized/DurationShort4 - done when: the Preview capsule follows the words, fades at the wrap, and the contrast check passes.
4. Flow: LIQUID + WEIGHT_PASS (stroke overlay) + adaptive spring selection + spec - same files - `defaultSpatialSpec` / `fastSpatialSpec` / tween fallback - done when: the fast L2→L3 sample handover has no visible overshoot.
5. Neon: band move, GLOW + residual, ILLUMINATE + spec - same files - `fastEffectsSpec`, blur cap - done when: no word blends into a neighbour at the largest text size.
6. Cinematic: HALO transfer, SHIMMER, crossfade handover + spec - same files - Emphasized/DurationMedium4 - done when: the previous-word halo is below completed intensity within DurationShort3.
7. Add reduced-motion variants for all four - `LyricAnimationStyle.kt` - done when: animator 0× shows the static variants.
8. Verify (below).

**Verification:** Run the build. Then check each preset with:
- word-timed, line-only and sample lyrics;
- a long wrapped line;
- a fast passage;
- seek, pause, 0.5× / 2× speed;
- light/dark/greyscale art;
- portrait/landscape/split; the cover overlay;
- animator 0× / 0.5×;
- GPU profiling (no jank from shadows).

**Risks:**
- Text shadow blur is expensive on long lists; apply glow only to the active and previous lines.
- The stroke-overlay weight looks different for non-variable fonts.
- Capsule padding can overlap adjacent visual lines at tight line spacing (`LyricsLineSpacing.TIGHT`).
- The expressive spatial spec must not be used for alpha.
```

---

## Prompt 5: Playful, Performance, and the full-feature verification

### 1. Improved prompt

```markdown
**Goal:** Implement the Playful and Performance presets, then verify the complete Animation style feature end to end.

**Scope:** `lyrics/LyricAnimationStyle.kt`, `lyrics/SmoothLyricsFill.kt` (POP, WAVE, VOWEL_GLOW), `lyrics/LyricExpression.kt` (read-only use of `SongExpressionProfile`), `LyricsSheet.kt` (overlapping-voice activation for Performance only). Remove all remaining `TODO(prompt …)` fallbacks.

**Spec:**
- Playful:
  - lines: slight lift (`LyricMotion.lineRise` small) + scale ≈ 1.02 (alignment origin), with a light spring (`MotionScheme.expressive().fastSpatialSpec()`) and rapid damping.
  - words: an onset pop of 4–6 % scale that settles in the bounded accent duration (nominal DurationShort3, `fastSpatialSpec`).
  - letters: an upward WAVE via the grapheme-safe `liftedPieces`; peak displacement ≈ 1.5 dp at the Medium lyric size, scaled by fontSize / Medium fontSize, clamped 1–2 dp at normal sizes; the wave stays inside the reserved overdraw, with no collisions.
  - dense passages (word duration < DurationShort4 or > 4 words/s): no wave, pop halved.
- Performance:
  - lines: phrase grouping: the model has no phrase field (`songPart` is a song section, not a phrase), so grouping is off; keep a hook `phraseGroupOf(line): Int?` that returns null today. Line handover tween MotionTokens.EmphasizedDecelerate, DurationMedium3 bounded; phrase endings (a line followed by a gap ≥ DurationMedium4) settle with the full exit duration.
  - words, with an analysis: only when `SongExpressionProfile` has an audio envelope (`intensityAt` > 0 possible) are words whose envelope sits above the song norm given a stronger accent (lift ≤ `LyricMotion.wordLift`, stroke-overlay weight, brightness). Profiles built from heuristics only (genre/pacing) do not count as vocal data.
  - words, fallback: precise word highlighting + modest emphasis on long words (duration > 1.8 × `typicalWordMs` when a profile exists, else > the median word duration of the line). No claims of stress detection.
  - letters: VOWEL_GLOW only for measured phonemes (`alphabet != "estimated-grapheme"`) whose `soundType` marks a vowel. Glow over `characterStart..characterEnd` (snapped outward to grapheme boundaries), sustained until the phoneme's `endTime`, released with `fastEffectsSpec()`. Never inferred from spelling.
  - overlaps: when lines carry different `voiceId`s with overlapping [time, endTime), each is active on its own timing (secondary-voice line drawn active without scrolling to it). Without that data, behaviour is unchanged.
  - rapid lyrics: accents halved, no lift.

**Motion:** as specified. Interruption: pops and waves retarget from their current value on the next word; a seek resolves without replaying pops; a pause freezes the wave position (progress-driven) and in-flight settles finish. Reduced motion: Playful → Clean-like highlight with a DurationShort3 fade on the active word; Performance → word highlighting + brightness accent only, with a static vowel glow.

**Accessibility:** Letter displacement stays ≤ 2 dp at normal sizes; contrast per prompt 1.

**Don't:** fabricate phoneme or stress data, run heavy analysis to enable the preset, use the letter estimator for vowel glow, or let waves exceed the overdraw.

**Acceptance criteria (full feature, spec §7):**
- [ ] Settings → Player & Lyrics → Animation style works; settings search finds it.
- [ ] All ten presets appear with their exact names and descriptions, in order.
- [ ] Every preset is visibly distinct and matches its definition (side-by-side screen recording of the Preview for all ten).
- [ ] Selection applies immediately to the Preview and the player and persists after restart; song, position, queue and play state are unchanged.
- [ ] The Preview and the player use the same `SyncedLyricsList` / `SmoothLyricLine` + `LyricStyleSpec` (code check: no preview-only renderer).
- [ ] Playback: pause freezes, resume is correct, speed changes are followed, seek resolves without replay, a style change applies at the current position, and manual scroll/return-to-current is unchanged.
- [ ] Timing fallbacks: line-only, word, measured-phoneme and untimed songs each behave per prompt 1.
- [ ] Wrapped words, punctuation, RTL, combining marks and emoji render correctly in every preset.
- [ ] Text is readable and unclipped in portrait, landscape, split and immersive at every text size.
- [ ] Reduced motion (animator 0×) gives stable variants for all ten.
- [ ] Existing lyric controls, gestures and unrelated settings are unchanged.
- [ ] Build command passes; unit tests pass.
```

### 2. Plan

```markdown
1. Playful: POP (onset scale, bounded), line lift/scale, WAVE sizing from font size, dense-passage reduction, spec - `SmoothLyricsFill.kt`, `LyricAnimationStyle.kt` - `fastSpatialSpec`, DurationShort3 - done when: the Preview's short words pop, the held "on" waves, and there are no collisions at the XL size.
2. Performance emphasis source: detect an analysis-backed profile; word-accent selection (envelope vs long-word fallback) - `LyricAnimationStyle.kt` (reads `LyricExpression.kt`) - done when: unit tests show a heuristic-only profile → fallback path.
3. Add the VOWEL_GLOW measured-only treatment - `SmoothLyricsFill.kt` - `fastEffectsSpec` release - done when: a test lyric with measured vowel phonemes glows only over those ranges; an estimated-grapheme lyric shows no glow.
4. Performance line handling: phrase-ending settle, `phraseGroupOf` hook (null), overlapping `voiceId` activation - `LyricsSheet.kt` (`resolveCurrentLineIndex` stays; add a secondary-active check in `LyricLineRow`) - done when: a TTML with overlapping voices shows both active without extra scrolling.
5. Remove every `TODO(prompt …)` fallback; add a unit test that the ten specs are pairwise different - `LyricAnimationStyle.kt` - done when: the test passes.
6. Reduced-motion variants for Playful + Performance - done when: animator 0× shows them.
7. Full verification (below).

**Verification:** Run the build + unit tests. Then do one manual check per acceptance criterion using:
- a word-timed TTML song, a line-only LRC song, a measured-phoneme song if available, and a plain-text song;
- an RTL song (Arabic/Hebrew), a song with emoji and combining marks, and a long wrapped line;
- seek, pause, stall (airplane mode on a stream), 0.5× / 2× speed;
- a style change mid-word;
- portrait/landscape/split/immersive at S–XL sizes and font scale 1.3;
- light/dark and white, black and greyscale artwork;
- animator 0× / 0.5×;
- back gesture from the Animation style screen and from the lyrics sheet;
- existing Lyrics settings rows, swipe-to-skip, manual scroll + return.

**Risks:**
- The overlap activation changes shared row logic; gate it to Performance.
- Profile availability is async; the Performance accent source may switch mid-song. Resolve it once per song.
- Waves in PHONEME highlight mode interact with the existing letter lifts; make the spec's `LyricMotion` the single source.
```

---

## 3. Assumptions (confirm before running)

- **"Lyrics settings" = Settings → Player & Lyrics → "Lyrics display" subsection.** The app has no standalone Lyrics settings page; the Lyrics category was folded into Player & Lyrics. *(Prompt 2, step 3)*
- **Clean replaces today's default look for everyone who hasn't picked a style.** Today's synced lyrics lift and swell words, while Clean keeps letters still, so existing users will see a calmer look after updating. If you'd rather keep today's look for existing installs, migrate them to Flow or Playful instead. *(Prompt 1, step 3)*
- **The preset owns autoscroll and line emphasis.** The existing "Animated lyrics" tweak no longer switches the autoscroll spring, but it keeps parallax. The blur toggle still caps Soft's blur. *(Prompt 1, step 8)*
- **Highlight mode (Auto/Word/Letter/Line) stays as the user's granularity control.** The style is the look. Letter mode's estimated letters still animate fills, but never count as real vowel timing. *(Prompt 1, step 6)*
- **Expressive typography scales motion within each preset's limits.** It never adds lifts to a still preset. *(Prompt 1, step 7)*
- **The cover-art lyrics overlay gets the word and letter treatment only.** Its own line layout is unchanged. *(Prompt 1, step 12)*
- **Glass uses a tinted translucent capsule rather than true backdrop blur.** The lyric list has no cheap way to sample what's behind a word, and per-word blur layers would go against the performance rule. *(Prompt 4, step 3)*
- **Performance phrase grouping is off.** The lyric data has no phrase field (`songPart` is a song section), so only a hook is added. *(Prompt 5, step 4)*
- **The existing active-line card (`activePillColor`) stays in every preset.** *(Prompts 3–5)*
- **The prompts were written against `KinsBand/PixelPlayer` main @ `1ef283a`.** Your local checkout has near-identical file sizes but may have uncommitted edits, so the agent should re-check the line numbers given as `~L…`.
