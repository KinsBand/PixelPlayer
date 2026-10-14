"""Whole-song CTC emissions, computed in overlapping chunks the way the phone does.

`AlignmentAudio.kt` feeds the model fixed chunks (a core plus context on each side) and keeps
only the core frames. Doing the same here means alignment errors measured on the desktop are
the errors users will see, not those of an unrealistically long single pass.
"""
from __future__ import annotations

import numpy as np
import torch

from common import HOP, SAMPLE_RATE, app_normalize


class CtcModel:
    """A PyTorch checkpoint (HF folder / hub id) or an exported `.onnx` file."""

    def __init__(self, path: str, device: str | None = None, threads: int = 4):
        self.path = path
        if path.endswith(".onnx"):
            import onnxruntime as ort

            opts = ort.SessionOptions()
            opts.intra_op_num_threads = threads
            self.sess = ort.InferenceSession(path, opts, providers=["CPUExecutionProvider"])
            self.input_name = self.sess.get_inputs()[0].name
            self.torch_model = None
        else:
            from transformers import Wav2Vec2ForCTC

            self.device = device or ("cuda" if torch.cuda.is_available() else "cpu")
            self.torch_model = Wav2Vec2ForCTC.from_pretrained(path).to(self.device).eval()
            self.sess = None

    def logits(self, x: np.ndarray) -> np.ndarray:
        """`[samples]` already-normalised float32 -> `[frames, vocab]` logits."""
        if self.sess is not None:
            return self.sess.run(None, {self.input_name: x[None, :]})[0][0]
        with torch.inference_mode():
            t = torch.from_numpy(x)[None].to(self.device)
            return self.torch_model(t).logits[0].float().cpu().numpy()


def song_log_probs(model: CtcModel, audio: np.ndarray, core_s: float = 20.0, context_s: float = 2.0) -> np.ndarray:
    """`[frames, vocab]` log-probabilities for a whole song, one frame per 20 ms."""
    core = int(core_s * SAMPLE_RATE) // HOP * HOP
    ctx = int(context_s * SAMPLE_RATE) // HOP * HOP
    total_frames = len(audio) // HOP
    out = []
    start = 0
    while start < len(audio):
        a = max(0, start - ctx)
        b = min(len(audio), start + core + ctx)
        chunk = audio[a:b]
        if len(chunk) < HOP * 2:
            break
        lg = model.logits(app_normalize(chunk))
        skip = (start - a) // HOP
        keep = min(core // HOP, total_frames - start // HOP)
        out.append(lg[skip: skip + keep])
        start += core
    lg = np.concatenate(out, 0)[:total_frames]
    lg = lg - lg.max(-1, keepdims=True)
    return lg - np.log(np.exp(lg).sum(-1, keepdims=True))
