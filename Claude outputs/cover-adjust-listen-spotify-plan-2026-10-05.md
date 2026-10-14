# Cover Adjust redesign, Listen capture, Spotify mirror: prompts and plans (2026-10-05)

This doc has three separate prompts, one per area, each with its own plan:

- **A.** The Filters panel redesign in Adjust.
- **B.** Listen and Hum that collect Google's result in the background.
- **C.** Mirroring what PixelPlayer plays to your Spotify activity, on a free account.

Run them in that order. They don't depend on each other.

Your answers so far:

- Removing a filter: an "Original" card at the start of each row, and dragging strength to 0 also clears it.
- Filters open: one centred Apply pill.
- Listen: a brief Google popup whose result is read automatically, by Shizuku or by an accessibility service.
- Spotify: a free way to show your PixelPlayer song as Spotify activity.

---

## 1. Evidence

All paths are under `app/src/main/java/com/theveloper/pixelplay/`.

**Cover editor**

| # | Location | What it shows |
|---|---|---|
| E1 | `presentation/screens/CreatePlaylistScreen.kt:1462` | `PlaylistCoverAdjustContent` is the Adjust screen. Its KDoc says opening filters "slides the viewport up, reveals era/mood/mono presets and turns Done into Apply". |
| E2 | `CreatePlaylistScreen.kt:1472,1510-1516` | `viewportBias` animates to `-1f` while filters are open. The viewport is `BoxWithConstraints(Modifier.weight(1f) …, contentAlignment = BiasAlignment(0f, viewportBias))`. The cover moves to the top, but the viewport still fills all the free height, so the panel sits at the bottom with empty space above it (the negative space you pointed out). |
| E3 | `CreatePlaylistScreen.kt:1551` | The cover is clipped with `RoundedCornerShape(32.dp)`, a hardcoded corner. |
| E4 | `CreatePlaylistScreen.kt:1651-1665` | The filters panel is an `AnimatedVisibility(visible = showFilters)` using `expandVertically(expandFrom = Top, editLayoutSpring())` + `fadeIn(tween(220, delay 90))`. Exit is `shrinkVertically` + `fadeOut(tween(120))`. It calls `CoverFiltersPanel(... selectedFilterId = focused?.filterId, onFilterSelect = editor::setFocusedFilter)`. |
| E5 | `CreatePlaylistScreen.kt:1668-1755` | The controls row holds Change (`FilledTonalButton`, 56dp, `CircleShape`), a Done/Apply `Button` (`weight(1f)`, colour animates primary→tertiary, `AnimatedContent` label "Done"↔"Apply", `Icons.Rounded.DoneAll`) and Filters (`Icons.Rounded.Tune`). Apply runs `onShowFiltersChange(false)` and nothing else. |
| E6 | `CreatePlaylistScreen.kt:743` | `editLayoutSpring()` = `spring(dampingRatio = 0.85f, stiffness = StiffnessMediumLow)`. Every layout move on this screen uses it. |
| E7 | `CreatePlaylistScreen.kt:1845-1861` | The category tabs are a `horizontalScroll` `Row` of `FilterChip(shape = CircleShape)` over `CoverFilters.categories`. |
| E8 | `CreatePlaylistScreen.kt:1863-1893` | Presets are an `AnimatedContent(categoryId)` wrapping a `LazyRow` of `CoverFilterCard`. Line 1888, `onFilterSelect(if (isSelected) null else preset.id)`, means a second tap clears the filter. |
| E9 | `CreatePlaylistScreen.kt:1897-1980` | `CoverFilterCard` is 76dp wide, uses `RoundedCornerShape(16.dp)`, a 3dp primary border when selected, scale 0.94→1 on a bouncy spring, and a check badge in the corner. The label uses `labelMedium`. |
| E10 | `presentation/components/CoverFilters.kt` | `CoverFilterPreset(id, label, categoryId, matrix, effect)` has `activeMatrix` and `activeEffect`. Categories are Eras, Mood, Mono, Time and Prints. Matrix helpers `offset`, `channels`, `contrast`, `saturation`, `brightness` and `concat` are private. `effectsSupported` = API 33+. |
| E11 | `presentation/components/CoverFilterShader.kt:42-61,342,369,396` | The AGSL uniforms include `vignette`, `grain`, `bloom` and others. `Modifier.coverFilterEffect(preset)` is the live preview. `CoverFilterRenderer.bake(source, preset)` bakes on the GPU through `node.setRenderEffect(...)` and returns null below 33. |
| E12 | `presentation/components/CoverArtLayers.kt:52-77,259,265,277,328,375` | `CoverArtLayer` has `filterId: String?`, plus `hasEdits` and `reset(keepFilter)`. `isAdjusted`, `needsComposite = overlays.isNotEmpty() \|\| base?.isUniform == false`, a change signature string that includes `filterId`, and `setFocusedFilter` all live in `CoverEditorState`. The compositor bakes each layer with `CoverFilterRenderer.bake(source, preset)` and falls back to the matrix paint. |
| E13 | `presentation/components/CoverLayerStrip.kt:221`, `ImageCropView.kt:391` | Both other preview sites resolve the look with `CoverFilters.byId(layer.filterId)`. |
| E14 | `CreatePlaylistScreen.kt:281-286`, `PlaylistViewModel.kt:713-714`, `LibraryScreen.kt:1673`, `PlaylistDetailScreen.kt:1156` | `PlaylistCoverImageEdits(flipHorizontal, filterId, rotationDegrees)` is the only edit state that gets saved. A plain photo is saved with `filterId` and baked in `PlaylistViewModel`. |
| E15 | `ui/theme/MotionTokens.kt` | Defines `Emphasized`, `EmphasizedDecelerate`, `EmphasizedAccelerate` and `Standard*`. Durations are Short1-4 (50/100/150/200), Medium1-4 (250/300/350/400) and Long1-2 (400/500). |
| E16 | `app/build.gradle.kts:94,237` | `minSdk = 30`, and `material-icons-extended` is on the classpath. |

**Listen and Hum**

| # | Location | What it shows |
|---|---|---|
| E17 | `data/recognition/shizuku/GoogleSearchBridge.kt` | `launchHumOrSingSearch()` tries Shizuku `am start -n …/AudioSearchActivity`, then `am start -a …INTENT_SOUND_SEARCH`, then plain intents, then google.com. It only checks `isSuccess` (the exit code). `ensureNotificationListenerAccess()` already grants itself silently through Shizuku with `cmd notification allow_listener`. |
| E18 | `presentation/viewmodel/VoiceSearchStateHolder.kt` | `triggerGoogleHumOrSing()` and `triggerNowPlayingListen()` (Shizuku first fires `triggerAmbientDetection()` and waits `ON_DEVICE_WAIT_MS = 8000` for `newlyHeard`, then opens Google). `beginListening` has `LISTEN_TIMEOUT_MS = 30000`. `onHostResumed()` stops listening because "its result can't come back to us". `resolveAndShowSong(title, artist)` shows the match card. The `init` block only collects `newlyHeard` while the sheet is open. |
| E19 | `data/recognition/RecentlyHeardRepository.kt:30,91` | `enum RecentlyHeardSource { NOW_PLAYING, NOW_PLAYING_HISTORY, SONG_SEARCH }`. Signature: `record(title, artist, heardAtEpochMs, source, announce)`. |
| E20 | `data/recognition/shizuku/PixelNowPlayingBridge.kt` | `triggerAmbientDetection()` sends `am broadcast -a com.google.android.as.ambientmusic.ACTION_TRIGGER_DETECTION`, Shizuku only. |
| E21 | `data/recognition/ambient/NowPlayingNotificationListener.kt` | Captures Now Playing notifications into Recently heard. It never sees Google's song search results. |
| E22 | `data/recognition/shizuku/ShizukuManager.kt:84-117` | `executeShell(cmd)` runs `sh -c` through Shizuku and returns `Result<String>` from the exit code. |
| E23 | `ui/overlay/OverlayCutoutService.kt:674`, `data/radio/RadioNowPlaying.kt:215`, `MainActivity.kt:962,1401-1402` | Places that call `triggerNowPlayingListen()` / `onHostResumed()`. The island's Listen runs from a service, so it's already "background". |
| E24 | `AndroidManifest.xml:163-177`, `res/xml/cutout_accessibility_service_config.xml` | `CutoutIslandAccessibilityService` exists with `canRetrieveWindowContent="false"` and no package filter. |
| E24a | Your screenshot of a found song (`Claude outputs/listen-result-reference/found-song-google-results.png`) | Google has no special result screen. It lands on an ordinary Google app search results page: the search box holds "Whirr Color Change" (artist and title with no separator), the song panel has the heading "Color Change" and the line "Song by Whirr · 2012", there's a YouTube card ("Color Change", "Whirr - Topic"), and the bottom nav reads Home / Search / Notifications / Activity. |

**Spotify**

| # | Location | What it shows |
|---|---|---|
| E25 | `data/spotify/web/SpotifyWebSession.kt:20-46` | A web-player session from the `sp_dc` cookie. It trades the cookie for a token at `open.spotify.com/api/token`, with no developer app needed. |
| E26 | `presentation/screens/ServiceWebLogin.kt:30-38` | The Spotify WebView sign-in stays on accounts.spotify.com because "going on to open.spotify.com makes phones try to open the Spotify app instead". |
| E27 | `data/spotify/SpotifyAuthManager.kt:43` | OAuth scopes have no `user-modify-playback-state` or `user-read-playback-state`. |
| E28 | `data/spotify/SpotifyRepository.kt:76-90` | `findTrackId(title, artist, isrc)` searches through the web session when one exists. |
| E29 | `data/spotify/web/SpotifyFriendActivitySource.kt`, `SpotifySocialRepository.kt:71-72` | Friends' activity is read from `spclient…/listening-activity/v1/feed`, which has an `isPlaying` flag, and the buddylist. It is read-only. |
| E30 | `presentation/screens/FriendsScreen.kt:249-258` | The Friends top bar actions are the Add button (`FilledTonalIconButton`) and `ActiveFriendsPill`. |

I read all the files above directly. I haven't seen Google's result screens or Spotify's web player, so those need a short check on the device first (A has none of these; see B step 1 and C step 1).

---

## 2A. Improved prompt: Filters panel redesign (Adjust screen)

**Goal:** When Filters is open in Adjust, the panel should sit directly under the cover, like Google Photos. That means:

- Section tabs and preset row sit directly under the cover.
- Presets get an adjustable strength.
- A new Adjust tab holds Google-Photos-style tools.
- A ruler slider pill with an icon or value on the left and reset on the right.
- A single centred Apply pill.

**Scope:** `PlaylistCoverAdjustContent`, `CoverFiltersPanel` and `CoverFilterCard` in `CreatePlaylistScreen.kt` (E1, E4, E7-E9). The look model in `CoverFilters.kt` (E10) and `CoverArtLayers.kt` (E12). The shader and bake in `CoverFilterShader.kt` (E11). Previews in `CoverLayerStrip.kt` and `ImageCropView.kt` (E13). Nothing else.

**Current behaviour:**

- When filters open, the cover moves to the top, but the viewport still takes `weight(1f)` (E2). The tabs and presets stay at the bottom with empty space between them and the cover.
- Tapping the selected card clears the filter (E8). Filters always run at full strength. There are no per-tool adjustments.
- The controls row keeps Change, Apply and Filters (E5).

**Spec:**

1. **Layout with filters open.** Order, top to bottom:
   - Cover.
   - 12dp gap.
   - Section tabs.
   - 12dp gap.
   - Preset/tool row.
   - Slider slot (only when open).
   - Flexible spacer that takes all the leftover height.
   - Apply pill.

   The viewport stops taking `weight(1f)` while filters are open and takes the cover's square height. With filters closed, the layout is exactly as it is now. The move uses one shared `editLayoutSpring()` (E6) for the viewport, panel and spacer, so they travel together.
2. **Section tabs.** Keep the `FilterChip` row (E7) and add an **Adjust** category as the first tab. The category order becomes Adjust, Eras, Mood, Mono, Time, Prints. Adjust is first so the tools are one tap away; this is an assumption.
3. **Preset row.**
   - Each preset category starts with an **Original** card (shows the unfiltered thumbnail). Selecting it sets `filterId = null`.
   - First tap on a preset selects it at strength 100.
   - A selected card shows a scrim over the thumbnail with the strength number ("100") centred in `titleMedium`, in place of the check badge (E9). The scrim is `scrim` at about 40% alpha, with the number in `onPrimary` on a `primary` pill, or `inverseOnSurface`.
   - Tapping the selected card again opens the strength slider; it no longer clears (replaces E8).
   - Dragging strength to 0 clears the filter (`filterId = null`, strength resets to 100).
4. **Adjust tab tools.** In order: Brightness, Contrast, Ultra HDR, Tone, White point, Highlights, Shadows, Black point, Saturation, Warmth, Tint, Blue tone, Vignette, then **Pop, Sharpen, Denoise**. **Portrait Light and Skin tone are left out.**
   - Each tool is a tile like your screenshots: label above (`labelLarge`, `onSurface`) and an icon in a rounded tile (`surfaceContainerHigh`; `primaryContainer` with `onPrimaryContainer` when selected), using the app's shape tokens.
   - Tapping a tile selects it and opens the slider straight away (a tool has nothing to apply at its default).
   - A tile whose value isn't at its default shows the value instead of the icon, so you can see what's changed.
   - Ranges:
     - −100..100, default 0: Brightness, Contrast, Tone, White point, Highlights, Shadows, Black point, Saturation, Warmth, Tint, Blue tone, Vignette (−100 = white vignette, 100 = dark).
     - 0..100, default 0: Ultra HDR, Pop, Sharpen, Denoise.
   - Adjustments stack on top of the selected preset, in this order: preset at strength → adjust matrix → adjust shader.
   - On API 30-32 (minSdk 30, E16; shader needs 33, E10), tools that need the shader are hidden: Ultra HDR, Tone, Highlights, Shadows, Blue tone, Vignette, Pop, Sharpen, Denoise. Matrix-only tools still show.
5. **Slider pill.**
   - Full-width pill on `surfaceContainer`, `CircleShape` (as used on this screen, E5), 64dp tall, 16dp side margins.
   - **Left slot:** the tool's icon (for preset strength, the `Tune` icon), until the value leaves its default. Then it shows the signed value in `titleMedium`.
   - **Centre:** a tick ruler that scrolls under a fixed centre indicator line. Ticks are `onSurfaceVariant` at low alpha, with a major tick every 25. Ticks between the default and the current value are `onSurface` and taller.
   - **Right slot:** a reset `IconButton` with 48dp target and `Icons.Rounded.SettingsBackupRestore` (the circle-arrow-with-dot icon). It's dimmed and disabled at the default value. It resets this tool only; for preset strength it resets to 100.
   - Drag and fling move the value in whole steps, clamped to the range, with a light haptic tick on each major tick and at the default.
   - Switching tile or card while the slider is open moves the ruler to the new tool's value; it doesn't open a new slider.
6. **Apply pill.**
   - While filters are open, Change and Filters collapse away and Apply sits centred alone. It keeps its 56dp height, wraps its content with a minimum width of about 160dp, and keeps the existing tertiary colours and the "Apply" label with `DoneAll` (E5).
   - Apply closes the panel as it does now (E5). Edits are live on the layer, so there's nothing extra to commit.
   - Closing filters brings Change and Filters back.
7. **Model and rendering.**
   - `CoverArtLayer` gains `filterStrength: Float = 1f` and `adjust: CoverAdjustments = CoverAdjustments.None`, an immutable data class with one Float per tool.
   - Update `hasEdits`, `reset()` and the change signature (E12) to include them.
   - `reset(keepFilter = true)` keeps strength and adjustments too.
   - Add a resolved look, `CoverLook(preset, strength, adjust)`, with:
     - `matrix`: the preset's `activeMatrix` lerped toward identity by strength, concatenated with the adjust matrix. The private helpers in `CoverFilters` (E10) are reused, made internal.
     - `effect`: preset effect strengths scaled by strength, plus the adjust shader stage.
   - Previews: `CoverFilterCard`, `CoverLayerStrip:221`, `ImageCropView:391` and the canvas use the layer's look in place of `byId(filterId)` (E13).
   - `coverFilterEffect` and `CoverFilterRenderer.bake` (E11) gain `CoverLook` overloads. The compositor (E12) and the `PlaylistViewModel` save (E14) use them.
   - The adjust stage is a separate AGSL shader, chained with `RenderEffect.createChainEffect`, so the 52 preset looks stay untouched.
8. **Saving.** `PlaylistCoverImageEdits` (E14) saves only `filterId`. So a layer with strength below 100 or any adjustment makes `needsComposite` true (E12), and the cover is baked to an image on save. No database change.

**Motion:** Use MotionTokens (E15) and `editLayoutSpring()` (E6) only.

- **Panel layout** (viewport, panel, spacer): `editLayoutSpring()`, shared.
- **Change/Filters collapsing:**
  - Exit: `shrinkHorizontally(editLayoutSpring())` + `fadeOut(tween(DurationShort3, easing = EmphasizedAccelerate))`.
  - Enter: `expandHorizontally(editLayoutSpring())` + `fadeIn(tween(DurationMedium1, delayMillis = DurationShort2, easing = EmphasizedDecelerate))`.
  - The Apply pill's position follows the same spring.
- **Slider slot:**
  - Enter: `expandVertically(expandFrom = Top, editLayoutSpring())` + `fadeIn(tween(DurationMedium1, easing = EmphasizedDecelerate))`.
  - Exit: `shrinkVertically(editLayoutSpring())` + `fadeOut(tween(DurationShort3, easing = EmphasizedAccelerate))`.
  - It closes when you tap the same tile or card again, change category, or press Apply.
- **Strength overlay on a card:**
  - Enter: `fadeIn` + `scaleIn(initialScale = 0.8f)`, `tween(DurationShort4, easing = EmphasizedDecelerate)`.
  - Exit: `tween(DurationShort3, easing = EmphasizedAccelerate)`.
  - The number itself updates live, without animating.
- **Left slot icon ↔ value:** `AnimatedContent`, fade through, in `tween(DurationShort4, Emphasized)` and out `tween(DurationShort2, EmphasizedAccelerate)`.
- **Ruler retarget** (switching tool or reset): an `Animatable` going to the new value with `tween(DurationMedium2, easing = Emphasized)`.
- **Interruption:**
  - A drag cancels any running ruler animation and continues from the current value (no snap).
  - Toggling filters mid-animation reverses from the current position, which the springs already do.
- **Reduced motion:** with animator duration scale 0, every expand or shrink becomes an instant size change with fade only, and ruler retargets snap.

**Accessibility:**

- Tiles and cards are at least 48dp, with `Role.Button` and `stateDescription` ("Selected, 100%" or "Brightness 13").
- The ruler has `progressBarRangeInfo` and `setProgress` semantics, so TalkBack can swipe it in 1-step increments. Its content description is the tool name.
- Reset is labelled "Reset {tool}".
- The overlay number has to meet contrast on every thumbnail, which is what the scrim is for.

**Don't:**

- No hardcoded hex, sp or new dp corner radii. Use theme shapes and typography roles; the existing 32dp/16dp corners (E3, E9) aren't touched in this change.
- No new motion token file. No Portrait Light or Skin tone.
- No change to the 52 preset definitions or to the database schema. No change to the closed-filters layout.

**Acceptance criteria:**

- [ ] With filters open, the tabs sit 12dp under the cover and presets sit right under the tabs. The free space is between the panel and Apply.
- [ ] First tap on a preset applies it at 100 and shows the "100" overlay. A second tap opens the strength slider. 0 or the Original card clears it.
- [ ] The Adjust tab lists the 16 tools in order, with no Portrait Light or Skin tone. Shader-only tools are hidden on API 30-32.
- [ ] The slider shows the icon at default and the value once moved. Reset returns the tool to default and is disabled at default.
- [ ] Preview, layer strip and saved cover look the same for strength and every tool.
- [ ] With filters open, only a centred Apply pill shows. Closing filters restores Change, Done and Filters.
- [ ] `cmd.exe /c "set JAVA_HOME=C:\Program Files\Android\Android Studio2\jbr&& gradlew.bat assembleDebug"` passes.

## 3A. Plan

1. **Make the matrix helpers internal and add `identity()` and `lerp(a, b, t)`.** File: `CoverFilters.kt` (E10). Done when: it builds and all 52 presets are unchanged.
2. **Add `CoverAdjustments`, `CoverAdjustTool` and `CoverLook`.**
   - `CoverAdjustTool`: an enum with id, label, icon, range, default and `needsShader`.
   - `CoverLook.matrix`: strength lerp plus adjust matrix (brightness offset, contrast, saturation, warmth R+/B−, tint G±, black and white point as linear levels).
   - Files: new `CoverAdjust.kt` next to `CoverFilters.kt`.
   - Done when: a unit test shows strength 1 with default adjustments equals `activeMatrix`, and strength 0 equals identity.
3. **Write the adjust AGSL shader** (Tone, Highlights, Shadows, Ultra HDR, Blue tone, Vignette, Pop, Sharpen, Denoise; the last three sample neighbours with `image.eval`). Add `previewEffect(look)` and `bake(source, look)` that chain it after the preset stage. File: `CoverFilterShader.kt` (E11). Done when: an all-default look bakes pixel-identical to today's preset bake.
4. **Add `filterStrength` and `adjust` to `CoverArtLayer`.** Update `hasEdits`, `reset` and the signature. Add `CoverEditorState.setFocusedStrength` and `setFocusedAdjust`, and make `needsComposite` true when either is non-default. File: `CoverArtLayers.kt` (E12). Done when: undo/reset clears both, and the unsaved-changes check picks them up.
5. **Switch the previews and save paths to `CoverLook`.** Files: `CoverFilterCard` (E9), `CoverLayerStrip.kt:221`, `ImageCropView.kt:391` (E13), compositor `CoverArtLayers.kt:375` (E12), `PlaylistViewModel.kt:714` (E14). Done when: the preview matches the saved JPEG at strength 40 with Vignette 60.
6. **Rework the filters-open layout:** cover, tabs, row, slider slot, spacer, Apply, all on `editLayoutSpring()`. File: `PlaylistCoverAdjustContent` (E2, E4). Done when: the screenshot comparison shows no gap between cover and tabs, and filters-closed is unchanged.
7. **Collapse Change and Filters around a centred Apply.** File: controls `Row` (E5). Done when: open and close look right and Apply stays tappable mid-animation.
8. **Add the Adjust category, the Original card, and the new card tap behaviour** (select, then second tap opens slider). Add the strength overlay. Files: `CoverFiltersPanel` and `CoverFilterCard` (E7-E9). Done when: it matches the Preset row spec.
9. **Build the `CoverAdjustToolTile` row**: label above, icon or value tile, selected colours, shader tools hidden below 33. File: `CreatePlaylistScreen.kt`. Done when: all 16 tools show on API 33+, and the matrix-only tools show on API 30.
10. **Build `CoverAdjustSlider`**: ruler, left icon/value, reset, haptics, `Animatable` retarget, drag interrupt. File: new `presentation/components/CoverAdjustSlider.kt`. Done when: it matches the Slider spec.
11. **Reduced motion and semantics** for the slider, tiles and cards. Done when: TalkBack can adjust the ruler, and at animator scale 0 nothing slides.
12. **Verify:**
    - Build command passes.
    - On device:
      - Enter and exit filters.
      - Toggle mid-animation.
      - Back gesture closes filters (existing `BackHandler`).
      - Animator scale 0x and 0.5x.
      - Light and dark, with a dynamic colour wallpaper.
      - A layered cover, and a flipped or rotated layer.
      - API 30 emulator (shader tools hidden).
      - Save, then reopen.

**Risks:**

- **Live slider cost.** Dragging rebuilds the RenderEffect every frame. The preset-id/size cache in the shader (E11) doesn't work for continuous values, so update uniforms on one cached `RuntimeShader` and recreate only the RenderEffect.
- **Re-editing.** Composites are baked (E12, E14), so strength and adjustments aren't re-editable after save. The preset itself is still re-editable when untouched. A later database change could fix this.
- **Thumbnail layout.** The Original card and overlay change the 76dp card layout (E9). Recheck long labels such as Melancholy and Cyanotype.
- **Viewport height.** The viewport no longer fills the height when filters are open (E2), so check that the layer strip spacing (`CoverLayerStripHeight`) still centres correctly.

---

## 2B. Improved prompt: Listen and Hum that collect Google's result in the background

**Goal:** When Listen or Hum opens Google's song search (from the app, the island or radio, E23), PixelPlayer reads the result off Google's screen, saves it to Recently heard, shows the match, and closes Google, so you end up back where you were. This works with or without Shizuku.

**Scope:** `GoogleSearchBridge` (E17), `VoiceSearchStateHolder` (E18), a new accessibility service, and the manifest. The island, sheet UI and Now Playing listener (E21) are unchanged.

**Current behaviour:**

- Google opens and the result stays inside Google. On return, `onHostResumed()` gives up (E18).
- With Shizuku, Listen first tries Pixel's on-demand detection for 8 seconds (E18, E20).

**Spec:**

1. **Reader service.** New `SongSearchResultReader`, an `AccessibilityService` separate from the island's service (E24 stays `canRetrieveWindowContent=false`).
   - Config: `packageNames="com.google.android.googlequicksearchbox"`, `canRetrieveWindowContent="true"`, `typeWindowStateChanged|typeWindowContentChanged`, `notificationTimeout` about 300ms.
   - It does nothing unless a search is armed.
2. **Arming.** `VoiceSearchStateHolder` gains `pendingExternalSearch: StateFlow<ArmedSearch?>` (mode, launchedAt, deadline = launch + `LISTEN_TIMEOUT_MS`).
   - `beginListening` arms it right before `launchHumOrSingSearch()` / `launchSoundSearch()` (E18).
   - It disarms on a result, timeout, `stopListening` or `closeSheet`.
3. **Reading.** A match ends on a normal Google search results page (E24a), so the reader waits for that page while a search is armed, then walks `rootInActiveWindow`:
   - **Main signal:** a text node matching `^Song by (.+?)(?: · \d{4})?$` gives the artist ("Whirr"). The nearest heading text before it gives the title ("Color Change").
   - **Backup:** the YouTube card's title and the "… - Topic" channel, with " - Topic" removed.
   - **Last resort:** the search box text ("Whirr Color Change"). The split between artist and title is unknown, so pass it to `VoiceSearchResolver` as free text and accept only a confident match.
   - A results page for anything other than a song (no "Song by" line and no Topic card) counts as no match.
   - Debounce until the same pair is read twice, about 500ms apart, because the page loads in stages.
4. **Delivery** (in this order):
   1. `recentlyHeardRepository.record(title, artist, source = SONG_SEARCH, announce = true)` (E19).
   2. `voiceSearchStateHolder.resolveAndShowSong(title, artist)` (E18).
   3. Disarm.
   4. **Return to the previous app.** Pressing Back on the results page would land on Google's home, not your app (E24a). So:
      - When the search is armed, record which app was in front: the reader's last window event from a package other than Google, or with Shizuku, `dumpsys activity activities | grep -m1 topResumedActivity`.
      - After the capture, bring that app back with `getLaunchIntentForPackage(pkg)` and `FLAG_ACTIVITY_NEW_TASK`. An accessibility service is allowed to start activities from the background.
      - If PixelPlayer was in front, `MainActivity` comes back and the match card is already showing.
      - Google's results stay in Recents.
   - `onHostResumed()` doesn't stop listening while a search is armed and its deadline hasn't passed (E18).
5. **With Shizuku:**
   - If the reader isn't enabled, enable it silently. Run `settings get secure enabled_accessibility_services`, append our component (never overwrite, because the island service and others live there), then `settings put … ` and `settings put secure accessibility_enabled 1`. This mirrors `ensureNotificationListenerAccess` (E17).
   - If that fails, fall back to polling `uiautomator dump /data/local/tmp/pp_gsa.xml && cat /data/local/tmp/pp_gsa.xml` every second through `executeShell` (E22) while Google is on top, and parse the same way.
6. **Without Shizuku:** On the first Listen or Hum, show a one-time dialog explaining why. Then open the reader's accessibility settings. On Android 13+ sideloads, mention "App info → ⋮ → Allow restricted settings". If it's still not enabled, keep today's behaviour.
7. **Launch check.** In `GoogleSearchBridge`, treat `am start` output containing "Error" as a failure, even with exit 0, so the fallbacks run (E17).

**Motion:** None. This adds no UI motion beyond the existing match card.

**Accessibility and privacy:**

- The service description says plainly that it only reads Google's song search screen, only right after you tap Listen or Hum.
- Nothing from other windows is read or logged.

**Don't:** Don't widen the island accessibility service (E24). Don't keep the reader active outside an armed search. Don't overwrite `enabled_accessibility_services`.

**Acceptance criteria:**

- [ ] Hum from the app, with Shizuku and with the reader enabled by hand: the song lands in Recently heard as `SONG_SEARCH`, the match card shows, and Google closes by itself.
- [ ] Listen from the island while in another app: you end up back in that app and the song is saved.
- [ ] No result within 30 seconds: listening stops, Google is left alone, and nothing is saved.
- [ ] The reader isn't enabled and there's no Shizuku: behaviour is the same as today, plus the one-time explainer.
- [ ] Build command passes.

## 3B. Plan

1. **On-device check (no code).** The result page is now known from your screenshot (E24a). With Shizuku, run `uiautomator dump` on a results page like it, once from Listen and once from Hum. Save the XML under `app/src/test/resources/gsa/`. Done when: the dump shows the "Song by …" line and the heading as accessibility text nodes, not hidden inside the WebView. If they're missing, the search box text becomes the main signal.
2. **Add the arming state** to `VoiceSearchStateHolder`, change `onHostResumed`, and arm in `beginListening` (E18). Done when: arming and disarming are covered by a unit test with a fake clock.
3. **Parser.** Add `GsaResultParser.parse(nodeTree | xml): Pair<String, String>?` with unit tests against the step 1 dumps. File: new `data/recognition/shizuku/GsaResultParser.kt`. Done when: both fixtures parse.
4. **Reader service.** Add `SongSearchResultReader`, its XML config, strings and manifest entry. Deliver results as in Delivery, then return to the app recorded when the search was armed (not Back). Files: new `data/recognition/SongSearchResultReader.kt`, `res/xml/song_search_reader_config.xml`, `AndroidManifest.xml` (E24 pattern). Done when: Hum is captured with the reader enabled by hand.
5. **Shizuku silent enable,** plus the `uiautomator` polling fallback. File: `GoogleSearchBridge.kt` (E17, E22). Done when: a fresh install with Shizuku captures without visiting settings.
6. **Non-Shizuku explainer dialog** and the settings deep link. Files: `VoiceSearchSheet.kt` (sheet host) and its strings. Done when: shown once, and the deep link opens the right screen.
7. **`am start` "Error" output counts as failure.** File: `GoogleSearchBridge.kt` (E17). Done when: a missing activity falls through to the intent fallback.
8. **Verify:**
   - All acceptance criteria, with and without Shizuku.
   - Island trigger from another app (E23).
   - A Pixel with Now Playing (the 8-second path still works first, E18).
   - A non-Pixel phone.

**Risks:**

- Google can change its result layout without notice. The parser falls back to the YouTube card and then the search box text, and the fixture tests will show when it breaks.
- The "Song by" line is English text. On another phone language it needs that language's wording, or the YouTube or search box fallback.
- `uiautomator dump` can fail with "could not get idle state" on Google's animated listening screen, which is why the accessibility service comes first.
- Restricted settings on Android 13+ sideloads block enabling it by hand until you allow them.
- The back press is only sent while Google's window is still on top, so a fast user can't be sent back from their own app.

---

## 2C. Improved prompt: Show your PixelPlayer song as your Spotify activity (free account)

**The short answer:** Spotify has no API for setting "now playing" by hand. Activity only appears when a Spotify player actually plays the track:

- The official Web API playback control needs Premium, and our OAuth doesn't have the playback scopes anyway (E27).
- librespot-style embedded players also need Premium.

The free workaround is to run Spotify's own **web player** inside PixelPlayer. It's a hidden, muted WebView signed in with the `sp_dc` cookie you already connect (E25). It plays the matched track on Spotify while you listen in PixelPlayer. Free web-player accounts can pick any track (with ads), and the Spotify app doesn't need to be running.

The catches:

- It uses an unofficial path, which is against Spotify's terms, so the account could be flagged.
- It streams the audio a second time, which costs data and battery.
- During ads, your activity shows nothing.
- It hinges on whether Spotify's web player runs inside an Android WebView at all. E26 already shows phones get pushed to the app. So step 1 is a feasibility check, and if that fails, this can't be done on Free.

**Goal:** A "Show on Spotify" toggle in Friends. While it's on, whatever PixelPlayer plays is mirrored, muted, to your Spotify web-player session, so friends see it in their activity feed.

**Scope:** New `data/spotify/web/SpotifyMirrorPlayer.kt` (hidden WebView host), a mirror controller next to the playback service, the toggle in `FriendsScreen` (E30), and a setting.

**Spec:**

1. **Host.**
   - A 1×1 transparent overlay window holding a WebView, so the WebView isn't throttled. It's owned by a small foreground-scoped controller, running only while mirroring is on and PixelPlayer is playing.
   - The WebView settings:
     - Desktop Chrome user agent.
     - Cookies copied from the Spotify login cookie store (E26).
     - `PROTECTED_MEDIA_ID` granted in `onPermissionRequest` for Widevine.
     - `intent://` and `spotify:` navigations blocked, so it never jumps to the app (E26).
     - `mediaPlaybackRequiresUserGesture = false`.
2. **Following PixelPlayer playback.**
   - On a track change: use the `spotify_` id if the song came from Spotify, otherwise `findTrackId(title, artist, isrc)` (E28). Then load `open.spotify.com/track/{id}` and start playback through an injected script.
   - Mute through the web player's own volume (the script sets it to 0), so it still counts as playing.
   - Pause and resume follow PixelPlayer. Seek is followed only when the jump is over 10 seconds.
   - No match: a "Not on Spotify" status, and the mirror pauses.
3. **Friends toggle.**
   - A `FilledTonalIconButton` left of Add (E30).
   - States: Off; Starting (progress); Live (`FriendsLiveGreen` dot); No match; Ad playing; Blocked (feasibility failed).
   - One tap turns it on immediately for the current song. Long-press opens a short explainer that includes the terms and data warning.
4. **Setting.** "Mirror only on Wi-Fi", on by default. Off by default overall. Needs the Spotify web session (E25).
5. **Self check.** After starting, read your own presence in the buddylist or feed endpoints (E29) once, to confirm the status, and show Live only when it's confirmed.

**Motion:** Toggle state changes use `AnimatedContent` with `tween(DurationShort4, easing = Emphasized)` for icon and colour (E15). There's no other UI motion.

**Accessibility:** The toggle is 48dp, with `stateDescription` matching the state names, and the explainer is reachable without long-press, from an overflow item.

**Don't:**

- No playback with sound.
- No mirroring while PixelPlayer is paused.
- No plays generated for songs you aren't actually playing.
- No Premium-only Web API calls.

**Acceptance criteria:**

- [ ] On a Free account, with the toggle on and a song playing in PixelPlayer, a second account sees that song as live in Spotify's Friend Activity within about 30 seconds.
- [ ] Pausing PixelPlayer pauses the mirror, and no Spotify audio is ever heard.
- [ ] Toggle off, or PixelPlayer stopped: the WebView and overlay are destroyed.
- [ ] If the step 1 check fails, the toggle shows Blocked with a reason, and no mirroring code runs.
- [ ] Build command passes.

## 3C. Plan

1. **Feasibility check** (debug-only screen). Load `open.spotify.com` in a WebView with a desktop user agent, the `sp_dc` cookie and Widevine allowed, then play one track. Done when: either a second account sees it in Friend Activity, or the failure is written down (redirect to the app, unsupported browser, or DRM error). **Stop here if it fails.**
2. **`SpotifyMirrorPlayer`**: overlay host, WebView settings, cookie copy, navigation blocking, and play/pause/mute/seek script bridge. Done when: the debug screen drives it.
3. **Mirror controller**: follows the playback service's track and play state, matches tracks through `findTrackId` (E28), applies the Wi-Fi rule, and manages the lifecycle. Done when: a track change re-targets within 3 seconds.
4. **Self check** of your own presence through `SpotifySocialRepository` (E29). Done when: Live shows only after it's confirmed.
5. **Friends toggle and explainer.** File: `FriendsScreen.kt` (E30). Done when: all six states render.
6. **Verify:**
   - All acceptance criteria.
   - Battery and data over a 30-minute session.
   - What happens during ads.
   - Logging out of Spotify stops the mirror.

**Risks:**

- **Spotify's terms.** The account could be flagged or banned.
- **Blocking.** Spotify can block WebView or Android at any time.
- **Ads.** On Free, ads break the "live" state.
- **Cost.** The doubled data use and battery cost.
- **Overlay permission.** The overlay window needs the same draw-over-apps permission the island uses.

---

## 4. Assumptions

| Area | Assumption | Plan step |
|---|---|---|
| A | The Adjust tab goes first, and tool tiles open the slider on the first tap (presets need two taps). | A8, A9 |
| A | Ultra HDR, Pop, Sharpen and Denoise are 0..100, and Ultra HDR is a local-contrast and highlight "HDR look". Covers are SDR JPEGs, so there's no real gain map. | A3 |
| A | Shader-only tools are hidden on API 30-32 rather than approximated. | A9 |
| A | Strength and adjustments are baked on save, with no database change, so they aren't re-editable after save. | A4, A5 |
| A | Exact icons: Brightness6, Contrast, HdrOn, Landscape (Tone), Circle (White point), Tonality (Highlights and Shadows), RadioButtonUnchecked (Black point), InvertColors (Saturation), Thermostat, Colorize, Cloud, Vignette, AutoAwesome (Pop), Details (Sharpen), BlurOn (Denoise). Check them in material-icons-extended (E16). | A9 |
| B | The results page layout is known (E24a), but whether its text is readable by accessibility (it may be a WebView) is unconfirmed until the step 1 dump. Hum is assumed to land on the same kind of page. | B1, B3 |
| B | When the app isn't open, the result shows only through Recently heard's `announce` and the match card on the next open. A heads-up "Found X" notification would be a follow-up. | B4 |
| C | Whether Spotify's web player runs in an Android WebView on a Free account isn't verified. Everything after C1 depends on it. | C1 |
