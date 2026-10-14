# Word-sync model: singing fine-tune

The app's word-by-word lyrics sync uses `facebook/wav2vec2-base-960h` (int8 ONNX, ~95 MB, downloaded by `AlignmentModelManager`). It was trained on **read audiobooks**, but on the phone it has to follow **singing over a full mix**: long vowels, melisma, vibrato, words stretched over several notes, and drums and guitars on top. This kit fine-tunes the same model on singing.

It keeps the vocabulary, the 20 ms frame rate and the input normalisation exactly as `CtcVocabulary.WAV2VEC2_960H`, `OnnxCtcModel` and `ChunkedEmissions` expect. The result is a drop-in file: you change four constants, not the aligner.

```
prepare_data.py        sung clips + lyrics  ->  train/val manifests
train.py               fine-tune (CTC), mixing vocals with music like the phone hears them
evaluate_alignment.py  word-start error on real songs: the metric that matters
export_onnx.py         int8 ONNX + SHA-256 + the Kotlin constants to paste
```

## 1. Where to run it

**Easiest, free:** upload `kaggle_singing_finetune.ipynb` (round 2 on Colab: `colab_round2.ipynb`; regenerate both with `python make_notebooks.py` after editing the kit) to [Kaggle](https://www.kaggle.com/code) (New Notebook → File → Import Notebook). It contains the whole kit and downloads every dataset itself. Turn on GPU T4 x2 and Internet, then Save Version → Save & Run All. That runs in the background for up to 12 h, and the free quota is about 30 GPU hours a week. `train.py --resume` continues from `resume.pt` if a session ends early.


You need an NVIDIA GPU. A free Colab or Kaggle T4 works, and so does any local card with 12 GB or more. On a CPU it runs but takes days. Use `colab.ipynb` if you don't have a GPU at home.

```bash
pip install -r requirements.txt      # after installing PyTorch for your CUDA version
```

## 2. Data (English singing with lyrics)

| Use | Dataset | What you get | Licence |
|---|---|---|---|
| **train (real songs)** | [MUSDB18-HQ](https://zenodo.org/records/3338373) + [MUSDB18 lyrics extension](https://zenodo.org/records/3989267) | 141 produced songs, full mix + isolated vocals, lyrics in 3-12 s sections (`prepare_data.py musdb`) | Educational / CC BY-NC-SA 4.0 |
| **train (your songs)** | Your library + synced `.lrc` files | One clip per lyric line, checked and trimmed by the base model (`prepare_data.py lrc`) | Yours |
| train | [GTSinger](https://huggingface.co/datasets/GTSinger/GTSinger), `English/` folder | Studio solo singing, short clips with word labels | CC BY-NC-SA 4.0 |
| train | [Children's Song Dataset](https://zenodo.org/records/4785016), `english/` | 50 full songs with lyrics text | CC BY-NC-SA 4.0 |
| train (optional) | [NUS-48E](https://www.comp.nus.edu.sg/~kanmy/) | 48 sung songs | Research, by request |
| train (optional) | Your own vocal stems or a-cappellas + lyrics | Closest to your library | Yours |
| mix-in music | Instrumental stems, e.g. [MUSDB18-HQ](https://zenodo.org/records/3338373) bass, drums and other summed | Accompaniment for augmentation | Academic use |
| anti-forgetting (optional) | [LibriSpeech](https://www.openslr.org/12) train-clean-100 | Read speech | CC BY 4.0 |
| **evaluation only** | [JamendoLyrics](https://github.com/f90/jamendolyrics), English songs | Full mixes with **human word-start times** | CC (per song) |

Never train on the JamendoLyrics songs, or the evaluation stops meaning anything.

Licence note (not legal advice): the CC BY-NC-SA training sets make the fine-tuned weights non-commercial and share-alike. That matches PixelPlay's own non-commercial licence, but publish the model file under CC BY-NC-SA 4.0 and credit the datasets.

```
data/
  GTSinger/English/...            # as downloaded
  CSD/english/wav/*.wav  CSD/english/txt/*.txt
  accompaniment/*.wav             # instrumental audio only (no vocals!)
  jamendolyrics/                  # git clone https://github.com/f90/jamendolyrics
```

```bash
python prepare_data.py utterances --root data/GTSinger/English --out data/manifests/gtsinger
python prepare_data.py longform --audio-dir data/CSD/english/wav --lyrics-dir data/CSD/english/txt --out data/manifests/csd
```

`utterances` reads each clip's words from a sibling `.txt`/`.lab`, `.json` or `.TextGrid`. `longform` cuts whole songs into clips of 12 s or less at word boundaries, using the base model, and drops clips it isn't confident about. Both print hours kept and what was skipped and why.

## 3. Baseline first

```bash
python evaluate_alignment.py --jamendo data/jamendolyrics --models facebook/wav2vec2-base-960h --out baseline.json
```

This is what the app does today. Write down the median error and "within 300 ms".

## 4. Train

```bash
python train.py \
  --train data/manifests/gtsinger/train.jsonl data/manifests/csd/train.jsonl \
  --val   data/manifests/gtsinger/val.jsonl   data/manifests/csd/val.jsonl \
  --accomp-dir data/accompaniment \
  --out runs/singing-v1 --steps 6000
```

- The defaults are: feature encoder frozen, lr 3e-5 with 10% warm-up, effective batch 16, and 70% of clips mixed with music at 0–15 dB.
- It logs val CER/WER (on music-mixed clips) for the base model at step 0, then every 500 steps, to `runs/singing-v1/train_log.jsonl`. The best checkpoint goes to `best/`.
- Rough timing: a few hours on a T4 for 6000 steps. Watch the first 500 steps. If val CER isn't dropping, lower `--lr` to 1e-5.
- Add `--speech-manifest librispeech.jsonl` if alignment on clearly spoken or rapped songs gets worse.

## 5. Did it actually get better?

```bash
python export_onnx.py --checkpoint runs/singing-v1/best --out runs/singing-v1/model_int8.onnx --test-audio some_sung_clip.wav
python evaluate_alignment.py --jamendo data/jamendolyrics \
  --models facebook/wav2vec2-base-960h runs/singing-v1/best runs/singing-v1/model_int8.onnx
```

Only ship it if the **int8 .onnx** beats the base model on JamendoLyrics. Lower CER on the training-style val set is not enough. Check the median and the "within 200/300 ms" share, and look at the per-song table for songs that got worse.

## 6. Put it in the app

`export_onnx.py` prints the SHA-256 and the constants. Upload the `.onnx` somewhere it can be downloaded over HTTPS (a Hugging Face model repo or a GitHub release). Then edit `app/src/main/java/com/theveloper/pixelplay/data/lyrics/align/AlignmentModelManager.kt`:

```kotlin
const val MODEL_VERSION = "w2v2-base-960h-singing-int8-v1"
private const val MODEL_FILE = "w2v2-base-960h-singing-int8-v1.onnx"
private const val MODEL_URL = "https://…/model_int8.onnx"
private const val MODEL_SHA256 = "<printed sha256>"
```

With a new `MODEL_FILE`, phones download the new file. A new `MODEL_VERSION` changes the alignment cache key (`LyricsWordAligner.keyOf`), so songs are re-aligned with the new model. The old `wav2vec2-base-960h-int8.onnx` stays on the phone until someone deletes it, so add a one-line cleanup when you switch. By default only the transformer's linear layers are int8, which gives roughly 120–130 MB. Pass `--quantize-conv` for ~95 MB like today, and evaluate that file too.
