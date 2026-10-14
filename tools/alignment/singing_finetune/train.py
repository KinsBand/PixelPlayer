"""Fine-tune wav2vec2-base-960h (the app's word-sync model) on singing.

The vocabulary, frame rate and input normalisation are left exactly as the app expects, so the
result is a drop-in replacement for the downloaded model: no Kotlin change beyond the model URL,
SHA-256 and version.

Phones align lyrics against the full mix, not isolated vocals, so by default 70 % of training
clips are mixed with a random slice of accompaniment (instrumental stems or instrumental songs
you have the rights to) at 0-15 dB vocal-to-music ratio. Mixing in some read speech
(--speech-manifest) stops the model forgetting clear diction, which plenty of songs still have.

Example (single GPU, ~16 GB):
    python train.py --train data/manifests/gtsinger/train.jsonl data/manifests/csd/train.jsonl \
        --val data/manifests/gtsinger/val.jsonl data/manifests/csd/val.jsonl \
        --accomp-dir data/accompaniment --out runs/singing-v1 --steps 6000
"""
from __future__ import annotations

import argparse
import json
import math
import random
import time
from pathlib import Path

import numpy as np
import torch
from torch.utils.data import DataLoader, Dataset

from common import (BASE_MODEL, BLANK, SAMPLE_RATE, VOCAB_SIZE, app_normalize, edit_distance,
                    greedy_decode, load_audio, normalize_words, read_manifest, words_to_ids)


class Accompaniment:
    """Random slices of instrumental audio, loaded lazily and cached in RAM (16 kHz mono)."""

    def __init__(self, folder: str | None, max_cached: int = 64):
        self.files = []
        if folder:
            self.files = [p for p in Path(folder).rglob("*") if p.suffix.lower() in {".wav", ".flac", ".ogg", ".mp3"}]
        self.cache: dict[Path, np.ndarray] = {}
        self.max_cached = max_cached

    def sample(self, n: int, rng: random.Random) -> np.ndarray | None:
        if not self.files:
            return None
        p = rng.choice(self.files)
        if p not in self.cache:
            if len(self.cache) >= self.max_cached:
                self.cache.pop(next(iter(self.cache)))
            self.cache[p] = load_audio(p)
        a = self.cache[p]
        if len(a) < n:
            a = np.tile(a, math.ceil(n / max(1, len(a))))
        s = rng.randrange(0, len(a) - n + 1)
        return a[s: s + n]


class SungDataset(Dataset):
    def __init__(self, rows, accomp: Accompaniment | None, augment: bool, mix_prob: float, snr: tuple[float, float],
                 fixed: bool = False):
        self.rows, self.accomp, self.augment = rows, accomp, augment
        self.mix_prob, self.snr = mix_prob, snr
        # fixed=True (validation): every clip gets the same music slice, gain and noise at every
        # evaluation, so a change in val CER is the model changing, not the dice.
        self.fixed = fixed

    def __len__(self):
        return len(self.rows)

    def __getitem__(self, i):
        r = self.rows[i]
        rng = random.Random(i) if self.fixed else random.Random()
        nrng = np.random.default_rng(i) if self.fixed else np.random.default_rng()
        x = load_audio(r["audio"])
        if self.augment:
            x = x * (10 ** (rng.uniform(-6, 6) / 20))
            if self.accomp and rng.random() < self.mix_prob:
                m = self.accomp.sample(len(x), rng)
                if m is not None:
                    pv = np.mean(x ** 2) + 1e-9
                    pm = np.mean(m ** 2) + 1e-9
                    snr_db = rng.uniform(*self.snr)
                    x = x + m * math.sqrt(pv / (pm * 10 ** (snr_db / 10)))
            if rng.random() < 0.3:
                x = x + nrng.standard_normal(len(x)).astype(np.float32) * rng.uniform(0.001, 0.01) * (np.abs(x).max() + 1e-6)
        words = normalize_words(r["text"])
        return app_normalize(x), words_to_ids(words), r["text"]


def collate(batch):
    xs, ys, texts = zip(*batch)
    n = max(len(x) for x in xs)
    inp = torch.zeros(len(xs), n)
    for i, x in enumerate(xs):
        inp[i, : len(x)] = torch.from_numpy(x)
    m = max(len(y) for y in ys)
    lab = torch.full((len(ys), m), -100, dtype=torch.long)
    for i, y in enumerate(ys):
        lab[i, : len(y)] = torch.tensor(y)
    return inp, lab, list(texts)


@torch.no_grad()
def evaluate(model, loader, device, limit=None):
    model.eval()
    ce = cn = we = wn = 0
    for k, (inp, _, texts) in enumerate(loader):
        if limit and k >= limit:
            break
        logits = model(inp.to(device)).logits.float().cpu().numpy()
        for lg, ref in zip(logits, texts):
            hyp = greedy_decode(lg)
            ce += edit_distance(hyp, ref)
            cn += len(ref)
            we += edit_distance(hyp.split(), ref.split())
            wn += len(ref.split())
    model.train()
    return {"cer": ce / max(1, cn), "wer": we / max(1, wn)}


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--train", nargs="+", required=True)
    p.add_argument("--val", nargs="+", required=True)
    p.add_argument("--speech-manifest", nargs="*", default=[], help="read-speech manifests mixed in to avoid forgetting")
    p.add_argument("--speech-ratio", type=float, default=0.15)
    p.add_argument("--accomp-dir", default=None)
    p.add_argument("--mix-prob", type=float, default=0.7)
    p.add_argument("--snr-db", type=float, nargs=2, default=[0.0, 15.0])
    p.add_argument("--base", default=BASE_MODEL)
    p.add_argument("--out", required=True)
    p.add_argument("--steps", type=int, default=6000)
    # 4 x 4 = the same 16 clips per update as before, in half the GPU memory (fp32 on a 15 GB T4).
    p.add_argument("--batch-size", type=int, default=4)
    p.add_argument("--grad-accum", type=int, default=4)
    p.add_argument("--lr", type=float, default=3e-5)
    p.add_argument("--warmup", type=float, default=0.1)
    p.add_argument("--max-sec", type=float, default=12.0)
    p.add_argument("--eval-every", type=int, default=500)
    p.add_argument("--eval-batches", type=int, default=200)
    p.add_argument("--workers", type=int, default=4)
    p.add_argument("--fp16", action="store_true",
                   help="mixed precision. Off by default: on a T4 it turned every loss into NaN (run 1)")
    p.add_argument("--no-fp16", action="store_true", help=argparse.SUPPRESS)  # old flag, now the default
    p.add_argument("--seed", type=int, default=0)
    p.add_argument("--resume", action="store_true",
                   help="continue from <out>/resume.pt if it exists (free Colab/Kaggle sessions end)")
    a = p.parse_args()

    random.seed(a.seed); np.random.seed(a.seed); torch.manual_seed(a.seed)
    out = Path(a.out); out.mkdir(parents=True, exist_ok=True)
    (out / "args.json").write_text(json.dumps(vars(a), indent=2))
    device = "cuda" if torch.cuda.is_available() else "cpu"
    if device == "cpu":
        print("WARNING: no GPU found. This will run, but expect days rather than hours.")

    def rows_of(paths):
        rows = [r for pth in paths for r in read_manifest(pth)]
        return [r for r in rows if r["duration"] <= a.max_sec and normalize_words(r["text"])]

    train_rows = rows_of(a.train)
    if a.speech_manifest:
        speech = rows_of(a.speech_manifest)
        random.shuffle(speech)
        train_rows += speech[: int(len(train_rows) * a.speech_ratio / (1 - a.speech_ratio))]
    val_rows = rows_of(a.val)
    print(f"train clips {len(train_rows)} ({sum(r['duration'] for r in train_rows)/3600:.1f} h), val clips {len(val_rows)}")

    accomp = Accompaniment(a.accomp_dir)
    if a.accomp_dir and not accomp.files:
        raise SystemExit(f"no audio found in --accomp-dir {a.accomp_dir}")
    train_ds = SungDataset(train_rows, accomp, True, a.mix_prob, tuple(a.snr_db))
    # Validate on the conditions the phone sees: mixed with music when accompaniment is available.
    val_ds = SungDataset(val_rows, accomp, bool(accomp.files), 1.0, (5.0, 5.0), fixed=True)
    tl = DataLoader(train_ds, a.batch_size, shuffle=True, collate_fn=collate, num_workers=a.workers, drop_last=True,
                    persistent_workers=a.workers > 0)
    vl = DataLoader(val_ds, a.batch_size, shuffle=False, collate_fn=collate, num_workers=a.workers)

    from transformers import Wav2Vec2ForCTC, Wav2Vec2Processor

    model = Wav2Vec2ForCTC.from_pretrained(a.base, ctc_loss_reduction="mean", ctc_zero_infinity=True,
                                           pad_token_id=BLANK, mask_time_prob=0.05).to(device)
    assert model.config.vocab_size == VOCAB_SIZE, "vocabulary changed: the app would read the wrong letters"
    # wav2vec2-base-960h ships without `masked_spec_embed` (the vector SpecAugment writes over
    # masked frames). Recent transformers versions leave that "newly initialized" weight as raw,
    # uninitialised memory - often NaN. Evaluation never masks, so it looked fine, but every
    # training batch masks some frames, so every loss was NaN in runs 2 and 3. Initialise it the
    # way transformers intends (uniform 0..1) and check nothing else is broken.
    with torch.no_grad():
        emb = getattr(model.wav2vec2, "masked_spec_embed", None)
        if emb is not None and (not a.resume or not (out / "resume.pt").exists()):
            emb.uniform_()
    bad_params = [n for n, q in model.named_parameters() if not torch.isfinite(q).all()]
    if bad_params:
        raise SystemExit(f"model weights contain NaN/inf before training: {bad_params[:5]}")
    model.freeze_feature_encoder()
    model.train()

    opt = torch.optim.AdamW([q for q in model.parameters() if q.requires_grad], lr=a.lr, weight_decay=0.005)
    warm = int(a.steps * a.warmup)
    sched = torch.optim.lr_scheduler.LambdaLR(opt, lambda s: min(1.0, (s + 1) / max(1, warm)) if s < warm
                                              else max(0.0, (a.steps - s) / max(1, a.steps - warm)))
    use_amp = device == "cuda" and a.fp16 and not a.no_fp16
    scaler = torch.amp.GradScaler("cuda", enabled=use_amp)
    log = open(out / "train_log.jsonl", "a", encoding="utf-8")

    resume_file = out / "resume.pt"
    step = 0
    if a.resume and resume_file.exists():
        # Free GPU sessions end after a few hours: pick up exactly where the last one stopped.
        ck = torch.load(resume_file, map_location=device, weights_only=False)
        model.load_state_dict(ck["model"]); opt.load_state_dict(ck["opt"])
        sched.load_state_dict(ck["sched"]); scaler.load_state_dict(ck["scaler"])
        step, best, base_metrics = ck["step"], ck["best"], ck["base"]
        print(f"resumed at step {step}/{a.steps} (best val cer {best:.4f})")
    else:
        base_metrics = evaluate(model, vl, device, a.eval_batches)
        print("base model on val:", base_metrics)
        log.write(json.dumps({"step": 0, **base_metrics}) + "\n"); log.flush()
        best = base_metrics["cer"]

    def save_resume():
        tmp = out / "resume.pt.tmp"
        torch.save({"model": model.state_dict(), "opt": opt.state_dict(), "sched": sched.state_dict(),
                    "scaler": scaler.state_dict(), "step": step, "best": best, "base": base_metrics}, tmp)
        tmp.replace(resume_file)  # atomic: a session dying mid-save never corrupts the checkpoint

    # A copy of a few weights, to prove at the end that training really changed the model.
    probe_name = next(n for n, q in model.named_parameters() if q.requires_grad and q.dim() == 2)
    probe_before = dict(model.named_parameters())[probe_name].detach().float().cpu().clone()

    # Pre-flight: one training-mode forward pass. If it is not a number, find out why before
    # spending hours: retry without SpecAugment masking, which isolates the masking path.
    pf_inp, pf_lab, _ = next(iter(tl))
    with torch.no_grad():
        pf = model(pf_inp.to(device), labels=pf_lab.to(device)).loss.item()
    print(f"pre-flight training loss: {pf:.3f}", flush=True)
    if not math.isfinite(pf):
        model.config.mask_time_prob = 0.0
        with torch.no_grad():
            pf2 = model(pf_inp.to(device), labels=pf_lab.to(device)).loss.item()
        print(f"  without time masking: {pf2:.3f}", flush=True)
        if not math.isfinite(pf2):
            raise SystemExit("Training loss is NaN even without masking - the data or model is broken. "
                             "Send the log to Claude.")
        print("  -> training without time masking", flush=True)

    t0, running, good, bad, applied, reported = time.time(), 0.0, 0, 0, 0, 0
    print(f"training: fp16={'on' if use_amp else 'off'} batch {a.batch_size} x accum {a.grad_accum}", flush=True)
    it = iter(tl)
    while step < a.steps:
        batch_ok = 0
        for _ in range(a.grad_accum):
            try:
                inp, lab, texts = next(it)
            except StopIteration:
                it = iter(tl)
                inp, lab, texts = next(it)
            if not torch.isfinite(inp).all():
                bad += 1
                if reported < 5:
                    reported += 1; print(f"  skipped batch: audio contains NaN/inf ({texts[:2]})")
                continue
            try:
                with torch.autocast(device_type="cuda", dtype=torch.float16, enabled=use_amp):
                    loss = model(inp.to(device), labels=lab.to(device)).loss / a.grad_accum
            except torch.cuda.OutOfMemoryError:
                # One unusually long batch: drop it and carry on instead of killing the run.
                opt.zero_grad(set_to_none=True); torch.cuda.empty_cache()
                bad += 1; print(f"  skipped batch: out of GPU memory ({inp.shape[1]/16000:.1f} s clips)")
                continue
            if not torch.isfinite(loss):
                bad += 1  # never backpropagate a NaN/inf: it would poison the whole update
                if reported < 5:
                    reported += 1
                    print(f"  skipped batch: loss {loss.item()} | longest {inp.shape[1]/16000:.1f}s | "
                          f"label lengths {(lab >= 0).sum(-1).tolist()} | {texts[:2]}")
                continue
            try:
                scaler.scale(loss).backward()
            except torch.cuda.OutOfMemoryError:
                opt.zero_grad(set_to_none=True); torch.cuda.empty_cache()
                bad += 1; print(f"  skipped batch: out of GPU memory in backward ({inp.shape[1]/16000:.1f} s clips)")
                continue
            running += loss.item() * a.grad_accum
            good += 1
            batch_ok += 1
        if batch_ok:
            scaler.unscale_(opt)
            gnorm = torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            if torch.isfinite(gnorm):
                scaler.step(opt); applied += 1
            scaler.update()
        opt.zero_grad(set_to_none=True); sched.step()
        step += 1
        if step % 50 == 0:
            avg = running / max(1, good)
            print(f"step {step}/{a.steps} loss {avg:.3f} lr {sched.get_last_lr()[0]:.2e} "
                  f"updates {applied}/50 bad-batches {bad} {time.time()-t0:.0f}s")
            if step == 50 and applied == 0:
                raise SystemExit("No usable training step in the first 50 (every loss was NaN/inf). "
                                 "Stopping instead of wasting GPU hours: check the data or try a lower --lr.")
            running, good, bad, applied = 0.0, 0, 0, 0
        if step % a.eval_every == 0 or step == a.steps:
            m = evaluate(model, vl, device, a.eval_batches)
            print(f"step {step} val {m}")
            log.write(json.dumps({"step": step, **m}) + "\n"); log.flush()
            if m["cer"] < best:
                best = m["cer"]
                model.save_pretrained(out / "best")
                Wav2Vec2Processor.from_pretrained(a.base).save_pretrained(out / "best")
                print(f"  saved best (cer {best:.4f})")
            save_resume()
    model.save_pretrained(out / "last")
    Wav2Vec2Processor.from_pretrained(a.base).save_pretrained(out / "last")
    moved = (dict(model.named_parameters())[probe_name].detach().float().cpu() - probe_before).abs().max().item()
    print(f"weights changed by up to {moved:.2e} ({probe_name}); 0 would mean nothing was learned")
    print(f"done. base val cer {base_metrics['cer']:.4f} -> best {best:.4f}. Next: evaluate_alignment.py")


if __name__ == "__main__":
    main()
