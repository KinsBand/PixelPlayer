# Cover Adjust redesign and Listen capture: implementation notes (2026-10-05)

This implements areas A and B of `cover-adjust-listen-spotify-plan-2026-10-05.md`. The Spotify mirror (area C) was not built.

**Status**

- Not compiled. No Android build could run from the session.
- Not committed.

Build with:

```
cmd.exe /c "set JAVA_HOME=C:\Program Files\Android\Android Studio2\jbr&& gradlew.bat assembleDebug"
```

## A. Filters panel in Adjust

### New files (`presentation/components/`)

**`CoverAdjust.kt`**
- `CoverAdjustTool` holds the 16 tools, without Portrait Light or Skin tone.
- `CoverAdjustments` keeps only non-zero values. It provides `matrix()` for the colour-only tools and a `key`.
- `CoverLook(preset, strength, adjust)` provides:
  - `matrix`: the preset's matrix lerped by strength, then the adjust matrix.
  - `presetEffect`: the shader strengths scaled by strength.
  - `needsGpu` and `key`.
- `CoverArtLayer.look` builds the look from a layer.

**`CoverAdjustSlider.kt`**: the ruler pill.
- Left side: the tool's icon, or its value once moved.
- Middle: a tick ruler with a fixed centre line.
- Right side: reset (`SettingsBackupRestore`), disabled at the default value.
- Supports drag and fling, glides to new values, and gives a haptic tick every 25 and at the default.
- Exposes accessibility progress semantics.

**`CoverFiltersPanel.kt`**: section tabs (Adjust first, then Eras, Mood, Mono, Time, Prints), with an "Original" card leading each preset row.
- Presets:
  - First tap applies the preset at 100 and shows a "100" pill over a scrim.
  - Second tap opens the strength slider.
  - Letting go at 0 removes the preset.
- Tool tiles have the label above and show the value instead of the icon once moved.
- Also holds `coverEditLayoutSpring()`, the shared spring. `editLayoutSpring()` in the screen delegates to it.

### Changed files

**`CoverFilters.kt`**
- The matrix helpers and `LR`/`LG`/`LB` are now `internal`.

**`CoverFilterShader.kt`**
- `applyUniforms` is split out so preview shaders are reused.
- Adds `previewEffect(look)`, cached by look key and size.
- Adds `renderEffect(look)`, which chains the preset stage and then the adjust stage.
- New `CoverAdjustShader` (AGSL) applies, in order: denoise, sharpen, Ultra HDR and Pop, tone, highlights and shadows, blue tone, vignette.
- `bake(source, look)` and `Modifier.coverFilterEffect(look)`. The old preset overloads still work.

**`CoverArtLayers.kt`**
- `CoverArtLayer` gains `filterStrength` (0..100) and `adjust`.
- `hasEdits`, `reset` and the change signature include both.
- `needsComposite` is now true when strength is below 100 or any adjustment is set, so those looks are baked on save with no database change.
- New `setFocusedStrength` and `setFocusedAdjust`. `setFocusedFilter` resets strength to 100.
- The compositor bakes each layer's look.

**`CoverLayerStrip.kt`, `ImageCropView.kt`**
- Previews use `layer.look`.

**`CreatePlaylistScreen.kt`** (`PlaylistCoverAdjustContent`)
- **Viewport:** the viewport is now a custom `Layout` with three parts: strip, cover and filters panel.
  - With filters closed, the group is centred.
  - With filters open, it rides to the top, so the tabs sit right under the cover and the free space is above Apply.
- **Controls:** with filters open, Change and Filters fold away horizontally and Done shrinks into a 184dp centred Apply pill.
- The old private `CoverFiltersPanel`, `CoverFilterCard` and `toFilterThumbnail` were removed; they now live in the new panel file.

### Worth checking on device

- The icons: Tonality, Lens, WbSunny, Brightness2, WbIncandescent, HdrOn, Details, Vignette and others. They're classic Material icons but weren't verified against the classpath.
- The AGSL adjust shader only compiles at runtime. A failure is logged under the `CoverFilterRenderer` tag, and the preview would crash on the first look that uses the adjust shader.
- Strength and adjustments are baked into the saved image, so they can't be re-edited after saving.

## B. Listen and Hum result capture

This part was built by a sub-agent and reviewed by eye.

### New files

- **`data/recognition/GsaResultParser.kt`**: finds the "Song by X · year" line, then falls back to the "X - Topic" card, then to the search box text. Unit test: `app/src/test/.../GsaResultParserTest.kt` (JUnit 5).
- **`data/recognition/SongSearchResultReader.kt`**: an accessibility service scoped to the Google app. It acts only while a search is armed.
- **`data/recognition/SongSearchDelivery.kt`**: confirms a reading (seen twice, at least 500ms apart), delivers it, and returns the user to the app they were in. It never presses Back.
- **`data/recognition/shizuku/GsaDumpPoller.kt`**: the Shizuku fallback, which runs `uiautomator dump` every 1.2s.
- **`res/xml/song_search_reader_config.xml`**: the reader's accessibility config.

### Changed files

**`VoiceSearchStateHolder.kt`**
- New `ArmedSearch` state with a 45s window.
- Works out which app to return to: from `dumpsys` with Shizuku, or PixelPlayer itself if one of its screens was showing.
- New `deliverExternalResult` and `deliverExternalQuery`, plus a one-time explainer flag.
- `onHostResumed` no longer stops listening while a search is armed.

**`GoogleSearchBridge.kt`**
- `am start` output containing "Error" now counts as a failure.
- New `ensureResultReaderEnabled()` silently appends the reader to the enabled accessibility services through Shizuku.
- New `foregroundPackage()`.

**`VoiceSearchResolver.kt`**
- New `findConfidentMatch(query)`, used when only the search box text is readable. It only accepts an exact library match.

**`VoiceSearchSheet.kt`, `MainActivity.kt`**
- The one-time explainer dialog. "Turn on" opens Accessibility settings.

**`AndroidManifest.xml`, `strings.xml`**
- The service entry, a launcher query, and 6 strings.

### Worth checking on device

- Whether the text of Google's results page is visible to accessibility, or hidden inside a WebView.
- Without Shizuku, when Listen is triggered from the island over another app, the user stays on Google's page. The app they came from can't be known without Shizuku.
