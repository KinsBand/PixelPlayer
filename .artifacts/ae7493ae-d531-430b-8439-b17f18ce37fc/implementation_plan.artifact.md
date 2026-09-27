# Performance Optimization Plan for PixelPlayer

Improve runtime performance, eliminate frame drops/jank, and optimize startup time on Pixel 10 Pro (arm64-v8a).

## User Review Required

- [IMPORTANT] All benchmarks will be run on the `benchmark` build variant (`assembleBenchmark`) against the connected device (`5A150DLCH006BF`).
- [IMPORTANT] We will add comprehensive Macrobenchmark test suites covering all 8 specified user journeys (Startup, Library scroll, Full player open/close, Queue scroll, Lyrics animation, Track options & Tune mix, Search, Home scroll) comparing `CompilationMode.None` vs `CompilationMode.Partial(BaselineProfileMode.Require)`.

## Open Questions

- None. All requirements and steps (Steps 1 to 4) are clearly defined.

## Proposed Changes

### 1. Baseline and Macrobenchmarks (`:baselineprofile`)
- Update or create comprehensive Macrobenchmark test suites covering:
  a. Cold startup to home screen (`StartupTimingMetric`)
  b. Library song list fast scrolling (`FrameTimingMetric`)
  c. Full player open and collapse (`FrameTimingMetric`)
  d. Queue sheet opening and 50+ songs scrolling
  e. Synced lyrics sheet playback animation (30s)
  f. Song options (⋮) and Tune-this-mix sheet
  g. Search query typing and results scrolling
  h. Home Daily Mix / banners scrolling
- Record P50/P90/P99 `frameDurationCpuMs` and `frameOverrunMs`, and startup times into `PERF_REPORT.md`.

### 2. Bottleneck Analysis & Profiling (`Step 2`)
- Capture Perfetto system traces during benchmark runs.
- Enable `-Ppixelplay.enableComposeCompilerReports=true` to analyze unstable parameters and non-skippable composables in hot screens (`FullPlayerContent.kt`, `LyricsSheet.kt`, `QueueBottomSheet`, `LibraryScreen.kt`, `SongInfoBottomSheet`, `UnifiedPlayerSheetV2`, `AlbumCoverLyricsOverlay`).
- Inspect main-thread work (Room queries, DB calls during composition/scrolling, heavy StateFlow collection fanning out, redundant `toImmutableList()` / `map{}` allocations, uncached Bitmap/Palette operations, continuous mix loop overhead).
- Run StrictMode analysis to catch disk/network accesses on the main thread.

### 3. Targeted Optimizations (`Step 3`)
- Fix recomposition storms and unstable parameters (`@Immutable`/`@Stable`, `derivedStateOf`, lambda-based state reads for animation/position).
- Split monolithic composables (`FullPlayerContent`, `LyricsSheet`, `LibraryScreen`, etc.) to isolate recomposition subtrees.
- Move blocking DB/disk queries off the main thread and cache Palette/bitmap results.
- Regenerate baseline profile (`gradlew.bat :app:generateReleaseBaselineProfile`).

### 4. Verification & Reporting (`Step 4`)
- Re-run all macrobenchmarks and update `PERF_REPORT.md` with before/after comparison tables, trace findings, and remaining analysis.

## Verification Plan

### Automated Tests
- Run macrobenchmarks via `./gradlew :baselineprofile:connectedBenchmarkAndroidTest`.
- Verify build success across `assembleBenchmark` and `assembleRelease`.

### Manual Verification
- Review generated `PERF_REPORT.md` table and benchmark outputs.
