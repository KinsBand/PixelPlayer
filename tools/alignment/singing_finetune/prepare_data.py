"""Build train/val manifests of English *singing* with transcripts.

Two kinds of source:

  utterances  Short sung clips that already come with their words (GTSinger English, NUS-48E,
              your own clips). For each audio file the words are taken from, in order: a sibling
              .txt/.lab, a sibling .json (keys txt/text/lyrics, or a word list), or a sibling
              .TextGrid word tier.

              python prepare_data.py utterances --root data/GTSinger/English --out data/manifests/gtsinger

  longform    Whole songs + their full lyrics (Children's Song Dataset English, your own a-cappella
              or stem recordings). Each song is aligned with the *base* model and cut into
              <= --max-sec segments at word boundaries; low-confidence segments are dropped.

              python prepare_data.py longform --audio-dir data/CSD/english/wav \
                  --lyrics-dir data/CSD/english/txt --out data/manifests/csd

  musdb       Real, professionally produced songs: MUSDB18 (or MUSDB18-HQ) + the MUSDB18 lyrics
              extension (zenodo 3989267: 3-12 s sections with their words). Clips are cut from the
              full mix *and* from the isolated vocals; sections marked c/d or * are skipped.

              python prepare_data.py musdb --musdb-root data/musdb18hq --lyrics-dir data/musdb_lyrics \
                  --out data/manifests/musdb

  lrc         Your own music library: audio files with a synced .lrc next to them (same name).
              Each lyric line becomes a clip; the base model checks the line is really sung
              there and trims it, so slightly-off LRC timings are fine and wrong ones are dropped.

              python prepare_data.py lrc --music-dir data/my_music --out data/manifests/mylib

Each run writes `<out>/train.jsonl` and `<out>/val.jsonl` ({"audio", "text", "duration", "group"}).
The split is by group (song folder or song), never by clip, so a song's verses never end up on
both sides.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

import numpy as np

from common import (SAMPLE_RATE, HOP, BASE_MODEL, ctc_forced_align, has_digits, load_audio,
                    normalize_words, word_spans, words_to_ids, write_manifest)

AUDIO_EXT = {".wav", ".flac", ".ogg", ".mp3", ".m4a", ".aac", ".opus", ".webm"}  # last four need ffmpeg


def is_val(group: str, val_pct: float) -> bool:
    h = int(hashlib.sha1(group.encode()).hexdigest()[:8], 16)
    return (h % 10_000) / 100.0 < val_pct


# ----------------------------------------------------------------------------- transcripts

def _textgrid_words(path: Path) -> list[str] | None:
    text = path.read_text(encoding="utf-8", errors="ignore")
    tiers = re.split(r"\n\s*item\s*\[\d+\]\s*:", text)
    for tier in tiers[1:]:
        name = re.search(r'name\s*=\s*"([^"]*)"', tier)
        if not name or name.group(1).lower() not in {"words", "word", "lyrics", "text"}:
            continue
        return [m for m in re.findall(r'text\s*=\s*"([^"]*)"', tier) if m.strip()]
    return None


def _json_words(path: Path, wav: Path) -> list[str] | None:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return None
    if isinstance(data, list):  # metadata list: pick the entry for this clip
        data = next((d for d in data if isinstance(d, dict) and wav.stem in json.dumps(d)), None)
    if not isinstance(data, dict):
        return None
    for key in ("txt", "text", "lyrics", "lyric", "transcript"):
        if isinstance(data.get(key), str):
            return data[key].split()
    for key in ("word", "words"):
        v = data.get(key)
        if isinstance(v, list):
            out = []
            for item in v:
                if isinstance(item, str):
                    out.append(item)
                elif isinstance(item, dict):
                    w = item.get("word") or item.get("text")
                    if isinstance(w, str):
                        out.append(w)
            return out
    return None


def transcript_for(wav: Path) -> list[str] | None:
    for ext in (".txt", ".lab"):
        p = wav.with_suffix(ext)
        if p.exists():
            return p.read_text(encoding="utf-8", errors="ignore").split()
    p = wav.with_suffix(".json")
    if p.exists():
        w = _json_words(p, wav)
        if w:
            return w
    for p in (wav.with_suffix(".TextGrid"), wav.with_suffix(".textgrid")):
        if p.exists():
            w = _textgrid_words(p)
            if w:
                return w
    return None


# ----------------------------------------------------------------------------- utterances

def cmd_utterances(a):
    root = Path(a.root)
    rows, skipped = [], {"no_text": 0, "digits": 0, "length": 0}
    for wav in sorted(p for p in root.rglob("*") if p.suffix.lower() in AUDIO_EXT):
        raw = transcript_for(wav)
        if not raw:
            skipped["no_text"] += 1
            continue
        words = normalize_words(" ".join(raw))
        if not words or has_digits(words):
            skipped["digits"] += 1
            continue
        try:
            import soundfile as sf
            dur = sf.info(str(wav)).duration
        except Exception:
            dur = len(load_audio(wav)) / SAMPLE_RATE
        if not (a.min_sec <= dur <= a.max_sec):
            skipped["length"] += 1
            continue
        group = str(wav.parent.relative_to(root)) if a.group_by == "folder" else wav.stem
        rows.append({"audio": str(wav.resolve()), "text": " ".join(words), "duration": round(dur, 3), "group": group})
    finish(rows, a, skipped)


# ----------------------------------------------------------------------------- longform

def cmd_longform(a):
    import soundfile as sf
    from infer import CtcModel, song_log_probs

    model = CtcModel(a.model)
    audio_dir, lyrics_dir, out = Path(a.audio_dir), Path(a.lyrics_dir), Path(a.out)
    seg_dir = out / "segments"
    seg_dir.mkdir(parents=True, exist_ok=True)
    rows, skipped = [], {"no_lyrics": 0, "align_failed": 0, "low_conf": 0}
    for song in sorted(p for p in audio_dir.rglob("*") if p.suffix.lower() in AUDIO_EXT):
        lyr = next((lyrics_dir / f"{song.stem}{e}" for e in (".txt", ".lab", ".words.txt")
                    if (lyrics_dir / f"{song.stem}{e}").exists()), None)
        if lyr is None:
            skipped["no_lyrics"] += 1
            continue
        words = [w for w in normalize_words(lyr.read_text(encoding="utf-8", errors="ignore")) if not has_digits([w])]
        audio = load_audio(song)
        lp = song_log_probs(model, audio)
        try:
            ft = ctc_forced_align(lp, words_to_ids(words))
        except ValueError:
            skipped["align_failed"] += 1
            continue
        spans = word_spans(ft, words)
        # per-word confidence: mean log-prob of the aligned letters
        tok_lp = np.full(len(ft), np.nan)
        targets = words_to_ids(words)
        for f, ti in enumerate(ft):
            if ti >= 0:
                tok_lp[f] = lp[f, targets[ti]]
        max_frames = int(a.max_sec * SAMPLE_RATE / HOP)
        i, n = 0, 0
        while i < len(words):
            if spans[i] is None:
                i += 1
                continue
            j = i
            while j + 1 < len(words) and spans[j + 1] is not None and spans[j + 1][1] - spans[i][0] <= max_frames:
                j += 1
            f0 = max(0, spans[i][0] - int(0.25 * SAMPLE_RATE / HOP))
            f1 = min(len(ft) - 1, spans[j][1] + int(0.30 * SAMPLE_RATE / HOP))
            conf = np.nanmean(tok_lp[spans[i][0]: spans[j][1] + 1])
            if (f1 - f0) * HOP / SAMPLE_RATE >= a.min_sec:
                if conf < a.min_conf:
                    skipped["low_conf"] += 1
                else:
                    seg = audio[f0 * HOP: (f1 + 1) * HOP]
                    path = seg_dir / f"{song.stem}_{n:03d}.wav"
                    sf.write(str(path), seg, SAMPLE_RATE)
                    rows.append({"audio": str(path.resolve()), "text": " ".join(words[i: j + 1]),
                                 "duration": round(len(seg) / SAMPLE_RATE, 3), "group": song.stem,
                                 "conf": round(float(conf), 3)})
                    n += 1
            i = j + 1
        print(f"{song.name}: {n} segments")
    finish(rows, a, skipped)


# ----------------------------------------------------------------------------- timed sections

def cut_timed(model, audio, sections, group, prefix, seg_dir, a, rows, skipped):
    """Cut (start_s, end_s, text) sections out of `audio` into clips.

    Each section is widened a little (section times are often rounded or slightly off), the words
    are force-aligned with the base model inside it, and the clip is trimmed to the sung words.
    Sections the model can't place confidently are dropped, so bad timings never reach training.
    """
    import soundfile as sf
    from infer import song_log_probs
    n = 0
    for t0, t1, text in sections:
        words = [w for w in normalize_words(text) if not has_digits([w])]
        if not words:
            skipped["no_words"] = skipped.get("no_words", 0) + 1
            continue
        s0 = max(0, int((t0 - a.pad_before) * SAMPLE_RATE))
        s1 = min(len(audio), int((t1 + a.pad_after) * SAMPLE_RATE))
        clip = audio[s0:s1]
        if len(clip) < SAMPLE_RATE:
            skipped["too_short"] = skipped.get("too_short", 0) + 1
            continue
        lp = song_log_probs(model, clip)
        targets = words_to_ids(words)
        try:
            ft = ctc_forced_align(lp, targets)
        except ValueError:
            skipped["align_failed"] = skipped.get("align_failed", 0) + 1
            continue
        spans = [sp for sp in word_spans(ft, words) if sp is not None]
        if not spans:
            skipped["align_failed"] = skipped.get("align_failed", 0) + 1
            continue
        f0 = max(0, spans[0][0] - int(0.25 * SAMPLE_RATE / HOP))
        f1 = min(len(ft) - 1, spans[-1][1] + int(0.30 * SAMPLE_RATE / HOP))
        conf = float(np.mean([lp[f, targets[ti]] for f, ti in enumerate(ft) if ti >= 0]))
        if conf >= a.min_conf:
            seg = clip[f0 * HOP: (f1 + 1) * HOP]  # confident: trim to the sung words
        elif getattr(a, "keep_conf", None) is not None and conf >= a.keep_conf:
            # Human-timed section the base model finds hard. These are exactly the clips that teach
            # it the most, so keep them, untrimmed, with only a little padding around the section.
            w0 = max(0, int((t0 - 0.25) * SAMPLE_RATE)); w1 = min(len(audio), int((t1 + 0.5) * SAMPLE_RATE))
            seg = audio[w0:w1]
            skipped["kept_hard"] = skipped.get("kept_hard", 0) + 1
        else:
            skipped["low_conf"] = skipped.get("low_conf", 0) + 1
            continue
        dur = len(seg) / SAMPLE_RATE
        if not (a.min_sec <= dur <= a.max_sec):
            skipped["length"] = skipped.get("length", 0) + 1
            continue
        path = seg_dir / f"{prefix}_{n:03d}.wav"
        sf.write(str(path), seg, SAMPLE_RATE)
        rows.append({"audio": str(path.resolve()), "text": " ".join(words), "duration": round(len(seg) / SAMPLE_RATE, 3),
                     "group": group, "conf": round(conf, 3)})
        n += 1
    return n


def _key(name: str) -> str:
    return re.sub(r"[^a-z0-9]", "", name.lower())


# ----------------------------------------------------------------------------- musdb

_MUSDB_LINE = re.compile(r"^\s*(\d+):(\d+(?:\.\d+)?)\s+(\d+):(\d+(?:\.\d+)?)\s+(\S+)\s+(.*)$")


def _musdb_tracks(root: Path):
    """Yields (name, {"mixture": array, "vocals": array}) for MUSDB18-HQ wav folders or MUSDB18 stems."""
    hq = sorted(root.rglob("mixture.wav"))
    if hq:
        for mix in hq:
            yield mix.parent.name, {"mixture": mix, "vocals": mix.parent / "vocals.wav"}
        return
    import musdb  # MUSDB18 .stem.mp4 files (needs ffmpeg)
    for t in musdb.DB(root=str(root)):
        yield t.name, {"mixture": t, "vocals": t}


def _load_source(src, which):
    if isinstance(src, Path):
        return load_audio(src)
    from scipy.signal import resample_poly
    import math
    audio = (src.audio if which == "mixture" else src.targets["vocals"].audio).mean(axis=1).astype(np.float32)
    g = math.gcd(int(src.rate), SAMPLE_RATE)
    return resample_poly(audio, SAMPLE_RATE // g, int(src.rate) // g).astype(np.float32)


def cmd_musdb(a):
    from infer import CtcModel
    model = CtcModel(a.model)
    lyrics = {_key(p.stem): p for p in Path(a.lyrics_dir).rglob("*.txt")}
    out = Path(a.out); seg_dir = out / "segments"; seg_dir.mkdir(parents=True, exist_ok=True)
    rows, skipped = [], {"no_lyrics": 0, "label_skipped": 0}
    for name, sources in _musdb_tracks(Path(a.musdb_root)):
        lyr = lyrics.get(_key(name))
        if lyr is None:
            skipped["no_lyrics"] += 1
            continue
        sections = []
        for line in lyr.read_text(encoding="utf-8", errors="ignore").splitlines():
            m = _MUSDB_LINE.match(line)
            if not m:
                continue
            label = m.group(5)
            # a = one singer, b = several singing the same words; c/d (different words / no
            # singing) and * (overlapping lines in unclear order) are not usable.
            if "*" in line or label.strip("*").lower() not in {"a", "b"}:
                skipped["label_skipped"] += 1
                continue
            t0 = int(m.group(1)) * 60 + float(m.group(2))
            t1 = int(m.group(3)) * 60 + float(m.group(4))
            sections.append((t0, t1, m.group(6)))
        n = 0
        for which in a.sources:
            audio = _load_source(sources[which], which)
            n += cut_timed(model, audio, sections, name, f"{_key(name)[:40]}_{which}", seg_dir, a, rows, skipped)
        print(f"{name}: {n} clips")
    finish(rows, a, skipped)


# ----------------------------------------------------------------------------- lrc (your library)

_LRC_TIME = re.compile(r"\[(\d+):(\d+(?:[.:]\d+)?)\]")


def parse_lrc(text: str) -> list[tuple[float, str]]:
    """[(start_s, line)] from a synced .lrc; metadata tags and <word> timings are ignored."""
    out = []
    for raw in text.splitlines():
        stamps = _LRC_TIME.findall(raw)
        if not stamps:
            continue
        words = re.sub(r"<\d+:\d+(?:[.:]\d+)?>", " ", _LRC_TIME.sub("", raw)).strip()
        for mm, ss in stamps:
            out.append((int(mm) * 60 + float(ss.replace(":", ".")), words))
    return sorted(out)


def lrc_sections(lines: list[tuple[float, str]], song_len_s: float, max_sec: float):
    secs = []
    for i, (t, txt) in enumerate(lines):
        nxt = lines[i + 1][0] if i + 1 < len(lines) else song_len_s
        if txt and nxt > t:
            secs.append((t, min(nxt, t + max_sec), txt))
    return secs


def cmd_lrc(a):
    from infer import CtcModel
    model = CtcModel(a.model)
    out = Path(a.out); seg_dir = out / "segments"; seg_dir.mkdir(parents=True, exist_ok=True)
    rows, skipped = [], {"no_lrc": 0, "not_synced": 0}
    for song in sorted(p for p in Path(a.music_dir).rglob("*") if p.suffix.lower() in AUDIO_EXT):
        lrc = song.with_suffix(".lrc")
        if not lrc.exists():
            skipped["no_lrc"] += 1
            continue
        lines = parse_lrc(lrc.read_text(encoding="utf-8", errors="ignore"))
        if len(lines) < 3:
            skipped["not_synced"] += 1
            continue
        try:
            audio = load_audio(song)
        except Exception as e:
            print(f"skip {song.name}: can't decode ({e})")
            continue
        sections = lrc_sections(lines, len(audio) / SAMPLE_RATE, a.max_sec)
        group = hashlib.sha1(str(song.resolve()).encode()).hexdigest()[:12]
        n = cut_timed(model, audio, sections, group, f"{_key(song.stem)[:40]}_{group[:6]}", seg_dir, a, rows, skipped)
        print(f"{song.name}: {n} clips")
    finish(rows, a, skipped)


def finish(rows, a, skipped):
    out = Path(a.out)
    train = [r for r in rows if not is_val(r["group"], a.val_pct)]
    val = [r for r in rows if is_val(r["group"], a.val_pct)]
    write_manifest(out / "train.jsonl", train)
    write_manifest(out / "val.jsonl", val)
    hours = sum(r["duration"] for r in rows) / 3600
    print(json.dumps({"train": len(train), "val": len(val), "hours": round(hours, 2), "skipped": skipped}, indent=2))


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    for name in ("utterances", "longform", "musdb", "lrc"):
        s = sub.add_parser(name)
        s.add_argument("--out", required=True)
        s.add_argument("--val-pct", type=float, default=8.0)
        s.add_argument("--min-sec", type=float, default=1.0)
        s.add_argument("--max-sec", type=float, default=12.0)
        if name == "utterances":
            s.add_argument("--root", required=True)
            s.add_argument("--group-by", choices=["folder", "file"], default="folder")
        else:
            if name == "longform":
                s.add_argument("--audio-dir", required=True)
                s.add_argument("--lyrics-dir", required=True)
            elif name == "musdb":
                s.add_argument("--musdb-root", required=True, help="MUSDB18-HQ folder (wav) or MUSDB18 (.stem.mp4)")
                s.add_argument("--lyrics-dir", required=True, help="unzipped train_lyrics.zip + test_lyrics.zip")
                s.add_argument("--sources", nargs="+", choices=["mixture", "vocals"], default=["mixture", "vocals"])
                s.add_argument("--keep-conf", type=float, default=-4.0,
                               help="human-timed sections between this and --min-conf are kept untrimmed "
                                    "(hard for the base model = most useful to learn from)")
            else:
                s.add_argument("--music-dir", required=True, help="audio files with a same-named synced .lrc")
            if name != "longform":
                s.add_argument("--pad-before", type=float, default=0.5)
                s.add_argument("--pad-after", type=float, default=1.0)
            s.add_argument("--model", default=BASE_MODEL, help="aligner used to cut segments")
            s.add_argument("--min-conf", type=float, default=-1.5, help="mean letter log-prob to keep a segment")
    a = p.parse_args()
    {"utterances": cmd_utterances, "longform": cmd_longform, "musdb": cmd_musdb, "lrc": cmd_lrc}[a.cmd](a)


if __name__ == "__main__":
    main()
