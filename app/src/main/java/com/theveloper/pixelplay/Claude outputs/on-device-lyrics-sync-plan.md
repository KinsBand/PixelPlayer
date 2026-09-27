# PixelPlayer: word-, letter- and line-synced lyrics for every song, on device

**Date** 2026-09-21 · **Status** Plan only. Nothing is edited yet. I read the live working copy: `data/model/Lyrics.kt`, `data/lyrics/LyricsTiming.kt`, `data/lyrics/LyricsHighlightMode.kt`, `data/repository/LyricsRepositoryImpl.kt`, `utils/LyricsUtils.kt`, `utils/TtmlLyricsParser.kt`, `data/analysis/PcmDecoder.kt` and `data/ai/GeminiNanoClient.kt`.

---

## 1. Goal

Every song should show lyrics that follow the audio at every level the user picks in the lyrics menu:

| Mode (already in `LyricsHighlightMode`) | What it needs |
|---|---|
| **Sentence by sentence** (`LINE`) | a start time and an end time for each line |
| **Word by word** (`WORD`) | a start time and an end time for each word |
| **Vowels / letters** (`PHONEME`) | a timed span for each letter or sound, mapped to character offsets in the word |
| **Auto** | the finest level that exists for the song |

Everything is computed **on the phone**. No audio leaves the device.

---

## 2. What the app does today

- **Sources.** Lyrics come from embedded tags, local `.lrc` / `.ttml` / `.json` files, or LRCLIB (`LyricsRepositoryImpl.getLyrics`). The first source that returns anything wins.
- **Timing depth depends on the source.**

| Source | Line timing | Word timing | Letter timing |
|---|---|---|---|
| Enhanced LRC (`<mm:ss.xx>` tags) | yes | start only, end is guessed from the next word | no |
| TTML | yes | yes, with end times | no |
| Kugou / Paxsenix | yes | yes | no |
| Native JSON (`LyricsTiming`) | yes | yes | yes, via `SyncedPhoneme` |
| **LRCLIB, the most common source** | **yes, starts only** | **no** | **no** |
| Embedded / plain `.txt` | often **no** | no | no |
| No lyrics found | — | — | — |

- **The data model is ready and the UI already renders it.** `SyncedWord.phonemes` holds `characterStart` and `characterEnd` spans, and `LyricsTimingEvidence` already has `modelVersion`, `confidence`, `assetHash` and `lyricsHash` fields. Nothing fills them. `highlightedLyricRanges()` already reveals characters from phoneme spans.
- **The rule in the code:** *"No inferred subdivision is labelled as measured alignment"* and *"No division by spelling or word length."* This plan keeps that rule. Word and letter timings come from measuring the audio. They are never made up by dividing a line's duration evenly.

**What is missing, then, is an on-device aligner:** a component that listens to the song and works out when each line, word and letter is sung.

---

## 3. Approach: a four-tier pipeline

Each song goes through the first tier that applies. The result is cached, so the work is done once per song.

```
             ┌─ has word + letter timing from its source ──────────────▶ use as-is   (Tier 0)
lyrics ──────┼─ has line timing only (LRCLIB, LRC) ────▶ align each line in its window  (Tier 1)
             ├─ plain text only ────────────────────────▶ align the whole song          (Tier 2)
             └─ no lyrics at all ───────────────────────▶ transcribe, then align        (Tier 3)
                                                         + no vocals found ▶ "Instrumental"
```

### 3.1 Tier 1: line-timed lyrics (most songs), cheap and reliable

For each line, cut the audio from `line.time − 300 ms` to `nextLine.time + 300 ms` and force-align that line's text inside the clip. Because each clip is only 2–8 seconds, this:

- can run **just in time**: align lines *n … n+3* while line *n−1* plays, so even the first listen gets word sync after a few seconds
- stays small in memory
- also **corrects the line start and end times**. LRCLIB times are often 200–500 ms off, or taken from a different edit of the song. The median difference between the LRCLIB times and the measured times becomes an automatic sync offset. The existing manual offset still stacks on top.

### 3.2 Tier 2: plain lyrics, whole-song alignment

Decode the full song, compute emissions once, and run one Viterbi pass over all the text. This produces line times too, which turns plain lyrics into synced lyrics. It runs in the background (§5) and shows "Syncing lyrics…" in the sheet until it finishes.

Long silences and instrumental breaks are handled by a *blank / star* token between lines, so the alignment does not get pulled across a solo.

### 3.3 Tier 3: no lyrics anywhere

On-device transcription with **whisper.cpp** (the multilingual `base` or `small` model) produces text. That text is then force-aligned (Tier 2) for accurate word and letter times. The result is labelled clearly in the UI: *"Transcribed on device, may contain mistakes"*, with an edit button, because transcription of sung vocals is error-prone.

A cheap vocal-activity check runs first. Songs with no vocals get an **"Instrumental"** state instead of a bad transcript.

### 3.4 Tier 0: source already has fine timing

TTML, Kugou and native JSON sources are used as-is. Enhanced LRC words only have start times, so they go through Tier 1 to get **end times and letter spans**. Their own word starts are kept wherever the aligner agrees within 150 ms.

---

## 4. The aligner

### 4.1 How it works

A **CTC acoustic model** (wav2vec2 family) turns audio into a per-frame probability for each character, about 50 frames per second (20 ms each). **CTC forced alignment** (Viterbi over the known text) then finds when each character is sung. That gives, in a single pass:

- **letter timings**: each character's first and last frame, which becomes `SyncedPhoneme(alphabet = "grapheme", characterStart, characterEnd, …)`. This fits the existing schema and the existing `highlightedLyricRanges()` renders it with no changes.
- **word timings**: the first letter's start to the last letter's end
- **line timings**: the first word's start to the last word's end
- **confidence**: the mean posterior over each word's frames

### 4.2 Model

| Option | Languages | Size (int8, approx.) | Notes |
|---|---|---|---|
| **MMS-FA (wav2vec2 MMS-300M, forced-alignment head)**, recommended | ~1,100, through romanized text | ~300 MB | Built for forced alignment. It uses a small romanized-letter vocabulary, so one model covers every script |
| wav2vec2-base-960h | English only | ~95 MB | Smaller and faster. Could be a "Lite" download |
| wav2vec2 IPA phoneme model | many | ~300 MB | True phonemes, but mapping them back to letters is much harder. Not worth it for v1 |

- Runtime: **ONNX Runtime Mobile** (`onnxruntime-android`), using XNNPACK on the CPU and NNAPI where it helps. Export and quantize the model to int8 on a desktop and ship it as a **downloadable model**, not inside the APK (Settings → Lyrics → On-device sync → Download, Wi-Fi only by default).
- Because Tier 1 only processes short windows, a mid-range phone should keep up with playback. Measure this in Phase 1 before committing.

### 4.3 Singing makes alignment harder: vocal isolation

Aligners trained on speech degrade under a full band mix. There are two levers:

1. **Always:** band-pass the audio to 100 Hz–4 kHz and use the mid (L+R) channel, where lead vocals sit. It costs almost nothing.
2. **Optional "High accuracy" mode:** a small on-device source-separation model (an MDX-Net / Demucs-lite ONNX export) isolates vocals before alignment. It is a separate download and runs only in background jobs, never just in time.

### 4.4 Text normalisation and mapping back to the original characters

- Lowercase the text, strip punctuation and markers like `(x2)` or `[Chorus]`, and expand digits ("2" → "two", per language).
- Non-Latin scripts are romanized for the model. The app already has romanizers for Japanese, Chinese, Korean, Hindi, Punjabi and Cyrillic in `LyricsUtils`. A **uroman** port covers everything else.
- **Every normalised character keeps a pointer back to its UTF-16 range in the displayed text.** Romanized "ka" maps back to the kana か, so letter highlighting lands on the real glyphs. This includes CJK, where one character is one "letter", and RTL scripts.
- Punctuation and spaces take the timing of the character before them, so they are revealed together with it and never flash on their own.

### 4.5 Keeping to "no fake timing"

- A word whose confidence is below the threshold (start at 0.35 and tune it) **keeps its measured start and end but drops its letter spans**. It highlights as a whole word.
- A line where most words are low-confidence falls back to **line-only** timing.
- Nothing is ever divided evenly. `LyricsTimingEvidence` records `source = "on-device-align"`, `modelVersion`, the mean `confidence` and `verified = false`, so the UI and future code can tell measured source timing from aligned timing.

---

## 5. Scheduling and caching

| When | What runs |
|---|---|
| Song starts playing, no cached alignment | Tier 1 just in time for upcoming lines. The song's full alignment is queued |
| Charging + idle (`WorkManager`, `requiresCharging`, `requiresDeviceIdle`) | Full Tier 1 / 2 / 3 over the library, most-played first, then the queue and recently added songs |
| Settings → "Sync lyrics for whole library now" | Same job, run in the foreground with a progress notification |

- **Cache** the aligned `Lyrics` as native JSON (`LyricsTiming.encode`) next to the existing lyrics cache, keyed by `songId`.
- **Invalidation:** `assetHash` (hash of the first and last 64 KB plus the audio file's duration) and `lyricsHash` (`LyricsTiming.textHash`). If the user edits the lyrics, picks different lyrics in `FetchLyricsDialog`, or the file changes, the song is re-aligned. `LyricsAssetRevision.kt` should be checked to see whether it already covers this.
- **Streaming (YouTube) songs:** align from the downloaded file when there is one. Otherwise run Tier 1 on the audio already buffered through `YouTubeStreamProxy`, and do the full pass only after a download.
- `PcmDecoder` needs a new mode: full length (it currently stops at `maxSeconds = 180`), 16 kHz, with a mid-channel option.
- **Battery and heat:** one alignment job at a time, stop when the phone is thermally throttling (`PowerManager.getThermalHeadroom`), and never on low battery.

---

## 6. UI changes

| Where | Change |
|---|---|
| Lyrics sheet header | A small badge showing sync depth: *Line · Word · Letter* plus *On-device*. While a job is running: "Syncing words…" with progress |
| Lyrics menu, highlight mode | Disable modes the song cannot support yet, with a hint ("Letter sync available after on-device sync") instead of silently falling back |
| Letter mode rendering | Fill **smoothly inside each letter** using `LyricsTiming.progress()`, instead of stepping letter by letter. This removes the "stepped" look the cover-lyrics plan §9 noted |
| Word end times | Words dim once they finish. `endTime` is now always present |
| Transcribed lyrics (Tier 3) | Label plus an "Edit" button that opens the existing lyrics editor. After an edit, the song is re-aligned |
| Settings → Lyrics → On-device sync | Toggle · model download or delete, with sizes · "High accuracy (vocal isolation)" · "Transcribe songs without lyrics" · "Only while charging" · "Sync whole library now" · storage used |
| Per song, in the player's more menu | "Re-sync lyrics" and "Reset to source timing" |

Both the cover overlay and the sheet already read the same `Lyrics` object, so both pick this up without layout work.

---

## 7. Files

| File | Change |
|---|---|
| `data/lyrics/align/LyricsAligner.kt` | **new**. Interface: `align(audio, lyrics, mode) → Lyrics` |
| `data/lyrics/align/CtcForcedAligner.kt` | **new**. Runs ONNX emissions, Viterbi with star/blank tokens, and computes confidence |
| `data/lyrics/align/AlignmentTextNormalizer.kt` | **new**. Normalises and romanizes the text and keeps the offset map back to UTF-16 ranges |
| `data/lyrics/align/VocalActivityDetector.kt` | **new**. Instrumental detection |
| `data/lyrics/align/OnDeviceTranscriber.kt` | **new**. whisper.cpp through JNI (Tier 3) |
| `data/lyrics/align/AlignmentModelManager.kt` | **new**. Downloads, verifies (SHA-256) and deletes models, and reports storage |
| `data/worker/LyricsAlignmentWorker.kt` | **new**. Background job over the library |
| `data/lyrics/LyricsSyncCoordinator.kt` | **new**. Picks the tier, runs the just-in-time path, caches and invalidates |
| `data/analysis/PcmDecoder.kt` | Full-length 16 kHz mid-channel decode, plus a windowed decode for Tier 1 |
| `data/repository/LyricsRepositoryImpl.kt` | After `getLyrics`, merge the cached alignment when its hashes match |
| `data/lyrics/LyricsHighlightMode.kt` | Allow `alphabet = "grapheme"`. Report which modes a song supports |
| `data/lyrics/LyricsTiming.kt` | Validation for grapheme spans. `progress()` already handles them |
| `presentation/viewmodel/LyricsStateHolder.kt` | Expose sync state and progress. Swap in aligned lyrics as they arrive |
| `presentation/components/LyricsSheet.kt`, `player/AlbumCoverLyricsOverlay.kt` | Depth badge, smooth fill inside each letter, disabled-mode hints |
| `presentation/screens/settings/LyricsSettings.kt`, `SettingsRegistry.kt`, `strings_settings.xml` | On-device sync section |
| `app/build.gradle.kts` (outside the connected folder) | `onnxruntime-android`, the whisper.cpp JNI module |
| `test/.../CtcForcedAlignerTest.kt` | Viterbi on synthetic emissions; offset mapping for CJK, RTL and combining marks; confidence fallback |

---

## 8. Phases

| # | Content | What users get |
|---|---|---|
| 1 | **Spike:** export MMS-FA to ONNX int8 and align 10 songs on a real phone. Measure speed, memory and error | A go / no-go decision and a final choice of model size |
| 2 | Normaliser + offset map + Viterbi + tests (pure Kotlin, no Android) | — |
| 3 | Tier 1 just in time + cache + invalidation | **Word and letter sync on every LRCLIB song**, plus automatic offset correction |
| 4 | Tier 2 background job + `WorkManager` + settings | Plain-text songs become fully synced |
| 5 | UI: badge, smooth fill inside letters, mode hints, re-sync | Visible polish |
| 6 | Optional vocal isolation ("High accuracy") | Better results on dense mixes |
| 7 | Tier 3 transcription + instrumental detection | **Every** song gets synced lyrics or an honest "Instrumental" |

Phases 1–3 deliver most of the value. Phase 7 is the heaviest and the least accurate part.

---

## 9. How we'll know it works

- **Ground truth:** songs that already have TTML or native JSON word timing. Strip the timing, re-align, and compare.
- **Targets:** median word-start error **≤ 80 ms**, 90th percentile ≤ 200 ms, and line-start error ≤ 150 ms on LRCLIB songs after offset correction.
- **Test set:** English pop, fast rap, a slow ballad, a metal/rock dense mix, Japanese, Korean, Chinese, Arabic (RTL), Hindi, Icelandic (æ ð þ), a song with a long instrumental, a live version whose LRCLIB timing is from the studio edit, and a pure instrumental.
- **Performance:** Tier 1 keeps ahead of playback on a mid-range phone. Record battery use per 100 songs in the background job, and check the recomposition count stays at one per line change (the target from the cover-lyrics plan).
- `LyricsTiming.validate()` returns no errors for every aligned song.

---

## 10. Decisions for you

1. **Model size:** multilingual MMS-FA (~300 MB, every language), or English-first Lite (~95 MB) with multilingual as an extra download? *Recommended: offer both, defaulting to multilingual on Wi-Fi.*
2. **Tier 3 transcription:** include it (adds whisper.cpp, ~75–250 MB more, and can be wrong on sung vocals), or show "No lyrics found" for songs without text? *Recommended: include it, off by default, clearly labelled.*
3. **Automatic offset correction:** apply it silently, or show "Timing adjusted +320 ms" with an undo? *Recommended: apply it and show it once, with undo.*
4. **Background job:** only while charging, or any time on Wi-Fi? *Recommended: charging + idle.*

---

## 11. Risks

- **Sung vocals are harder than speech.** Expect worse results on heavy distortion, screamed vocals, stacked harmonies and heavy autotune. The confidence fallback (§4.5) keeps those songs from looking broken.
- **Model size and first-run download.**
- **Low-end phones** (< 4 GB RAM) may only manage background alignment, not just in time. Gate this with the existing `DeviceCapabilitiesViewModel`.
- **Licensing:** check the licences of the MMS weights, whisper.cpp and uroman before shipping. I have not verified them here, and some model weights carry non-commercial licences.
