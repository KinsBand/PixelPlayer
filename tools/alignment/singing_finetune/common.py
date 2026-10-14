"""Shared helpers for the singing fine-tune kit.

Everything here mirrors what the app does on the phone, so a model trained and measured with
these scripts behaves the same way once it is swapped into `AlignmentModelManager`:

* the vocabulary is `facebook/wav2vec2-base-960h`'s, token for token (`CtcVocabulary.WAV2VEC2_960H`);
* audio is 16 kHz mono, normalised to zero mean / unit variance (`ChunkedEmissions.normalize`);
* one model frame is 320 samples (20 ms).
"""
from __future__ import annotations

import json
import math
import re
from pathlib import Path

import numpy as np

SAMPLE_RATE = 16_000
HOP = 320  # samples per model frame (20 ms)
BASE_MODEL = "facebook/wav2vec2-base-960h"

BLANK = 0
WORD_SEP = 4
LETTERS = {
    "E": 5, "T": 6, "A": 7, "O": 8, "N": 9, "I": 10, "H": 11, "S": 12,
    "R": 13, "D": 14, "L": 15, "U": 16, "M": 17, "W": 18, "C": 19, "F": 20,
    "G": 21, "Y": 22, "P": 23, "B": 24, "V": 25, "K": 26, "'": 27, "X": 28,
    "J": 29, "Q": 30, "Z": 31,
}
VOCAB_SIZE = 32
ID_TO_CHAR = {v: k for k, v in LETTERS.items()}
ID_TO_CHAR[WORD_SEP] = " "

# Silence / breath / filler markers used by singing corpora (GTSinger, Opencpop-style labels…).
_NON_LEXICAL = {"SP", "AP", "<SP>", "<AP>", "SIL", "<SIL>", "BR", "<BR>", "PAU", "<PAU>", "#"}


def normalize_words(text: str) -> list[str]:
    """Display lyrics -> list of upper-case words containing only model letters.

    Same rules as the app's `AlignmentTextNormalizer`: parentheses (backing vocals) are dropped,
    curly apostrophes become straight ones, everything else that is not A-Z or ' splits words.
    Words with digits are returned as-is so callers can reject the utterance (the model would
    spell "24/7" differently from how it is sung).
    """
    text = re.sub(r"\([^)]*\)|\[[^\]]*\]", " ", text)
    text = text.replace("’", "'").replace("‘", "'").replace("`", "'")
    out = []
    for raw in text.upper().split():
        if raw.strip("<>") in {t.strip("<>") for t in _NON_LEXICAL}:
            continue
        if any(ch.isdigit() for ch in raw):
            out.append(raw)
            continue
        for w in re.split(r"[^A-Z']+", raw):
            w = w.strip("'")
            if w:
                out.append(w)
    return out


def words_to_ids(words: list[str]) -> list[int]:
    ids: list[int] = []
    for i, w in enumerate(words):
        if i:
            ids.append(WORD_SEP)
        ids.extend(LETTERS[c] for c in w)
    return ids


def has_digits(words: list[str]) -> bool:
    return any(any(ch.isdigit() for ch in w) for w in words)


def ids_to_text(ids) -> str:
    return "".join(ID_TO_CHAR.get(int(i), "") for i in ids).strip()


def greedy_decode(logits: np.ndarray) -> str:
    """CTC greedy decode of `[frames, vocab]` logits."""
    best = logits.argmax(-1)
    out, prev = [], -1
    for t in best:
        if t != prev and t != BLANK:
            out.append(int(t))
        prev = t
    return re.sub(r" +", " ", ids_to_text(out))


def app_normalize(x: np.ndarray) -> np.ndarray:
    """Zero mean, unit variance, exactly like `ChunkedEmissions.normalize` on the phone."""
    x = x.astype(np.float64)
    x = x - x.mean()
    return (x / math.sqrt(x.var() + 1e-7)).astype(np.float32)


def load_audio(path: str | Path, sr: int = SAMPLE_RATE) -> np.ndarray:
    """wav/flac/ogg/mp3 via libsndfile (anything else via ffmpeg) -> mono float32 at `sr`."""
    import soundfile as sf
    from scipy.signal import resample_poly

    try:
        data, file_sr = sf.read(str(path), dtype="float32", always_2d=True)
    except Exception:
        # m4a / aac / opus / webm etc.: decode with ffmpeg straight to mono at `sr`.
        import shutil, subprocess
        if not shutil.which("ffmpeg"):
            raise
        raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-f", "f32le", "-ac", "1", "-ar", str(sr), "-"],
                             capture_output=True, check=True).stdout
        return np.frombuffer(raw, dtype=np.float32).copy()
    data = data.mean(axis=1)
    if file_sr != sr:
        g = math.gcd(int(file_sr), sr)
        data = resample_poly(data, sr // g, int(file_sr) // g).astype(np.float32)
    return data


def edit_distance(a, b) -> int:
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def read_manifest(path: str | Path) -> list[dict]:
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def write_manifest(path: str | Path, rows: list[dict]) -> None:
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")


# --------------------------------------------------------------------------------------------
# CTC forced alignment (Viterbi over blank-interleaved states), numpy only.
# --------------------------------------------------------------------------------------------

def ctc_forced_align(log_probs: np.ndarray, targets: list[int]) -> np.ndarray:
    """Returns, for every frame, the index into `targets` it is aligned to (-1 = blank).

    `log_probs` is `[frames, vocab]` log-softmax output. Raises ValueError when the audio is too
    short for the transcript.
    """
    T = log_probs.shape[0]
    L = len(targets)
    S = 2 * L + 1
    min_frames = L + sum(1 for i in range(1, L) if targets[i] == targets[i - 1])
    if T < min_frames:
        raise ValueError(f"{T} frames cannot hold {L} tokens")
    tok = np.full(S, BLANK, dtype=np.int64)
    tok[1::2] = targets
    # skip (s-2 -> s) allowed for token states whose token differs from the previous token
    can_skip = np.zeros(S, dtype=bool)
    for s in range(3, S, 2):
        can_skip[s] = tok[s] != tok[s - 2]

    neg = -1e30
    score = np.full(S, neg, dtype=np.float64)
    score[0] = log_probs[0, tok[0]]
    if S > 1:
        score[1] = log_probs[0, tok[1]]
    back = np.zeros((T, S), dtype=np.int8)  # 0 stay, 1 from s-1, 2 from s-2
    for t in range(1, T):
        stay = score
        step = np.concatenate(([neg], score[:-1]))
        skip = np.where(can_skip, np.concatenate(([neg, neg], score[:-2])), neg)
        stacked = np.stack([stay, step, skip])
        choice = stacked.argmax(0)
        score = stacked[choice, np.arange(S)] + log_probs[t, tok]
        back[t] = choice
    s = S - 1 if S == 1 or score[S - 1] >= score[S - 2] else S - 2
    path = np.empty(T, dtype=np.int64)
    for t in range(T - 1, -1, -1):
        path[t] = s
        s -= int(back[t, s])
    return np.where(path % 2 == 1, path // 2, -1)


def word_spans(frame_token: np.ndarray, words: list[str]) -> list[tuple[int, int] | None]:
    """Per word: (first frame, last frame) of its letters in a `ctc_forced_align` result."""
    owner = []
    for wi, w in enumerate(words):
        if wi:
            owner.append(-1)  # the word separator belongs to no word
        owner.extend([wi] * len(w))
    spans: list[list[int] | None] = [None] * len(words)
    for f, ti in enumerate(frame_token):
        if ti < 0:
            continue
        wi = owner[ti]
        if wi < 0:
            continue
        if spans[wi] is None:
            spans[wi] = [f, f]
        else:
            spans[wi][1] = f
    return [tuple(s) if s else None for s in spans]
