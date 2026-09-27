# Mobile lyric and tab alignment: model selection

Research date: 2026-09-24. Target supplied by the user: Pixel 10 Pro, English.

## Decision

Start evaluation from existing pretrained models, without training a new model:

1. English Wav2Vec2 Base 960h, quantized ONNX, as a **candidate acoustic encoder** for a known-text CTC alignment implementation.
2. Spotify Basic Pitch as a **candidate note-feature extractor**, combined with an audio-to-score alignment algorithm and the existing tab.
3. Keep English singing-specific SOFA as a research comparison for phoneme timing. Its checkpoint licence and Android export must be resolved before considering distribution.

These are evaluation candidates, not a validated production stack. No reviewed model establishes accurate alignment of every song, every displayed letter, and every instrument's tab notes on Android. Passing inference on synthetic audio is not an accuracy benchmark. No production playback code has been changed by this investigation.

## Verified candidates and limitations

| Candidate | Evidence and mobile route | Scope and limitation | Decision |
|---|---|---|---|
| Wav2Vec2 Base 960h | English, 16 kHz input, Apache-2.0 original weights; a ready quantized ONNX conversion is 95,212,816 bytes (90.8 MiB). ONNX Runtime supports Android. | Trained on read speech. Supplies CTC character scores; needs a forced-alignment decoder, text normalization and word mapping. Singing/full-mix timing quality and quantization effects are unmeasured here. | First English lyric baseline to evaluate; do not label it singing-accurate yet. |
| Spotify Basic Pitch | Official TFLite file: 204,448 bytes; ONNX: 230,444 bytes. Apache-2.0 repository. Model input is 22.05 kHz mono audio. | Estimates pitches, onsets and pitch contours. Best on a single instrument. Does not itself align a supplied tab, identify guitar strings/frets, or reliably separate instruments in a mix. Small weights do not imply negligible working memory or CPU. | First note-feature baseline; compare with simpler chroma/onset score alignment. |
| SOFA / Silasimo SOFA-combined | SOFA is designed for singing phoneme alignment and provides ONNX export. The English checkpoint uses SynthGT and English GTSinger. | The identified checkpoint is CC-BY-NC-SA-4.0; the framework's MIT licence does not replace the checkpoint terms. Export, operator compatibility, working memory and Pixel performance are not verified. Solo-singing training is not proof of accuracy on mixed recordings. | Research comparison, not a default downloadable production model. |
| Whisper via whisper.cpp | Android support and quantized models exist. Upstream lists unquantized tiny at 75 MiB disk/~273 MB memory and base at 142 MiB/~388 MB. | Primarily transcription; documented word timestamps are experimental. These figures are upstream estimates, not Pixel measurements. Neither prompting with lyrics nor token timestamps guarantees exact known-lyric alignment. | Optional later transcription fallback when lyrics are missing; unnecessary for the first known-lyrics baseline. |
| Qwen3-ForcedAligner-0.6B | Pretrained aligner with 11 languages including English. | Official model table lists speech for the aligner. Singing/BGM claims elsewhere on the card refer to the ASR models. No Android performance established here; not a letter/phoneme or tab model. | Desktop comparison candidate, not the initial low-resource phone choice. |

Sources: [original Wav2Vec2 model card](https://huggingface.co/facebook/wav2vec2-base-960h), [ONNX conversion and files](https://huggingface.co/onnx-community/wav2vec2-base-960h-ONNX/tree/main/onnx), [ONNX Runtime mobile](https://onnxruntime.ai/docs/get-started/with-mobile.html), [Basic Pitch](https://github.com/spotify/basic-pitch), [official model files](https://github.com/spotify/basic-pitch/tree/main/basic_pitch/saved_models/icassp_2022), [SOFA](https://github.com/qiuqiao/SOFA), [English SOFA checkpoint](https://huggingface.co/Silasimo/SOFA-combined), [whisper.cpp](https://github.com/ggml-org/whisper.cpp), [Qwen aligner model card](https://huggingface.co/Qwen/Qwen3-ForcedAligner-0.6B).

## How the requested outputs fit together

### Lyrics

Input must be accessible decoded audio and lyrics for the same recording/version. Use existing word-timed sources when suitable. Otherwise establish coarse song/line anchors, run known-text alignment in bounded overlapping windows, and map aligned tokens back to the original words and lines. Line start/end times can be derived from their accepted word intervals: separate line and word neural models are not inherently required.

An acoustic CTC model alone is not a complete aligner. Integration still needs normalization, repeated-token handling, constrained decoding, boundary reconciliation between windows, confidence calibration, rejection of mismatched lyrics, and preservation of original display text. Do not force missing verses, live ad-libs, or the wrong chorus into apparently valid timestamps.

Letter highlighting needs an additional distinction. Spelling characters, acoustic CTC token emissions, and phonemes are different objects. Silent letters and multi-letter sounds cannot always receive independent measured times. Use validated sound-to-character groups where available; otherwise retain word highlighting or explicitly estimated animation. Do not describe subdivision by spelling length as acoustic alignment.

### Tabs

Tabs align independently to instrumental audio, not through the lyrics. Reuse the existing tab's pitches, rhythm, string/fret choices and unfolded repeat order. Compare reference score features against observed chroma/onsets or Basic Pitch outputs, obtain a constrained time-warp mapping, then map score note events to recording time. A note timestamp inherited from a coarse bar mapping is not automatically a measured note onset.

[Matchmaker](https://github.com/pymatchmaker/matchmaker) provides a useful existing score-following reference with time-warping approaches. It is a Python research library, not a drop-in Android dependency; its piano evidence does not prove mixed-song guitar accuracy. Instrument separation may be necessary on difficult mixes and would need its own resource/accuracy evaluation.

## Existing PixelPlayer integration points

- `data/model/Lyrics.kt`: line, word, phoneme/character spans and timing evidence already exist.
- `data/lyrics/LyricsTiming.kt`: validates timing and stores model/confidence/recording evidence.
- `data/lyrics/LetterTimingEstimator.kt`: current letter subdivision is explicitly estimated. Preserve that distinction.
- `data/network/lyrics/wordsync/WordLyricsProviders.kt`: existing external timed-lyric sources can remain the inexpensive first tier.
- `data/analysis/PcmDecoder.kt`: existing decoder is bounded, but does not yet expose the full arbitrary-window streaming interface needed by this pipeline. Do not retain a whole decoded song for alignment.
- `data/songsterr/TabTimeline.kt`: already unfolds repeats and accepts bar sync points. Fine note alignment needs additional evidence/mapping beyond this bar-level timeline.
- `app/build.gradle.kts`: LiteRT is already present. ONNX Android runtime is a possible additional dependency, not yet added by this work.

## Reproducible starting point

`tools/alignment/model_smoke.py` downloads two pinned ONNX model files to the OS temporary directory, checks exact sizes and SHA-256 values, and runs three single-thread CPU inferences per model. It writes runtime/version, tensor shapes and elapsed times to a JSON report. It reads no user audio and uploads nothing.

Run with installed NumPy and ONNX Runtime:

```powershell
python tools/alignment/model_smoke.py --output artifacts/alignment-model-smoke.json
```

This verifies file integrity and desktop operator execution only. It does not implement forced alignment, postprocess notes, measure RAM, test TFLite, or benchmark Android. Models remain outside the APK/repository. The temporary model files consume about 91 MiB and can be removed when no longer needed.

Initial run passed for both models using ONNX Runtime 1.26.0 on Windows with one inference thread. Wav2Vec2 processed a synthetic 5-second window in 1.43-1.70 seconds; Basic Pitch processed a synthetic 1.99-second window in 0.065-0.079 seconds. These are three desktop smoke runs, not representative song timings or phone performance. Raw output is in `artifacts/alignment-model-smoke.json`. ADB identifies the attached physical phone as Pixel 10 Pro; no benchmark was installed or run on it.

## Acceptance before playback integration

Build a manually checked English evaluation set covering clear vocals, dense mixes, rap, sustained vowels, repeats, live versions, backing vocals, wrong lyrics and instrumentals. Keep evaluation songs separate from any later training data. Measure word/line onset and offset errors, coverage, and false confident matches. Judge letter/sound boundaries separately using actual human labels. For tabs, test note onset errors and repeat/section errors against the correct recording and tab, including alternate tunings and tempo drift.

Proposed initial product targets, not measured results: report word boundary accuracy within 100 ms and 200 ms, tab onset accuracy within 50 ms and 100 ms, and median/p95 errors plus failure rate. Agree thresholds after inspecting representative difficult songs; averages alone hide failures.

On the connected Pixel 10 Pro, compare baseline playback with analysis under the same conditions. Measure audio underruns, frame jank, Java and native peak memory/PSS, time per audio window, thermal state, battery cost and persistent bytes. Start with one worker and one inference thread, a single loaded model, short windows, bounded queues, recording/text/model-version cache keys and a fixed cache budget. CPU scheduling priority alone cannot guarantee smooth audio. Heavy work should defer during playback if device measurements fail the budget.

Only enable automatic alignment after both the song-quality and Pixel resource checks pass. Persist improvements per recording; do not begin continuous training on unverified model predictions.
