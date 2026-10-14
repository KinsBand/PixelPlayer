"""The number that matters: how far are word starts from where the singer actually sings them?

Runs known-lyrics CTC alignment over whole songs (full mixes, chunked like the phone) and
compares every word's predicted start with a human annotation. Use JamendoLyrics
(https://github.com/f90/jamendolyrics), English songs only, and keep these songs out of training.

    python evaluate_alignment.py --jamendo data/jamendolyrics \
        --models facebook/wav2vec2-base-960h runs/singing-v1/best runs/singing-v1/model_int8.onnx

Expected layout (the repo's own): mp3/<song>.mp3, lyrics/<song>.words.txt (or .txt) and
annotations/words/<song>.csv whose first column is each word's start in seconds. A
JamendoLyrics.csv with a Language column, if present, is used to keep English songs only.
Any other dataset in that layout works too.

Writes a JSON report (median/mean error, share within 100/200/300 ms, per song) next to it.
"""
from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path

import numpy as np

from common import HOP, SAMPLE_RATE, ctc_forced_align, load_audio, normalize_words, word_spans, words_to_ids
from infer import CtcModel, song_log_probs


def load_songs(root: Path, english_only: bool):
    keep = None
    meta = root / "JamendoLyrics.csv"
    if english_only and meta.exists():
        with open(meta, encoding="utf-8") as f:
            rows = list(csv.DictReader(f))
        lang_key = next((k for k in rows[0] if k.lower() == "language"), None) if rows else None
        name_key = next((k for k in rows[0] if k.lower() in {"filepath", "name", "file"}), None) if rows else None
        if lang_key and name_key:
            keep = {Path(r[name_key]).stem for r in rows if r[lang_key].lower().startswith("en")}
    audio_dir = root / "mp3" if (root / "mp3").exists() else root / "audio"
    for audio in sorted(audio_dir.glob("*")):
        if keep is not None and audio.stem not in keep:
            continue
        words_file = next((p for p in (root / "lyrics" / f"{audio.stem}.words.txt", root / "lyrics" / f"{audio.stem}.txt")
                           if p.exists()), None)
        ann = root / "annotations" / "words" / f"{audio.stem}.csv"
        if words_file is None or not ann.exists():
            continue
        raw_words = words_file.read_text(encoding="utf-8").split()
        starts = []
        with open(ann, encoding="utf-8") as f:
            for row in csv.reader(f):
                try:
                    starts.append(float(row[0]))
                except (ValueError, IndexError):
                    continue  # header
        if len(starts) != len(raw_words):
            print(f"skip {audio.stem}: {len(raw_words)} words vs {len(starts)} annotations")
            continue
        yield audio, raw_words, starts


def evaluate_model(model_path: str, songs, threads: int):
    model = CtcModel(model_path, threads=threads)
    errors, per_song = [], {}
    for audio_path, raw_words, starts in songs:
        audio = load_audio(audio_path)
        lp = song_log_probs(model, audio)
        # keep the display-word <-> annotation pairing while normalising
        pairs = []
        for w, s in zip(raw_words, starts):
            norm = normalize_words(w)
            # One annotation per display word: skip words that normalise to nothing, to several
            # words ("rock-n-roll") or contain digits, as the app interpolates those anyway.
            if len(norm) == 1 and not any(c.isdigit() for c in norm[0]):
                pairs.append((norm[0], s))
        words = [w for w, _ in pairs]
        try:
            spans = word_spans(ctc_forced_align(lp, words_to_ids(words)), words)
        except ValueError:
            print(f"{audio_path.stem}: alignment failed")
            continue
        e = [abs(sp[0] * HOP / SAMPLE_RATE - s) for sp, (_, s) in zip(spans, pairs) if sp is not None]
        errors += e
        per_song[audio_path.stem] = {"words": len(e), "median_s": float(np.median(e)) if e else None,
                                     "within_300ms": float(np.mean(np.array(e) <= 0.3)) if e else None}
        print(f"  {audio_path.stem}: median {per_song[audio_path.stem]['median_s']:.3f}s over {len(e)} words")
    e = np.array(errors)
    return {
        "model": model_path,
        "words": int(len(e)),
        "median_s": float(np.median(e)) if len(e) else None,
        "mean_s": float(e.mean()) if len(e) else None,
        "p95_s": float(np.percentile(e, 95)) if len(e) else None,
        "within_100ms": float((e <= 0.1).mean()) if len(e) else None,
        "within_200ms": float((e <= 0.2).mean()) if len(e) else None,
        "within_300ms": float((e <= 0.3).mean()) if len(e) else None,
        "songs": per_song,
    }


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--jamendo", required=True)
    p.add_argument("--models", nargs="+", required=True)
    p.add_argument("--all-languages", action="store_true")
    p.add_argument("--threads", type=int, default=4)
    p.add_argument("--out", default="alignment_report.json")
    a = p.parse_args()
    songs = list(load_songs(Path(a.jamendo), not a.all_languages))
    if not songs:
        raise SystemExit("no songs found; check the folder layout described in --help")
    print(f"{len(songs)} songs")
    results = []
    for m in a.models:
        print(f"== {m}")
        r = evaluate_model(m, songs, a.threads)
        results.append(r)
        print({k: v for k, v in r.items() if k != "songs"})
    Path(a.out).write_text(json.dumps(results, indent=2))
    print("\nmodel".ljust(60), "median  within200ms  within300ms")
    for r in results:
        print(r["model"][-58:].ljust(60), f"{r['median_s']:.3f}s  {r['within_200ms']:.1%}       {r['within_300ms']:.1%}")


if __name__ == "__main__":
    main()
