# Voice and audio song search — implementation plan

Status: plan only, nothing implemented. Target: PixelPlayer-Clean-, Compose / Material3 1.5.0-alpha22, Media3 1.10.1, Hilt, Room 2.8.4, TFLite 2.17.0.

## 1. Scope and what "free" means here

Google's "Search a song" (Hum to Search) has **no public API**. There is no free endpoint that accepts humming and returns a song from the global catalogue. ACRCloud and AudD sell humming/fingerprint APIs; both need a key and both cost money past a trial. AcoustID/Chromaprint is free and open but identifies a *specific audio file*, not a noisy microphone capture of a room, so it cannot serve as a listen-mode engine.

This plan therefore builds the whole thing **on device, against the user's own library**. No keys, no per-query cost, no network requirement, and it works in aeroplane mode. The trade is the obvious one:

> It can only recognise songs that are already in your library (local files, plus anything already synced into `CloudSongEntity` / `GDriveSongEntity` that has been indexed).

Everything below is sized around that. A cloud fallback for the global catalogue is sketched in §11 as an explicitly optional, user-keyed extension — it is not part of the milestones.

Four modes ship. They are separate modes because the signal, the front-end DSP, the index and the scoring are different in each case; sharing one "voice search" button across them would force the app to guess, and guessing wrong costs a 10-second recording.

| Mode | Input | Engine | Index it searches |
| --- | --- | --- | --- |
| **Listen** | Music playing nearby | Landmark spectral fingerprint | Fingerprint hash index |
| **Hum / Sing** | User's voice carrying the tune | Pitch-contour extraction + DTW | Melody contour profiles |
| **Say** | Spoken title / artist / album | On-device speech-to-text | Existing `SongSearchFtsEntity` |
| **Lyrics** | Spoken (or sung) lyric line | On-device speech-to-text | New lyrics FTS over `LyricsEntity` |

Hum and Sing are shown as two buttons but share one matcher with different front-end parameters — see §5.4. They are split in the UI because the user needs to tell the app whether the input carries words (sing → lyrics cross-scoring is available) or not (hum → pitch only).

---

## 2. Current code this plugs into

Nothing here needs rewriting; all of it is extended.

| Area | Existing file | Use |
| --- | --- | --- |
| Search UI | `presentation/screens/SearchScreen.kt` | Mic entry point in the search field |
| Search state | `presentation/viewmodel/SearchStateHolder.kt` | Receives resolved queries from Say mode |
| Home entry | `presentation/components/CondensedHomeTopBar.kt` | Second mic entry point |
| Bottom deck | `presentation/components/UnifiedPlayerSheetV2.kt`, `UnifiedPlayerOverlaysLayer.kt`, `UnifiedPlayerSheetLayers.kt`, `PlayerInternalNavigationBar.kt`, `BottomGestureBar.kt` | The deck the sheet displaces |
| PCM decode | `data/analysis/PcmDecoder.kt`, `PcmAudio.kt`, `utils/AudioDecoder.kt` | Decoding library tracks for indexing |
| Analysis provenance | `data/analysis/AudioAnalysisEngine.kt`, `data/metadata/SongMetadataStore.kt` | Pattern for versioned, hash-bound, resumable analysis |
| Background work | `data/worker/SyncWorker.kt`, `EnrichmentWorker.kt`, `SyncManager.kt` | Pattern for the index builder |
| Library change detection | `data/observer/MediaStoreObserver.kt` | Incremental re-index triggers |
| Lyrics | `data/repository/LyricsRepositoryImpl.kt`, `data/database/LyricsDao.kt`, `data/lyrics/LyricsTiming.kt`, `presentation/components/LyricsSheet.kt` | Lyrics index + the synced-lyrics strip in the result sheet |
| Favourites | `data/database/FavoritesDao.kt` | The heart button in the result sheet |
| Genre | `data/model/Genre.kt`, `data/analysis/GenreCategorizerEngine.kt` | The genre chip next to the artist |
| Prefs | `data/preferences/UserPreferencesRepository.kt` | Mode defaults, index settings |
| Haptics / segmented control | `presentation/utils/AppHaptics.kt`, `presentation/components/ToggleSegmentButton.kt` | Reused directly |

New package: `com.theveloper.pixelplay.data.recognition`.

---

## 3. Permissions and capture

`RECORD_AUDIO` is **not** in `AndroidManifest.xml` today. Add:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

Runtime request on first mic tap, with a rationale sheet in the app's existing dialog style. Denial must leave the mic icon visible but inert with an explanation — not hidden.

### 3.1 `MicCaptureSession`

One capture class serves all modes. `AudioRecord`, 16 kHz mono, `ENCODING_PCM_16BIT`, buffer = 4× `getMinBufferSize`.

Audio source per mode — this matters more than it looks:

- **Listen**: `MediaRecorder.AudioSource.UNPROCESSED` first. Automatic gain control, noise suppression and echo cancellation all flatten or notch the spectral peaks the fingerprinter keys on. Fall back to `VOICE_RECOGNITION`, then `MIC`. Probe `AudioManager.getProperty(PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)`.
- **Hum / Sing**: `VOICE_RECOGNITION` — the processing helps here.
- **Say / Lyrics**: not used; `SpeechRecognizer` owns the mic.

Other capture rules:

- Ring buffer of 12 s, fed to the active matcher every 1 s so Listen can resolve early and stop.
- If PixelPlayer is itself playing through the speaker, Listen mode must pause playback first (or refuse, if the user is on headphones and the mic would hear nothing). Ask once, remember the choice. Without this the app fingerprints its own output.
- Request `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` so other apps duck.
- Hard stop at 15 s. Release `AudioRecord` in `onStop`; a leaked recorder holds the mic indicator on indefinitely.
- Never write raw capture to disk. The buffer stays in memory and is zeroed on release.

---

## 4. Listen mode — landmark fingerprinting

Standard constellation/landmark scheme (Wang 2003, the approach Dejavu and audfprint implement). Robust to room noise, speaker EQ and moderate clipping.

**The constants below are measured, not assumed.** A Python prototype was run over a synthetic 60-track corpus with per-track tempo, timbre, scale and percussion pattern, queried with 8-second excerpts degraded by a phone-mic band-pass plus pink noise. §4.6 records what the measurements changed. Every number here still needs re-checking against real library audio before Listen mode leaves the experimental flag — the synthetic corpus is good enough to size the index and rank design choices, not to certify an accept threshold.

### 4.1 Analysis front-end

Identical code path for indexing and querying, so the two sides produce comparable hashes. Implemented in `SpectralFrontEnd.kt`, built on the existing `data/analysis/dsp/Fft.kt` (used via the in-place `Fft.fft`, not `Fft.magnitudes`, which allocates three arrays per frame — a three-minute track is ~11 000 frames).

- Resample to 16 kHz mono. The existing `PcmDecoder` + `Resampler` path is fine for this; see §4.6.
- STFT: Hann window, frame 1024 samples (64 ms), hop 256 samples (16 ms) → 62.5 frames/s, 513 bins at 15.6 Hz resolution.
- Log magnitude, then per-frame spectral whitening (subtract a 33-bin moving average across frequency) so a bass-heavy room does not dominate peak picking.
- Peak picking: 2-D local maxima over a ±3-frame × ±9-bin neighbourhood, restricted to 300–4000 Hz, one candidate per frame in each of six log-spaced bands.
- **Global density cap: keep the 30 strongest peaks per second.** This is the most important constant in the system and the first draft of this plan did not have it. Without it the picker fires on every frame in every band and produces ~310 hashes/s — about 1.2 GB of index for a 1000-track library.

### 4.2 Hashing

For each anchor peak, pair with peaks in a target zone: Δt ∈ [2, 63] frames (32 ms – 1.01 s), |Δf| ≤ 127 bins, fan-out 5 → **152 hashes/s measured**.

The key is packed into **24 bits, not 32**:

```
hash = (deltaFrames shl 18) or ((deltaBin and 0x1FF) shl 9) or (anchorBin and 0x1FF)
```

Putting the 9-bit anchor bin at the *top* of the word — the obvious layout, and the one the first draft specified — sets bit 31 for any anchor above bin 255 and makes the key a negative `Int`. The index is a sorted array searched with a signed binary search, so those keys would sort below zero and never be found. Keeping the whole key inside 24 bits makes every hash positive by construction. Verified exhaustively on the JVM over all 3.76 M legal (anchorBin, Δf, Δt) triples: all non-negative, all distinct, max 16 776 960.

Anchor offsets are stored **quantised to 4 frames (64 ms)**. This was measured to *improve* accuracy — it absorbs sub-frame misalignment between reference and query — while shrinking the payload to 32 bits:

```
payload = (songIndex shl 14) or (offsetQuanta and 0x3FFF)
```

18 bits of song index (262 143 tracks), 14 bits of offset (17.4 minutes). Eight bytes per entry total.

### 4.3 Index storage — not Room

Measured size at 152 hashes/s, 8 bytes per entry, 3.5 min average track:

| Library | Entries | On disk |
| --- | --- | --- |
| 200 songs | 6.4 M | ~51 MB |
| 1 000 songs | 31.9 M | ~255 MB |
| 5 000 songs | 160 M | ~1.3 GB |

Room cannot carry that — the B-tree index on `hash` roughly doubles it and insert throughput collapses. `FingerprintIndex.kt` uses a **memory-mapped flat file** in `filesDir/recognition/`, binary-searched over a mapped `IntBuffer`: no allocation per lookup, no SQLite round trip.

The builder spills landmarks into 256 per-bucket temp files, then sorts one bucket at a time in memory — bounded memory, no k-way merge across a thousand shard handles, resumable at bucket granularity.

**The bucket must be a mixed function of the hash, not its top bits.** `hash ushr 16` takes bits dominated by the `dt` field, and `dt` is heavily skewed in real audio because peaks are dense in time. Measured on a realistically skewed distribution, top-bit bucketing left 134 of 256 buckets empty and gave the largest bucket 25× its fair share — turning "sort one bucket in memory" into a 25 MB allocation at 32 M entries. Fibonacci mixing (`hash * 0x9E3779B9 ushr 24`) spreads it to 1.04× ideal across all 256 buckets, 1 MB peak. Entries stay sorted by raw hash *within* a bucket, so lookup is unaffected.

Room keeps only the bookkeeping table. Note `Song.id` is a `String`, so the index holds a compact `songIndex` and maps it back through a manifest file:

```kotlin
@Entity(tableName = "fingerprint_index_state")
data class FingerprintIndexStateEntity(
    @PrimaryKey val songId: String,
    val contentHash: String,        // same convention as SongMetadataStore
    val fingerprinterVersion: Int,
    val hashCount: Int,
    val durationMs: Long,
    val builtAtEpochMs: Long
)
```

Settings must show the index size and let the user pick the scope — **Favourites only / Recently played / Whole library** — defaulting to favourites + recently played, with the projected size shown before they commit. At 255 MB per 1000 tracks this is not optional.

### 4.4 Matching

1. Fingerprint the 8–12 s query → ~1 200 hashes.
2. For each, binary-search the index; skip buckets larger than 400 entries (percussion and silence, which contribute noise not evidence).
3. Per song, histogram `Δ = refOffset − queryOffset`.
4. Score = the tallest bin's count.
5. **Accept when score ≥ 25 for an 8-second query**, scaled linearly with query length. The first draft said 8, which would have produced constant false positives. Measured: true matches at 10 dB SNR had a median score of 49 and a 5th percentile of 27, while queries for tracks *not in the index* peaked at 22. The distributions very nearly touch, so the threshold sits above the observed false-positive maximum rather than at the elbow, and the app says "no match" rather than naming the wrong song.
6. The winning Δ **is the playback position** — where in the track the microphone was listening — so the result sheet's synced lyrics can open on the right line without playing anything. Resolution is one 64 ms quantum. **Caveat the first draft missed:** on a track with a repeated chorus the histogram can legitimately peak on a different repeat of the same musical material. `FingerprintCandidate.positionConfident` is false when no single alignment dominates, and the sheet then shows lyrics from the top instead of claiming a position.

Run incremental matching at 4, 6, 8, 10 and 12 s and stop as soon as the threshold is met.

### 4.5 Index builder

`FingerprintIndexWorker` (WorkManager), modelled on `EnrichmentWorker`:

- One cancellable job at a time; never expedited — this is a battery-hostile job.
- Constraints: charging **or** battery > 30 %, not in power-save, thermal status below `THERMAL_STATUS_MODERATE`.
- Skip a song whose `contentHash` + `fingerprinterVersion` already match, exactly as the existing analysis reuse check does.
- Progress surfaced through the existing `SyncProgressBar.kt` pattern.
- Bump `FINGERPRINTER_VERSION` on any front-end change; that invalidates every shard, so treat it as a release-gated constant.
- `MediaStoreObserver` enqueues incremental work for added/changed songs.
- `PcmDecoder` defaults to `maxSeconds = 180` and caps at 4 M mono samples. Both need raising for full-track indexing, or long tracks are silently indexed only in part.

Throughput is decode-bound. Expect roughly real-time ÷ 20–40 per core; a 1 000-song library is tens of minutes of background work. State that in the UI before starting.

### 4.6 What the measurements changed

Recorded so these are not re-litigated or re-invented:

| Claim in the first draft | Measured | Outcome |
| --- | --- | --- |
| ~48 hashes/s | 310 hashes/s uncapped | Added the per-second density cap; 152 h/s at the tuned settings |
| Accept at score ≥ 8 | Out-of-index queries reach 22 | Threshold raised to 25 at 8 s |
| Robust to ±2 % playback speed | Top-1 falls from 100 % to 60–65 % | **Claim withdrawn** — see §10 |
| 12-byte entries, 32-bit hash at the top | Negative keys break the signed binary search | 24-bit hash, 8-byte entries |
| — | Offset quantisation to 64 ms improves accuracy *and* halves storage | Adopted |
| — | Histogram concentration (peak ÷ total) is a *worse* discriminator than raw count: true median 0.041 vs false p95 0.052, i.e. backwards | Rejected; raw count is the gate |
| — | Runner-up ratio separates poorly (true p5 1.1, false median 1.2) | Demoted to a weak tie-break |
| — | Reference anchor stride 2 halves the index (255 → 128 MB/1k) at no cost to top-1, but halves the true/false score margin | Not adopted; index *scope* is the size lever instead |
| Linear decimation of the reference may alias and corrupt fingerprints | With real 8–20 kHz content: top-1 100 % either way, median score 50 → 46 | **Concern unfounded**; reuse the existing `PcmDecoder`/`Resampler` path, no new decimator |

The first version of the aliasing test was worthless — the corpus was synthesised at 16 kHz and upsampled, so it had no content above 8 kHz to alias. It was redone at 44.1 kHz with HF cymbal content before the conclusion above was drawn.

---

## 5. Hum / Sing mode — melody contour matching

This is the hard one and the least accurate. Plan for a **ranked list of five candidates with confidence**, never a single confident answer.

### 5.1 Query front-end

- 16 kHz mono, frame 46 ms (736 samples), hop 10 ms.
- **YIN** f0 estimation (cumulative mean normalised difference, parabolic interpolation), search range 70–1000 Hz. pYIN is better but heavier; start with YIN plus the gating below.
- Voicing gate: YIN aperiodicity < 0.15 **and** frame RMS above an adaptive noise floor.
- f0 → semitones: `69 + 12 * log2(f / 440)`.
- 5-frame median filter; discard voiced runs shorter than 60 ms and unvoiced gaps shorter than 40 ms (these are glottal artefacts, not phrasing).
- **Transpose invariance**: subtract the contour median. People hum in whatever key suits them.
- **Tempo invariance**: handled by DTW, not by resampling — resampling to a fixed length throws away exactly the information DTW needs.

Require ≥ 3 s of voiced content before matching; otherwise prompt "keep going".

### 5.2 Reference melodies — the honest part

There is no free melody database for arbitrary music, so reference contours must be **estimated from the library audio itself** by predominant-melody extraction. This is genuinely approximate:

- Works acceptably on sparse arrangements with a clear lead vocal or lead instrument.
- Degrades badly on dense mixes, wall-of-sound production, heavy distortion, and anything where the loudest pitched thing is not the tune people hum.
- Instrumental hooks that a person would hum (a synth riff under a vocal) are frequently *not* the extracted predominant melody.

Approach: harmonic-summation salience over the STFT (sum log-magnitude at f, 2f, 3f, 4f with decaying weights across a semitone-spaced candidate grid), Viterbi pitch tracking over the salience surface, then the same median-filter / voicing / median-subtraction chain as the query so both sides are directly comparable. A TFLite melody model could replace this later — TFLite is already a dependency and `GenreCategorizerEngine` shows the loading pattern — but do not assume a suitably licensed model exists until one has been evaluated on real device audio.

Store a per-song contour as a quantised `ByteArray` (one signed byte per 100 ms frame = relative semitone, `0x80` for unvoiced). A 3.5-minute song is 2 100 bytes. A 5 000-song library is ~10 MB — this one fits in Room comfortably:

```kotlin
@Entity(tableName = "melody_profile")
data class MelodyProfileEntity(
    @PrimaryKey val songId: Long,
    val contentHash: String,
    val extractorVersion: Int,
    val contour: ByteArray,          // 100 ms frames, relative semitones
    val confidence: Float,           // mean salience of the tracked path
    val builtAtEpochMs: Long
)
```

Songs whose extraction confidence falls below a threshold are stored but **excluded from matching**, and the count of excluded songs is shown in Settings. Pretending to search 5 000 songs while 2 000 have unusable contours is the kind of silent failure that makes the feature feel broken.

### 5.3 Matching

Two stages, because DTW against 5 000 contours is far too slow:

1. **Prefilter.** Quantise each contour to a rounded-semitone interval string, cut into 4-grams, build an inverted index (Room table or in-memory map). Query n-grams → songs sharing ≥ 2 n-grams → keep the top 200.
2. **Rescore.** Sliding-window subsequence DTW of the query contour against each candidate, Sakoe–Chiba band of ±25 % of query length, cost = |Δsemitone| clamped at 6 with an unvoiced-skip penalty. Normalise by path length. Return the best five by normalised distance, with confidence mapped from the distance gap between #1 and #2.

Both stages are pure Kotlin over `ByteArray`; run on `Dispatchers.Default`, cancellable.

### 5.4 Why Hum and Sing are separate buttons

Same pipeline, different parameters and one extra scoring term:

| | Hum | Sing |
| --- | --- | --- |
| Voicing gate | Looser — hums are quiet and breathy | Tighter |
| Vibrato smoothing | Heavier median filter | Lighter, vibrato is real signal |
| Expected range | Narrow, often monotone-ish | Wider |
| Parallel STT | No | **Yes** — run `SpeechRecognizer` on the same capture and cross-score the transcript against the lyrics index (§7), then fuse: `0.65 × melodyScore + 0.35 × lyricScore` |

Sing mode is meaningfully more accurate than hum mode precisely because of that second channel, which is why it earns its own button rather than a toggle buried in options.

---

## 6. Say mode — spoken title / artist / album

Cheapest mode, ships first, and is genuinely free and offline.

- Prefer `SpeechRecognizer.createOnDeviceSpeechRecognizer(context)`, guarded by `SpeechRecognizer.isOnDeviceRecognitionAvailable(context)`. These are recent-API additions (on-device creation from Android 12, the availability/support-check helpers from Android 13) — **verify the exact `@RequiresApi` levels against the SDK at build time** and guard with `Build.VERSION.SDK_INT` plus a runtime availability check rather than trusting either.
- Fall back to `createSpeechRecognizer` with `RecognizerIntent.EXTRA_PREFER_OFFLINE`. If neither is available, say so and offer the keyboard — do **not** silently send audio to a network recogniser. That would contradict `data/network/NetworkAccessPolicy.kt`.
- On-device language packs are downloaded by the system Speech Recognition & Synthesis app, not by PixelPlayer. When the pack is missing, surface the system download path instead of an opaque error.
- `EXTRA_PARTIAL_RESULTS` on: partials stream into the search field live, which is most of the perceived quality of this mode.
- Query cleanup before searching: strip leading `play|find|search for|put on|look up`, split on ` by ` into title + artist, strip trailing ` please`.
- Route the cleaned query into `SearchStateHolder`, which already fans out to `SongSearchFtsEntity` and the YouTube Music path. No new search code.

---

## 7. Lyrics mode — spoken or sung lyric line

- Same STT front-end as Say mode.
- New FTS table over normalised lyric text, populated from `LyricsEntity` (strip timestamps, lowercase, fold diacritics, collapse whitespace):

```kotlin
@Fts4(contentEntity = LyricsIndexEntity::class)
@Entity(tableName = "lyrics_fts")
data class LyricsFtsEntity(val songId: Long, val plainText: String)
```

- Match: FTS `MATCH` on the transcript, then rerank by **longest common word n-gram run** between transcript and lyric text, normalised by transcript length. A 5-word run is a strong signal; three scattered common words are not.
- Return the matched line index as well as the song, so the result sheet's lyrics strip opens on the line the user actually spoke.
- Honest limit to put in the UI copy: speech recognisers are trained on speech, not singing. Spoken lyrics work well; *sung* lyrics transcribe poorly. Sung input should go to Sing mode, which uses STT only as a secondary signal.

---

## 8. UI — mic entry, sheet animation, result layout

### 8.1 Entry points

1. `SearchScreen.kt` — mic icon inside the search field, trailing side.
2. `CondensedHomeTopBar.kt` — mic icon beside the existing search affordance.

Both dispatch to the same `VoiceSearchController`.

### 8.2 The sheet is a layer, not a `ModalBottomSheet`

The requirement is that the sheet **displaces the bottom deck**, not covers it. A `ModalBottomSheet` draws a scrim over the mini player and nav bar, which is the opposite. So:

- Add `VoiceSearchSheet` as a sibling layer inside `UnifiedPlayerOverlaysLayer.kt`, hoisted at the same level as `UnifiedPlayerSheetV2` so one state object can drive both the sheet and the deck offset.
- New `VoiceSearchStateHolder` in `presentation/viewmodel/`, following the existing state-holder split, injected into `PlayerViewModel`'s composition the same way `QueueStateHolder` is.

### 8.3 The animation

One driver, everything derived from it:

```kotlin
val progress = remember { Animatable(0f) }   // 0 = closed, 1 = open
// open:  progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
// close: progress.animateTo(0f, spring(dampingRatio = 0.9f,  stiffness = Spring.StiffnessMedium))
```

Derived transforms, all in `graphicsLayer` so nothing relayouts per frame:

| Element | Transform |
| --- | --- |
| Bottom deck (`PlayerInternalNavigationBar` + mini player + `BottomGestureBar`) | `translationY = -sheetHeightPx * progress` |
| Search row / top bar row | `translationY = -liftPx * progress` where `liftPx ≈ 24.dp` — the "pull up" that opens space |
| Mic icon | `scaleX/scaleY = 1f + 0.6f * progress`, `alpha = 1f - progress` — it hands off to the sheet's listening blob |
| Sheet | `translationY = sheetHeightPx * (1f - progress)`, `alpha = progress.coerceIn(0.3f, 1f)` |
| Scrim over content above the sheet | `alpha = 0.32f * progress`, and **not** over the deck |

Measure the sheet with `onSizeChanged` into a `MutableState<Int>`; the deck reads it. No `SubcomposeLayout`, no shared-element machinery — the morph reads as a morph because the mic scales out exactly as the sheet's blob scales in on the same spring.

Details that make it feel right:

- Haptic `AppHaptics` tick at open, at recognition success, at failure.
- Predictive back: the manifest already sets `enableOnBackInvokedCallback="true"`. Wire `PredictiveBackHandler` so the drag progress drives `progress` directly.
- Drag-to-dismiss on the sheet handle, with velocity-aware settle.
- While the deck is offset, its touch targets move with it — use `offset { }` on the deck rather than `graphicsLayer` **if** hit testing at the new position matters. Compose's `graphicsLayer` translation does move hit testing, but mixing it with the player sheet's own drag gestures is the likely source of bugs here, so verify with a test that taps the nav bar while the voice sheet is open.
- Respect `Settings.Global.ANIMATOR_DURATION_SCALE` = 0 by snapping.

### 8.4 Sheet content, three states

**A. Listening**

- Mode selector row at the top: `ToggleSegmentButton` with Listen / Hum / Sing / Say / Lyrics. Last-used mode persists in `UserPreferencesRepository`.
- Centre: a reactive blob driven by capture RMS (reuse the shader/canvas approach from `WavySliderExpressive.kt` or `AmbientDownloadVisual.kt` rather than adding a new animation system).
- Status line: "Listening…" → "Got it, matching…".
- Elapsed ring or subtle progress to the 15 s cap.
- Cancel.

**B. Result** — the layout you specified

```
┌────────────────────────────────────────────────┐
│  ▁▂▃  (handle)                                 │
│                                                │
│  ┌────────┐   Song Title                  ♥    │
│  │ cover  │   Artist · Genre                   │
│  │  88dp  │                                    │
│  └────────┘                                    │
│                                                │
│  ─────────── synced lyrics ───────────         │
│      previous line (dimmed)                    │
│   >  current line (highlighted)                │
│      next line (dimmed)                        │
│                                                │
│  [ Play ]  [ Queue ]  [ Playlist ]  [ Info ]   │
└────────────────────────────────────────────────┘
```

- **Cover** left, 88 dp, app's existing rounded shape, via `SmartImage` / `OptimizedAlbumArt`.
- **Middle column** vertically centred against the cover: title on top (`MarqueeText` for overflow), artist below it with the **genre as a chip immediately to its right**, separated by `·`. Genre comes from `Song.genre`, falling back to `GenreCategorizerEngine`; when unknown, the chip is omitted rather than showing "Unknown".
- **Heart** on the right, vertically centred in the row, toggling through `FavoritesDao` — the same toggle the player and library already use, so state is consistent everywhere.
- **Synced lyrics below the row**, reusing `LyricsSheet.kt`'s line rendering and `LyricsTiming.kt`. In Listen mode, seed the scroll position from the fingerprint alignment offset (§4.4-6) so it opens on the line that was actually playing in the room. In Lyrics mode, seed from the matched line. In Hum/Sing and Say modes there is no position, so show the first chorus or the opening lines, static.
- Actions row at the bottom; `Info` opens the existing `SongInfoBottomSheet`.

**C. Candidates / no match**

- Hum and Sing always land here first: up to five rows (cover, title, artist · genre, confidence), tap to promote one to state B.
- Listen and Say land here only on failure: "No match in your library", the mode-appropriate retry hint, and a "Search online instead" action routing to the existing YouTube Music search.

### 8.5 Settings

New Settings section, registered in `presentation/settings/SettingsRegistry.kt` so it is reachable from `SettingsSearch.kt`:

- Default mode.
- Recognition index: scope (favourites / recent / all), current size, last built, Rebuild, Delete.
- Melody profiles: built count, excluded-low-confidence count.
- "Pause playback when listening" — Always / Ask / Never.
- Microphone source override (diagnostic; some OEMs mislabel `UNPROCESSED`).

---

## 9. Milestones

| # | Milestone | Contents | Ships value |
| --- | --- | --- | --- |
| **M0** | Foundation | `RECORD_AUDIO`, `MicCaptureSession`, `VoiceSearchStateHolder`, the sheet + deck-displacement animation, mode selector, both entry points, a stub recogniser returning a fixed song | No — but every later milestone is UI-free after this |
| **M1** | Say mode | On-device STT, availability probing, partials, query cleanup, route into `SearchStateHolder` | Yes, immediately |
| **M2** | Listen mode | STFT front-end, peak picking, hashing, flat index format, `FingerprintIndexWorker`, matcher, position-aligned lyrics, Settings index controls | Yes, the headline feature |
| **M3** | Lyrics mode | Lyrics FTS, n-gram reranking, matched-line seeding | Yes |
| **M4** | Hum / Sing | YIN, contour normalisation, salience melody extraction, n-gram prefilter, DTW rescorer, candidate list, sing-mode STT fusion | Partially — accuracy will be the open question |
| **M5** | Polish | App shortcut + Glance widget entry, predictive back tuning, index size/battery tuning, accessibility pass | — |

M0–M3 are well-understood engineering. M4 is a research-flavoured milestone; budget for the melody extractor to need two or three iterations and be prepared to ship it behind the existing experimental-settings flag (`ExperimentalSettingsScreen.kt`) until the numbers hold up.

---

## 10. Risks and limits, stated plainly

- **Library-only recognition.** Someone humming a song they do not own gets nothing. The empty state must say why, not just "no match".
- **Index cost.** Listen mode is unusable without a built index, and building it is minutes-to-hours of background decoding plus **255 MB per 1000 tracks**. Scope defaults exist for this reason.
- **Playback-speed sensitivity.** A ±2 % speed or pitch offset drops top-1 from 100 % to 60–65 %. This is inherent to linear-frequency landmark hashing: the shift moves peaks off their bins. It does not matter for ordinary phone or speaker playback, where speed error is effectively zero, but it does for vinyl, DJ sets and pitched-up uploads. The standard mitigation is to fingerprint the query two or three times at resampled rates and take the best score, at a proportional CPU cost; not planned for M2.
- **False accepts are the failure mode to fear.** On synthetic audio the true- and false-match score distributions nearly touch. The threshold is set conservatively and the app prefers silence, but this number must be re-measured on real audio before the feature leaves the experimental flag.
- **Repeated choruses confuse the position estimate**, not the identification. Handled by `positionConfident`.
- **Hum accuracy is not Google's.** Google matches against label-supplied melody data across tens of millions of tracks with a trained embedding model. Estimated melodies plus DTW over a personal library is a different and weaker system. Ranked candidates, not a single answer.
- **Melody extraction fails silently on dense mixes.** Hence the confidence gate and the excluded-song count in Settings.
- **Sung lyrics transcribe badly.** Documented in the UI, not hidden.
- **Mic processing varies by OEM.** `UNPROCESSED` is advertised inconsistently; the diagnostic override exists because of this.
- **Own-output feedback.** Listen mode hearing PixelPlayer's own speaker output is a real failure mode with an easy fix; do not skip it.
- **No claim of a latency target.** Listen typically resolves in 4–6 s on a clean capture; that is an observation to be measured per device, not a guarantee.
- **Privacy.** Audio never leaves the device in any of the four modes, and never touches disk. That is worth stating in the UI, since "voice search" reasonably makes people assume otherwise.

---

## 11. Optional extension, not in the milestones

If global-catalogue recognition is ever wanted, the cleanest seam is a `RecognitionProvider` interface with the on-device implementation as the default, plus a user-keyed cloud provider tried only after the local matcher declines. ACRCloud is the only mainstream option with a real humming endpoint; AudD covers listening only. Both need a key, both cost money past a trial, and both send captured audio off the device — which means the privacy statement in §10 would need per-provider qualification and an explicit opt-in. Keeping it behind an interface costs nothing now and avoids a rewrite later.

---

## 12. Verification

- Unit: hash packing round-trip; DTW against synthetic contours with known transposition and tempo scaling; n-gram prefilter recall on synthetic data; FTS reranker on handmade transcript/lyric pairs.
- Integration: fingerprint 50 library tracks, replay each through a simulated capture (add pink noise at several SNRs, ±2 % resample, 6 dB clipping) and assert top-1 accuracy and the Δ-offset error. This is the single most valuable test in the set — build it in M2, not after.
- Index builder: cancel mid-build and assert resume from completed shards; assert version bump invalidates.
- UI: tap the nav bar while the voice sheet is open (hit-test regression); predictive back at several drag progresses; animator duration scale 0.
- Manual: real-room capture across three phones, on speaker and from another device, quiet and noisy.
