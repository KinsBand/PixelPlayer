"""Download pinned candidate models and smoke-test CPU inference, not music accuracy.

Run with Python 3.10+ and numpy/onnxruntime installed:
    python tools/alignment/model_smoke.py --output artifacts/alignment-model-smoke.json
Models are stored in the OS temporary directory, outside the APK and repository.
No user audio is read or uploaded. Synthetic inputs cannot validate alignment quality.
"""

import argparse
import hashlib
import json
from pathlib import Path
import platform
import tempfile
import time
import urllib.request

import numpy as np
import onnxruntime as ort


MODELS = {
    "wav2vec2-english-int8": {
        "url": "https://huggingface.co/onnx-community/wav2vec2-base-960h-ONNX/resolve/729c1a6730fb549c20a1c73a3d3f96f11020225e/onnx/model_quantized.onnx",
        "sha256": "1d9a366c27b2966625cd5035ac3db8847c53bba617169912bb251b42975a3a22",
        "bytes": 95212816,
        "samples": 80000,
        "sample_rate": 16000,
    },
    "basic-pitch": {
        "url": "https://raw.githubusercontent.com/spotify/basic-pitch/fa5997af0a8210982619003269994a1be25eddf3/basic_pitch/saved_models/icassp_2022/nmp.onnx",
        "sha256": "2c3c1d144bfa61ad236e92e169c13535c880469a12a047d4e73451f2c059a0ec",
        "bytes": 230444,
        "samples": 43844,
        "sample_rate": 22050,
    },
}


def valid_file(path, spec):
    if not path.is_file() or path.stat().st_size != spec["bytes"]:
        return False
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest() == spec["sha256"]


def acquire(directory, name, spec):
    path = directory / (name + ".onnx")
    if valid_file(path, spec):
        return path
    with tempfile.NamedTemporaryFile(dir=directory, suffix=".part", delete=False) as out:
        partial = Path(out.name)
        try:
            with urllib.request.urlopen(spec["url"], timeout=60) as response:
                total = 0
                while chunk := response.read(1024 * 1024):
                    total += len(chunk)
                    if total > spec["bytes"]:
                        raise ValueError("Download exceeds pinned file size")
                    out.write(chunk)
        except BaseException:
            out.close()
            partial.unlink(missing_ok=True)
            raise
    try:
        if not valid_file(partial, spec):
            raise ValueError("Model size or SHA-256 mismatch")
        partial.replace(path)
    finally:
        partial.unlink(missing_ok=True)
    return path


def smoke(path, spec):
    options = ort.SessionOptions()
    options.intra_op_num_threads = 1
    options.inter_op_num_threads = 1
    start = time.perf_counter()
    session = ort.InferenceSession(str(path), options, providers=["CPUExecutionProvider"])
    load_seconds = time.perf_counter() - start
    feed = {}
    for item in session.get_inputs():
        if item.type != "tensor(float)":
            raise ValueError(f"Unexpected input type: {item.name}: {item.type}")
        shape = [1, spec["samples"]]
        if len(item.shape) == 3:
            shape.append(1)
        feed[item.name] = np.zeros(shape, dtype=np.float32)
    elapsed = []
    for _ in range(3):
        start = time.perf_counter()
        outputs = session.run(None, feed)
        elapsed.append(time.perf_counter() - start)
        if not all(np.isfinite(output).all() for output in outputs):
            raise ValueError("Non-finite model output")
    return {
        "status": "synthetic_inference_passed",
        "bytes": path.stat().st_size,
        "sha256": spec["sha256"],
        "load_seconds": load_seconds,
        "input_seconds": spec["samples"] / spec["sample_rate"],
        "inference_seconds": elapsed,
        "inputs": [{"name": item.name, "shape": item.shape} for item in session.get_inputs()],
        "outputs": [{"name": item.name, "shape": list(value.shape)}
                    for item, value in zip(session.get_outputs(), outputs)],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    directory = Path(tempfile.gettempdir()) / "pixelplayer-alignment-models"
    directory.mkdir(exist_ok=True)
    report = {
        "scope": "Desktop synthetic-input smoke test only; NOT song accuracy or Android performance",
        "platform": platform.platform(),
        "onnxruntime": ort.__version__,
        "threads": 1,
        "models": {},
    }
    for name, spec in MODELS.items():
        print(f"Checking {name}", flush=True)
        try:
            report["models"][name] = smoke(acquire(directory, name, spec), spec)
        except Exception as error:
            report["models"][name] = {"status": "failed", "error": str(error)}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return int(any(item["status"] == "failed" for item in report["models"].values()))


if __name__ == "__main__":
    raise SystemExit(main())
