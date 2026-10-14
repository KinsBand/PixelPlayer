# Camera island: song-change peek, full lyrics parity + scrolling, bottom resize, Listen button

## 1. Evidence
All paths under `app/src/main/java/com/theveloper/pixelplay/`. "Layout" = `ui/overlay/OverlayCutoutWidgetLayout.kt`.

- E1: ui/overlay/OverlayCutoutState.kt:14-27 - state has `expansionLevel, isVisible, song, isPlaying, lyrics, activeLineIndex, lyricsSyncOffsetMs, colorScheme, coversStatusBar`. No peek or skip-direction field.
- E2: ui/overlay/OverlayCutoutDimensions.kt:385-391 - `enum class CutoutExpansionLevel { COLLAPSED, LEVEL_1_SINGLE, LEVEL_2_THREE, LEVEL_3_SIX, LEVEL_4_FULL }` ("state 3" = LEVEL_3_SIX).
- E3: Layout:171-208 - `settleTo()` animates one `Animatable` height; open uses `spring(dampingRatio = 0.82f, stiffness = 360f)`, collapse `spring(DampingRatioNoBouncy, 420f)`; grows the window first, trims after.
- E4: Layout:242-271, 278 - `dragModifier` (`detectVerticalDragGestures`) is applied to the whole island Box, so any vertical drag anywhere resizes (comment at 734: "dragging anywhere on the island still resizes it").
- E5: Layout:839-857 - `SlidingText` title/artist: always `slideInVertically { it / 2 }` + `slideOutVertically { -it / 2 }` (always slides up), `tween(360/300, FastOutSlowInEasing)`, `fadeIn(tween(300))`/`fadeOut(tween(200))`.
- E6: ui/overlay/OverlayCutoutService.kt:265-267, 443-466 - `onMediaItemTransition(mediaItem, reason)` -> `handleMediaItemChanged()` updates song/lyrics only. No peek, no direction. Island skips call `mediaController?.seekToNext()/seekToPrevious()` (642-646).
- E7: ui/overlay/OverlayCutoutDimensions.kt:187 `headerHeight`; 195-206 level heights (L1 42dp, L2 104dp, L3 176dp + CONTROLS_HEIGHT, L4 full); 215 `outlineHeight = maxOf(bottomCornerRadius * 2, CONTROLS_HEIGHT)`; 240-244 `morphFraction` spans pill -> LEVEL_1 height; 250-253 `snapLevel` = nearest of `CutoutExpansionLevel.entries`.
- E8: Layout:455-470 - content reveal `((c - 0.35f) / 0.65f)` alpha + 0.94->1 scale, driven by `morphFraction`.
- E9: Layout:655-692 - island already reads `rememberLyricsDisplayPrefs()` (alignment forced CENTER), `rememberEffectiveLyricsFont`, sync offset, and `rememberLyricsImmersiveState`.
- E10: Layout:917-972 - every level renders `CoverSyncedLyrics` with `windowRadius` 1/2/3/6.
- E11: presentation/components/player/AlbumCoverLyricsOverlay.kt:224-236, 290-297 - `CoverSyncedLyrics` lays out only `targetIndex ± windowRadius` (not scrollable). It reads `useAnimatedLyrics, lineBlurActive, animatedLyricsBlurStrength, highlightMode, translation/romanization, font, textSize, alignment`; it does NOT read `LocalLyricAnimationStyle`, `LocalLyricExpression` or `LocalLyricMotion`.
- E12: presentation/components/LyricsDisplayPrefs.kt:186-230 - prefs include `expressiveTypography, adaptiveTypography, fontWeight, lineSpacing, immersiveEnabled, immersiveTimeoutMs, animationStyle`, and `lineBlurActive = useAnimatedLyrics && animatedLyricsBlurEnabled && !disableBlurAllOver`.
- E13: presentation/components/LyricsSheet.kt:2431-2459 - public `SyncedLyricsList(lines, listState, playbackPositionFlow, lyricsSyncOffset, accentColor, textStyle, onLineClick, highlightZoneFraction, highlightOffsetDp, autoscrollAnimationSpec, useAnimatedLyrics, highlightMode, animatedLyricsBlurEnabled, animatedLyricsBlurStrength, immersiveMode, lyricsAlignment, showTranslation, showRomanization, ..., onSeekTo, relayoutKey)` - a `LazyColumn` (2646); auto-scroll skips while `listState.isScrollInProgress` (2591). Reads `LocalLyricAnimationStyle` + `LocalLyricReducedMotion` (2463-2464).
- E14: presentation/components/LyricsSheet.kt:824-838, 1076-1084 - sheet provides `LocalLyricExpression` (`rememberSongExpressionProfile(song, lyrics, structure, durationMs, enabled = expressiveTypography && adaptiveTypography)`), `LocalLyricMotion`, `LocalLyricAnimationStyle provides prefs.animationStyle`, `LocalLyricReducedMotion provides systemAnimationsOff` (ANIMATOR_DURATION_SCALE == 0).
- E15: presentation/components/LyricsSheet.kt:1362-1400 - reference call of `SyncedLyricsList` from the sheet (seek via `resolveSeekPositionMs`).
- E16: presentation/viewmodel/VoiceSearchStateHolder.kt:32-33 `@Singleton class VoiceSearchStateHolder @Inject constructor`; 58-59 `isListening: StateFlow<Boolean>`; 155-170 `triggerNowPlayingListen()` (Now Playing via Shizuku, else Google song search; checks notification access first).
- E17: data/recognition/shizuku/GoogleSearchBridge.kt:79 - `launchSoundSearch()`.
- E18: presentation/components/voicesearch/VoiceSearchSheet.kt:167 - in-app Listen uses `Icons.Rounded.GraphicEq`.
- E19: Layout:975-990 - controls `AnimatedVisibility(showControls && !immersive)` for LEVEL_3_SIX/LEVEL_4_FULL with default `expandVertically() + fadeIn()` / `shrinkVertically() + fadeOut()`; 997-1058 `IslandControls` row: prev 46dp, play 54dp, next 46dp, centered, `spacedBy(14.dp)`.
- E20: ui/theme/MotionTokens.kt:14-39 - `Emphasized`, `EmphasizedDecelerate`, `EmphasizedAccelerate`, `Standard*`; `DurationShort1..4` (50-200), `DurationMedium1..4` (250-400), `DurationLong1` 400, `DurationLong2` 500.
- E21: ui/overlay/OverlayCutoutService.kt:75-96 - `@AndroidEntryPoint` service with `@Inject lateinit var` deps (can inject `VoiceSearchStateHolder`).
- E22: Layout:703-713 - level content swaps via `Crossfade(tween(220))`.
- E23: Layout:445-446 - pill outline band at the bottom: around the lyric in state 1, around the controls in states 3-4, none in state 2.

Not read: `IslandAudioReactor.kt`, `OverlayIslandTheme.kt`, `LyricExpression.kt` internals.

## 2. Improved prompt

**Goal:** Make the camera-cutout island feel like a live lyrics surface: it peeks the title/artist row on every track change with direction-aware motion, shows lyrics with every lyrics setting the user has (animation style, expressive/adaptive typography, sync animation, blur, immersive), lets the user scroll through all lyrics, resizes only from its bottom band (and header), and adds a Listen (hearing) button to the control pill.

**Scope:** camera island only.
- `ui/overlay/OverlayCutoutState.kt` (E1), `OverlayCutoutService.kt` (E6, E21), `OverlayCutoutDimensions.kt` (E7), `OverlayCutoutWidgetLayout.kt` (E3-E5, E8-E10, E19, E22).
- Reuse, don't fork: `SyncedLyricsList` (E13) and the sheet's CompositionLocals (E14). Promote `rememberSongExpressionProfile` or equivalent only if it is not already accessible.

**Current behaviour:**
- Track change only swaps text; title/artist always slide up (E5, E6). A collapsed island shows nothing.
- All levels use the windowed `CoverSyncedLyrics` (E10, E11): no scrolling, and no animation style / expressive typography / lyric motion (E11 vs E14).
- A vertical drag anywhere resizes the island (E4), so lyrics can't be scrolled.
- Control pill has prev / play / next only (E19).

**Spec:**
1. **Song-change peek + direction**
   - Add to `OverlayCutoutState`: `skipDirection: SkipDirection` (FORWARD / BACKWARD / NONE) and `peekToken: Long` (bumped on each real song change).
   - Service: in `handleMediaItemChanged`, when the song id changes, work out direction by comparing `mediaController.currentMediaItemIndex` with the last known index (lower = BACKWARD, otherwise FORWARD; wrap-around/shuffle = FORWARD). Bump `peekToken`. Don't peek on the first load (E6 line 402) or when `isVisible` turns on.
   - Layout: if `expansionLevel == COLLAPSED` and not dragging, a new `peekToken` settles height to a new `metrics.peekHeight` (= `headerHeight + statusHintHeight`, E7), holds `PEEK_HOLD_MS = 3000`, then settles back to COLLAPSED. A newer token restarts the hold. Tap on the peek opens LEVEL_1_SINGLE (current tap path). Drag from the peek works as normal.
   - Peek is not a `CutoutExpansionLevel` (keeps `snapLevel` and `contentLevel`, E7/E22, unchanged). While peeking, `morphFraction` uses `peekHeight` as its span so the surface reaches full width/colour and the header is fully revealed (E8); otherwise unchanged.
   - Only the header row shows in the peek. The level content area is not composed (`isOpen` content gated on height ≥ LEVEL_1).
   - If already open (any level): stay at that level; only the text animates.
   - `SlidingText` (E5): FORWARD = new text enters from below, old exits up (today's behaviour); BACKWARD = new enters from above, old exits down; NONE = fade only.
2. **Lyrics parity**
   - Wrap `IslandContent` in the same CompositionLocals as the sheet (E14): `LocalLyricAnimationStyle provides prefs.animationStyle`, `LocalLyricReducedMotion` (animator scale == 0), `LocalLyricExpression` from `rememberSongExpressionProfile(... enabled = prefs.expressiveTypography && prefs.adaptiveTypography)`, `LocalLyricMotion` (same `when` as the sheet).
   - Text style uses `prefs.fontWeight` and `prefs.lineSpacing` as the sheet does (E12), on top of the existing size multiplier.
   - Blur passes `prefs.lineBlurActive`-equivalent (`animatedLyricsBlurEnabled && !disableBlurAllOver`, E15) and `animatedLyricsBlurStrength`. Immersive keeps using `rememberLyricsImmersiveState` (E9).
   - Alignment stays CENTER (E9 reason: camera hole).
3. **Scrollable lyrics (states 2-4)**
   - LEVEL_2_THREE, LEVEL_3_SIX, LEVEL_4_FULL render `SyncedLyricsList` (E13), with one `LazyListState` hoisted in `IslandContent` and keyed on `song.id`, so changing level doesn't lose the scroll position or re-centre.
   - Pass `playbackPositionFlow = positionFlow`, `lyricsSyncOffset = state.lyricsSyncOffsetMs`, seek via the existing `seekToLine` maths (E9), `immersiveMode = immersive`, `lyricsAlignment = "center"`. `highlightZoneFraction` / `highlightOffsetDp` / `autoscrollAnimationSpec`: same as the sheet's values (E15), with the highlight zone centred in the list's viewport.
   - Content padding top/bottom so the first and last lines can scroll to the highlight zone; the bottom padding clears the control pill in states 3-4.
   - The user can scroll to any line; auto-scroll pauses while scrolling and resumes as the sheet does (E13 line 2591). A touch also resets immersive (existing `resetsLyricsImmersive`).
   - LEVEL_1_SINGLE keeps `CoverSyncedLyrics` with `windowRadius = 1` (E10), now under the same CompositionLocals.
   - No synced lyrics: keep current fallback text (E10).
4. **Resize from the bottom**
   - Move `dragModifier` (E4) off the whole Box onto: the header row and the bottom outline band (`outlineHeight`, E7/E23) in every open state. The collapsed pill and the peek keep the whole surface as drag/tap target.
   - The lyrics list area only scrolls; its vertical drags never resize.
   - Buttons in the band keep tapping normally (drag starts only past touch slop, as today).
   - Same snapping, rubber-banding, haptic and springs as now (E3, E4).
5. **Listen (hearing) button**
   - Inject `VoiceSearchStateHolder` (E16) into the service (E21); pass `isListening` and `onListen = { voiceSearch.triggerNowPlayingListen() }` down to `IslandControls`.
   - In LEVEL_3_SIX and LEVEL_4_FULL, add a 46dp `ControlButton` at the right end of the control pill, `Icons.Rounded.Hearing`, `contentDescription = "Identify song playing nearby"`, container `onPrimaryContainer.copy(alpha = 0.12f)`, tint `onPrimaryContainer` (matches prev/next, E19). Prev/play/next stay centred; the Listen button is aligned to the end with the same horizontal inset as the header.
   - While `isListening`: container switches to `primary`, icon to `onPrimary`, and a gentle alpha pulse.
   - Hides with the controls in immersive (E19).

**Motion:**
- Peek enter: height via `settleTo` open spring (E3, gesture/morph = spring). Header reveal follows the derived morph (E8).
- Peek exit: collapse spring (E3), after `PEEK_HOLD_MS`.
- Title/artist enter: `slideInVertically` ± half height + `fadeIn`, `tween(MotionTokens.DurationMedium4, easing = MotionTokens.EmphasizedDecelerate)`. Exit: `tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedAccelerate)` (250 ≈ 62% of 400). Title and artist share these durations.
- Listen state change: `animateColorAsState(tween(MotionTokens.DurationMedium2, easing = MotionTokens.Emphasized))`. Pulse: `infiniteRepeatable(tween(MotionTokens.DurationLong2 * 2, easing = MotionTokens.Standard), RepeatMode.Reverse)` on alpha 1 -> 0.6, drawn in `graphicsLayer`.
- Controls show/hide (E19): give them explicit specs: enter `tween(DurationMedium2, EmphasizedDecelerate)`, exit `tween(DurationShort4, EmphasizedAccelerate)`; the Listen button moves with the row.
- Lyrics: the list's own animation-style specs (E13); island adds none.
- Interruption: any drag or tap during a peek cancels the hold and continues from the current height (`motion.job?.cancel()`, E3). A second skip mid-peek restarts the hold without re-animating height; `AnimatedContent` retargets from the current text position. User scroll interrupts auto-scroll; the list resumes from where it is.
- Reduced motion (animator scale 0): no peek height animation (snap), hold still applies; text crossfades without slide; Listen pulse off (static `primary` container); lyrics follow `LocalLyricReducedMotion`.

**Accessibility:**
- Listen and all controls ≥ 46dp visual with 48dp touch target; Listen has a content description and `Role.Button`; state "Listening" announced via `stateDescription`.
- Peek: the header announces "Now playing: {title} by {artist}" as a polite live region.
- Lyric colours stay `resolveCoverLyricsColors` on `primaryContainer` (E9).

**Don't:**
- No new easing curves or hardcoded hex; use `MotionTokens` (E20) and `MaterialTheme.colorScheme` roles. Dp sizes only where they match existing control sizes (E19).
- Don't add PEEK to `CutoutExpansionLevel`.
- Don't fork `SyncedLyricsList` or copy its logic into the island.
- Don't change the lyrics sheet, cover overlay or the voice search sheet.
- Don't resize the window per frame (E3 rule).

**Acceptance criteria:**
- [ ] Collapsed island + skip next: grows to the title/artist row, text slides up, shrinks back after ~3s.
- [ ] Skip previous: same peek, text slides down.
- [ ] Two quick skips: one peek, hold restarts, no flicker.
- [ ] Open island + skip: stays at its level, direction-aware text slide.
- [ ] Changing Animation style, expressive/adaptive typography, animated lyrics, blur on/off/strength, highlight mode, translation, romanization, text size, font, weight, line spacing, immersive in the app changes the island the same way.
- [ ] States 2-4: can scroll to the first and last line; auto-scroll resumes after scrolling stops; tapping a line seeks.
- [ ] Dragging on the lyrics scrolls; dragging the bottom band or header resizes; buttons still tap.
- [ ] States 3 and 4 show the hearing button at the right of the control pill; tap starts Now Playing listen (or Google song search fallback); it highlights while listening.
- [ ] Animator scale 0x and 0.5x, light/dark, several album-art palettes.
- [ ] `cmd.exe /c "set JAVA_HOME=C:\Program Files\Android\Android Studio2\jbr&& gradlew.bat assembleDebug"` passes.

## 3. Plan

1. Confirm `Icons.Rounded.Hearing` resolves (material-icons-extended) and that `rememberSongExpressionProfile` and the sheet's highlight/autoscroll values (E14/E15) are reachable from `ui/overlay`; widen visibility to `internal` if needed - LyricsSheet.kt, lyrics/LyricExpression.kt - done when: an import compiles in Layout.
2. Add `SkipDirection` enum + `skipDirection`, `peekToken` to `OverlayCutoutState` - OverlayCutoutState.kt (E1) - done when: builds.
3. Service: track last `currentMediaItemIndex`; on song-id change set direction and bump `peekToken`, skip on first load - OverlayCutoutService.kt (E6) - done when: log shows FORWARD/BACKWARD per skip.
4. Add `peekHeight` and a peek-aware `morphFraction(height, peeking)` - OverlayCutoutDimensions.kt (E7) - done when: peek reaches c = 1.
5. Layout: `LaunchedEffect(state.peekToken)` peek settle -> hold `PEEK_HOLD_MS` -> collapse, cancellable by drag/tap/level change; gate level content on height ≥ LEVEL_1 - Layout (E3, E8) - done when: AC 1 and 3 pass.
6. `SlidingText(direction)` with MotionTokens specs and reduced-motion fade - Layout (E5, E20) - done when: AC 2 and 4 pass.
7. Wrap `IslandContent` in the four CompositionLocals; apply `fontWeight` / `lineSpacing` to the island text style - Layout (E9, E12, E14) - done when: animation style change shows on the state 1 line.
8. Hoist a `LazyListState` keyed on song id; replace the LEVEL_2-4 `CoverSyncedLyrics` branches with `SyncedLyricsList` using the sheet's parameters, centre alignment and padding - Layout (E10, E13, E15) - done when: AC 6 passes.
9. Move `dragModifier` to the header row and bottom outline band (plus whole surface when collapsed/peeking) - Layout (E4, E23) - done when: AC 7 passes.
10. Give controls `AnimatedVisibility` explicit MotionTokens specs - Layout (E19, E20) - done when: show/hide uses the new specs.
11. Inject `VoiceSearchStateHolder`; collect `isListening` into the layout; add the Listen `ControlButton` at the row's end with colour animation, pulse, accessibility, reduced motion - OverlayCutoutService.kt (E16, E21), Layout (E19) - done when: AC 8 passes.
12. Verify - build command; then the manual checks below.

**Verification:** build command passes. Manual, one per AC: collapsed skip next/prev; double skip; open-island skip; toggle each lyrics setting in the app and watch the island; scroll to first/last line in states 2-4, wait for auto-scroll resume, tap-to-seek; drag lyrics vs bottom band vs header; Listen in 3 and 4 (with and without notification access / Shizuku); interrupt a peek with a tap and a drag; animator 0x and 0.5x; light/dark; three album palettes; status-bar-covered and plain overlay windows (`coversStatusBar`, E1).

**Risks:**
- Window sizing: peek is a new resting height; `windowFor`/`updateTouchWindow` assume COLLAPSED = small touch strip (E3, E6 line 752). Peek must grow the window first and collapse it after, or the header gets clipped / touches miss.
- `SyncedLyricsList` uses `collectAsStateWithLifecycle` and snapper; inside the service's ComposeView the lifecycle owner must be RESUMED or it won't update (E13).
- Gesture conflict: the list consumes vertical drags; LEVEL_1 has no list, so its bottom band must still resize (E4).
- `rememberSmoothPosition` runs only while `isOpen` (E9); the list uses `positionFlow` directly, so check it isn't jittery at the service's coarse tick rate.
- Performance: a LazyColumn with blur in an always-on overlay; check frame time in state 4 with blur at max.
- Direction from `currentMediaItemIndex` is wrong under shuffle (index order ≠ play order) - FORWARD fallback hides this but won't be perfect.

## 4. Assumptions
- `Icons.Rounded.Hearing` is available (extended icons); otherwise `Icons.Rounded.GraphicEq` as used by the in-app Listen (E18). Step 1, 11.
- `rememberSongExpressionProfile` needs song structure; the island passes `null` / no structure if it isn't available in the service (adaptive typography still uses energy/mood/timing). Step 7.
- Peek hold 3000ms and the peek doesn't show while the app's own full player is in front - not checked; default is to always peek. Step 5.
- "State three" = LEVEL_3_SIX (E2), and you chose to also show it in state 4.
