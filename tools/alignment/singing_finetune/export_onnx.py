"""Export a fine-tuned checkpoint to the int8 ONNX file the app downloads.

    python export_onnx.py --checkpoint runs/singing-v1/best --out runs/singing-v1/model_int8.onnx

Checks that the exported model has the same input/output contract as the current one
(`input_values` [1, samples] -> `logits` [1, frames, 32]), compares it with PyTorch on a real or
synthetic clip, then prints the SHA-256 and the Kotlin constants for `AlignmentModelManager`.
"""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

import numpy as np
import torch

from common import SAMPLE_RATE, VOCAB_SIZE, app_normalize, greedy_decode, load_audio


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--checkpoint", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--test-audio", default=None, help="a sung clip to compare PyTorch vs ONNX on")
    p.add_argument("--quantize-conv", action="store_true",
                   help="also quantise the convolutional feature encoder (smaller file, ~95 MB like the current "
                        "model, at some accuracy cost). Default quantises only the transformer's linear layers.")
    p.add_argument("--version", default="w2v2-base-960h-singing-int8-v1")
    a = p.parse_args()

    from transformers import Wav2Vec2ForCTC
    from onnxruntime.quantization import QuantType, quantize_dynamic
    import onnxruntime as ort

    model = Wav2Vec2ForCTC.from_pretrained(a.checkpoint).eval()
    assert model.config.vocab_size == VOCAB_SIZE
    out = Path(a.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    fp32 = out.with_name(out.stem + "_fp32.onnx")
    dummy = torch.randn(1, SAMPLE_RATE * 4)
    kwargs = dict(input_names=["input_values"], output_names=["logits"],
                  dynamic_axes={"input_values": {0: "batch_size", 1: "sequence_length"},
                                "logits": {0: "batch_size", 1: "frames"}},
                  opset_version=14, do_constant_folding=True)
    try:
        torch.onnx.export(model, (dummy,), str(fp32), dynamo=False, **kwargs)
    except TypeError:  # older torch without the dynamo switch
        torch.onnx.export(model, (dummy,), str(fp32), **kwargs)

    ops = None if a.quantize_conv else ["MatMul", "Gemm"]
    quantize_dynamic(str(fp32), str(out), weight_type=QuantType.QUInt8, op_types_to_quantize=ops)

    x = load_audio(a.test_audio) if a.test_audio else np.random.randn(SAMPLE_RATE * 6).astype(np.float32) * 0.1
    x = app_normalize(x)
    with torch.inference_mode():
        ref = model(torch.from_numpy(x)[None]).logits[0].numpy()
    sess = ort.InferenceSession(str(out), providers=["CPUExecutionProvider"])
    i, o = sess.get_inputs()[0], sess.get_outputs()[0]
    got = sess.run(None, {i.name: x[None]})[0][0]
    assert got.shape == ref.shape and got.shape[1] == VOCAB_SIZE, (got.shape, ref.shape)
    agree = float((got.argmax(-1) == ref.argmax(-1)).mean())
    print(f"input {i.name} {i.shape} -> output {o.name} {o.shape}")
    print(f"frame argmax agreement int8 vs fp32: {agree:.1%}")
    if a.test_audio:
        print("pytorch:", greedy_decode(ref))
        print("onnx   :", greedy_decode(got))
    if agree < 0.9:
        print("WARNING: quantisation changed many frames; evaluate_alignment.py the .onnx before shipping it.")

    digest = sha256(out)
    size = out.stat().st_size
    print(f"\n{out}  {size/1e6:.1f} MB  sha256 {digest}")
    print("\nUpload the file somewhere it can be downloaded over HTTPS (a Hugging Face model repo or a GitHub")
    print("release), then in AlignmentModelManager.kt set:\n")
    print(f'        const val MODEL_VERSION = "{a.version}"')
    print(f'        private const val MODEL_FILE = "{a.version}.onnx"')
    print('        private const val MODEL_URL = "https://<where you uploaded it>"')
    print(f'        private const val MODEL_SHA256 = "{digest}"')
    print(f"        const val MODEL_BYTES_APPROX = {round(size / 1048576)}L * 1024 * 1024")
    if size > 160 * 1024 * 1024:
        print("\nAlso raise MODEL_BYTES_MAX (currently 160 MB): this file is bigger.")
    fp32.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
