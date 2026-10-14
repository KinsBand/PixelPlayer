import json, base64, pathlib
kit = ["common.py","infer.py","prepare_data.py","train.py","evaluate_alignment.py","export_onnx.py","requirements.txt"]
files = {f: base64.b64encode(pathlib.Path(f).read_bytes()).decode() for f in kit}
def md(t): return {"cell_type":"markdown","metadata":{},"source":t.strip("\n")}
def code(t): return {"cell_type":"code","metadata":{},"execution_count":None,"outputs":[],"source":t.strip("\n")}

WRITE_KIT = code("import base64, os\nos.makedirs(KIT, exist_ok=True)\nFILES = " + json.dumps(files, indent=0) +
     "\nfor name, b64 in FILES.items():\n    open(os.path.join(KIT, name), 'wb').write(base64.b64decode(b64))\nos.chdir(KIT)\nprint(sorted(os.listdir(KIT)))")
PIP = code('!pip -q install "transformers>=4.40,<5" "soundfile>=0.12" onnx onnxruntime musdb huggingface_hub remotezip')

DATA_MD = md("""
## Data: real songs first (downloads itself, 30-60 min)
Every source is optional: if one fails, it says why and the rest carry on.

| Source | What | Used for |
|---|---|---|
| **MUSDB18-HQ + lyrics extension** | 141 real, professionally produced songs (rock, pop, rap, electronic...), full mix *and* isolated vocals, lyrics in 3-12 s sections | training (real songs) |
| **Your library** (optional) | your own songs + synced `.lrc` lyrics | training (closest to what you listen to) |
| GTSinger English | studio singing with word labels | training |
| Children's Song Dataset | 50 sung songs + lyrics | training |
| MUSDB18 accompaniment | the instrumental part of each song (mix minus vocals) | mixed under clean vocals |
| **JamendoLyrics** | real songs with human word times | **evaluation only, never trained on** |
""")

DATA = code(r'''
import os, glob, zipfile, subprocess, traceback, math
import numpy as np, soundfile as sf
from scipy.signal import resample_poly
STATUS = {}
def step(name, fn):
    try:
        fn(); STATUS[name] = "ok"
    except Exception as e:
        traceback.print_exc(); STATUS[name] = f"FAILED: {e}"
    print(f"== {name}: {STATUS[name]}")

# 0. Data already prepared by kaggle_data_prep.ipynb (or a run that timed out)? Use it instead of
#    downloading again: *.tar packs and/or an unpacked pp_stage folder.
import tarfile, shutil
def restore_prepared():
    found = 0
    for root in PREPARED_SOURCES:
        for tar in sorted(glob.glob(f"{root}/**/pp_data_pack/*.tar", recursive=True)):
            name = os.path.basename(tar)[:-4]
            if os.path.exists(f"{DATA}/.restored_{name}"): continue
            print("restoring", tar)
            with tarfile.open(tar) as t: t.extractall(DATA)
            open(f"{DATA}/.restored_{name}", "w").close(); found += 1
        for stage in glob.glob(f"{root}/**/pp_stage", recursive=True):
            if os.path.abspath(stage) == os.path.abspath(DATA): continue
            print("restoring", stage)
            shutil.copytree(stage, DATA, dirs_exist_ok=True); found += 1
    print("prepared data restored from", found, "source(s)" if found else "- none attached, downloading")
restore_prepared()

def to16k_mono(x, sr):
    x = x.mean(axis=1) if x.ndim == 2 else x
    g = math.gcd(int(sr), 16000)
    return resample_poly(x, 16000 // g, int(sr) // g).astype(np.float32)

# 1. MUSDB18-HQ: fetch only mixture + vocals of each song straight out of the 22.7 GB zip
#    (HTTP range requests), shrink to 16 kHz mono, and derive the accompaniment as mix - vocals.
MUS = f"{DATA}/musdb16k"; ACC = f"{DATA}/accompaniment"
def get_musdb():
    # Zenodo sometimes drops a connection mid-file. Each song is retried (re-opening the zip)
    # and a song that keeps failing is skipped, so one bad moment no longer loses all 150.
    import time
    from remotezip import RemoteZip
    URL = "https://zenodo.org/records/3338373/files/musdb18hq.zip?download=1"
    os.makedirs(ACC, exist_ok=True)
    have = len(glob.glob(f"{MUS}/*/*/vocals.wav"))
    if have >= 140:
        print(f"  {have} songs already prepared - no download needed"); return
    def open_zip():
        for attempt in range(6):
            try:
                return RemoteZip(URL)
            except Exception as e:
                print(f"  opening the zip failed ({e}); retrying in {15 * (attempt + 1)} s")
                time.sleep(15 * (attempt + 1))
        raise RuntimeError("could not open the MUSDB18-HQ zip on Zenodo")
    z = open_zip()
    names = [i.filename for i in z.infolist() if i.filename.endswith("/mixture.wav")]
    done = {os.path.basename(os.path.dirname(p)) for p in glob.glob(f"{MUS}/*/*/vocals.wav")}
    failed = []
    for k, mix_name in enumerate(names):
        track_dir = os.path.dirname(mix_name); track = os.path.basename(track_dir)
        if track in done: continue
        split = "test" if "/test/" in f"/{mix_name}" else "train"
        out = f"{MUS}/{split}/{track}"
        tmp = f"{DATA}/_tmp"
        for attempt in range(5):
            try:
                subprocess.run(["rm", "-rf", tmp]); os.makedirs(tmp, exist_ok=True)
                z.extract(mix_name, tmp); z.extract(f"{track_dir}/vocals.wav", tmp)
                mix, sr = sf.read(f"{tmp}/{mix_name}", dtype="float32"); voc, _ = sf.read(f"{tmp}/{track_dir}/vocals.wav", dtype="float32")
                m16, v16 = to16k_mono(mix, sr), to16k_mono(voc, sr)
                os.makedirs(out, exist_ok=True)
                sf.write(f"{out}/mixture.wav", m16, 16000); sf.write(f"{out}/vocals.wav", v16, 16000)
                sf.write(f"{ACC}/{track}.wav", m16 - v16, 16000)
                print(f"  [{k+1}/{len(names)}] {track}")
                break
            except Exception as e:
                wait = 10 * (attempt + 1)
                print(f"  [{k+1}/{len(names)}] {track}: download broke ({str(e)[:80]}); retry {attempt+1}/5 in {wait} s")
                time.sleep(wait)
                try: z = open_zip()
                except Exception: pass
        else:
            failed.append(track)
            subprocess.run(["rm", "-rf", out])
        subprocess.run(["rm", "-rf", tmp])
    got = len(glob.glob(f"{MUS}/*/*/vocals.wav"))
    print(f"  songs ready: {got}/{len(names)}" + (f"; skipped after retries: {failed}" if failed else ""))
    if got < 50:
        raise RuntimeError(f"only {got} MUSDB songs downloaded - Zenodo is struggling, try again later")
step("MUSDB18-HQ audio", get_musdb)

def get_musdb_lyrics():
    os.makedirs(f"{DATA}/musdb_lyrics", exist_ok=True)
    if len(glob.glob(f"{DATA}/musdb_lyrics/**/*.txt", recursive=True)) >= 100:
        print("lyrics files:", len(glob.glob(f"{DATA}/musdb_lyrics/**/*.txt", recursive=True))); return
    for part in ("train_lyrics.zip", "test_lyrics.zip"):
        z = f"{DATA}/{part}"
        if not os.path.exists(z):
            subprocess.run(["wget", "-q", "-O", z, f"https://zenodo.org/records/3989267/files/{part}?download=1"], check=True)
        zipfile.ZipFile(z).extractall(f"{DATA}/musdb_lyrics")
    print("lyrics files:", len(glob.glob(f"{DATA}/musdb_lyrics/**/*.txt", recursive=True)))
step("MUSDB18 lyrics extension", get_musdb_lyrics)

# 2. GTSinger English (8.7 GB)
def get_gtsinger():
    # GTSinger is ~20k small files and Hugging Face allows 5000 file requests per 5 minutes, so
    # fetch only what training reads (audio + word labels), and on a rate limit wait it out and
    # carry on: files already downloaded are skipped on the next attempt.
    import time
    from huggingface_hub import snapshot_download
    for attempt in range(15):
        try:
            snapshot_download("GTSinger/GTSinger", repo_type="dataset",
                              allow_patterns=["English/**/*.wav", "English/**/*.json"],
                              local_dir=f"{DATA}/GTSinger", max_workers=4,
                              token=os.environ.get("HF_TOKEN") or None)
            break
        except Exception as e:
            if "429" not in str(e):
                raise
            print(f"  Hugging Face rate limit (attempt {attempt+1}); waiting 5 min, then resuming...")
            time.sleep(310)
    else:
        print("  still rate-limited; continuing with the GTSinger files downloaded so far")
    print("wavs:", len(glob.glob(f"{DATA}/GTSinger/English/**/*.wav", recursive=True)))
if USE_GTSINGER:
    step("GTSinger English", get_gtsinger)
else:
    print("== GTSinger English: skipped (USE_GTSINGER = False; run 1 spent 90 min on rate limits and got 0 usable clips)")

# 3. Children's Song Dataset (1.9 GB)
def get_csd():
    if not glob.glob(f"{DATA}/CSD/**/english", recursive=True):
        subprocess.run(["wget", "-q", "-O", f"{DATA}/CSD.zip", "https://zenodo.org/records/4785016/files/CSD.zip?download=1"], check=True)
        zipfile.ZipFile(f"{DATA}/CSD.zip").extractall(f"{DATA}/CSD"); os.remove(f"{DATA}/CSD.zip")
step("Children's Song Dataset", get_csd)

# 4. Fallback accompaniment if MUSDB18-HQ didn't work: the free 7 s previews
def get_previews():
    os.makedirs(ACC, exist_ok=True)
    if len(os.listdir(ACC)) < 20:
        import musdb
        for t in musdb.DB(root=f"{DATA}/musdb7", download=True):
            sf.write(f"{ACC}/{t.name.replace('/', '_')}_preview.wav", t.targets["accompaniment"].audio, t.rate)
    print("accompaniment files:", len(os.listdir(ACC)))
step("accompaniment", get_previews)

# 5. JamendoLyrics (evaluation only)
def get_jamendo():
    if not os.path.isdir(f"{DATA}/jamendolyrics"):
        subprocess.run(["git", "clone", "-q", "--depth", "1", "https://github.com/f90/jamendolyrics", f"{DATA}/jamendolyrics"], check=True)
step("JamendoLyrics", get_jamendo)

import json
print(json.dumps(STATUS, indent=1))
print(subprocess.run(f"du -sh {DATA}/* ; df -h {DATA}", shell=True, capture_output=True, text=True).stdout)
''')

MANIFESTS = code(r'''
import glob, os, subprocess
TRAIN, VAL = [], []
def build(name, args):
    out = f"{DATA}/manifests/{name}"
    r = subprocess.run(f"python prepare_data.py {args} --out {out}", shell=True, text=True, capture_output=True)
    print(f"--- {name}\n" + r.stdout[-1500:] + (r.stderr[-1500:] if r.returncode else ""))
    if r.returncode == 0 and os.path.exists(f"{out}/train.jsonl") and os.path.getsize(f"{out}/train.jsonl") > 0:
        TRAIN.append(f"{out}/train.jsonl"); VAL.append(f"{out}/val.jsonl")

if glob.glob(f"{DATA}/musdb16k/*/*/mixture.wav") and glob.glob(f"{DATA}/musdb_lyrics/**/*.txt", recursive=True):
    build("musdb", f"musdb --musdb-root {DATA}/musdb16k --lyrics-dir {DATA}/musdb_lyrics")
if glob.glob(f"{DATA}/GTSinger/English/**/*.wav", recursive=True):
    build("gtsinger", f"utterances --root {DATA}/GTSinger/English")
csd = sorted(glob.glob(f"{DATA}/CSD/**/english", recursive=True))
if csd:
    lyr = f"{csd[0]}/lyric" if os.path.isdir(f"{csd[0]}/lyric") else f"{csd[0]}/txt"
    build("csd", f"longform --audio-dir {csd[0]}/wav --lyrics-dir {lyr}")
for i, d in enumerate(sorted({os.path.dirname(p) for p in glob.glob(f"{MY_MUSIC}/**/*.lrc", recursive=True)})):
    build(f"mylib{i}", f'lrc --music-dir "{d}"')
if not TRAIN:
    raise SystemExit("No training data could be prepared - see the messages above.")
if not any(("/musdb/" in t) or ("/mylib" in t) for t in TRAIN):
    # Children's songs alone (2 h, one singer style) aren't worth the GPU hours.
    raise SystemExit("No real-song data (MUSDB18 download failed and no songs of your own attached). "
                     "Stopping so you don't waste GPU time - run it again in a while.")
print("training on:", TRAIN)
''')

EVAL_BASE = code('!python evaluate_alignment.py --jamendo {DATA}/jamendolyrics --models facebook/wav2vec2-base-960h --out {RUN}/baseline.json')
TRAIN_CELL = code('''
train_args, val_args = " ".join(TRAIN), " ".join(VAL)
!python train.py --base "{BASE}" --train {train_args} --val {val_args} \\
  --accomp-dir {DATA}/accompaniment --out {RUN} --steps {STEPS} --lr {LR} --workers 2 --resume
''')
EXPORT = code('''
import os
if not os.path.isdir(f"{RUN}/best"):
    raise SystemExit("Training produced no model (see the training cell above). Nothing to export.")
!python export_onnx.py --checkpoint {RUN}/best --out {RUN}/model_int8.onnx --version {VERSION}
# Fair comparison: the starting model squeezed to int8 the same way, so the only difference
# between the last two rows is the training (int8 rounding alone moved some songs in run 1).
!python export_onnx.py --checkpoint "{BASE}" --out {DATA}/start_int8.onnx --version start
models = f"{DATA}/start_int8.onnx {RUN}/model_int8.onnx"
!python evaluate_alignment.py --jamendo {DATA}/jamendolyrics --models {models} --out {RUN}/alignment_report.json
''')
UPLOAD = code('''
import os
if HF_REPO and os.environ.get("HF_TOKEN"):
    from huggingface_hub import HfApi
    api = HfApi(token=os.environ["HF_TOKEN"]); api.create_repo(HF_REPO, exist_ok=True)
    api.upload_file(path_or_fileobj=f"{RUN}/model_int8.onnx", path_in_repo=f"{VERSION}.onnx", repo_id=HF_REPO)
    print(f"MODEL_URL = https://huggingface.co/{HF_REPO}/resolve/main/{VERSION}.onnx")
else:
    print(f"Not uploaded. model_int8.onnx is in {RUN}")
if os.path.exists(f"{RUN}/model_int8.onnx") and os.path.exists(f"{RUN}/resume.pt"):
    os.remove(f"{RUN}/resume.pt")   # ~1 GB; only needed while training
''')
SHIP = md('**Use the new model only if `model_int8.onnx` beats the others** on median error and "within 300 ms". Then send Claude the constants printed by the export step.')

def kaggle():
    cells = [
md("""
# PixelPlay word-sync model: singing fine-tune on real songs (free Kaggle GPU)
1. Right panel → **Session options**: Accelerator **GPU T4 x2**, Internet **On** (phone-verified account).
2. **Recommended: Add Input → your `kaggle_data_prep` notebook's output** (all datasets, already downloaded). Without it this notebook downloads everything itself, which can take many hours when Zenodo is slow.
3. Optional, best data: your own songs. Make a private Kaggle Dataset of audio files with a same-named synced `.lrc` next to each (e.g. `Song.mp3` + `Song.lrc`), then **Add Input** it here.
4. Optional: Add-ons → Secrets → `HF_TOKEN` to upload the finished model.
5. **Save Version → Save & Run All (Commit)**. Runs in the background; results in the **Output** tab.
"""),
code('''
STEPS, LR = 6000, 3e-5
USE_GTSINGER = False                      # slow (Hugging Face rate limits) and gave 0 usable clips in run 1
BASE    = "facebook/wav2vec2-base-960h"   # or a previous run's best/ folder attached as input
VERSION = "w2v2-base-960h-singing-int8-v1"
HF_REPO = ""
DATA    = "/tmp/pp_data"
RUN     = "/kaggle/working/runs/singing-v1"
KIT     = "/kaggle/working/singing_finetune"
MY_MUSIC = "/kaggle/input"                # any attached dataset with .lrc files is used
PREPARED_SOURCES = ["/kaggle/input"]      # attach the output of kaggle_data_prep.ipynb to skip downloads
import os, subprocess, glob, shutil
for d in (DATA, RUN): os.makedirs(d, exist_ok=True)
print(subprocess.run("nvidia-smi --query-gpu=name,memory.total --format=csv; df -h /tmp /kaggle/working", shell=True, capture_output=True, text=True).stdout)
try:
    from kaggle_secrets import UserSecretsClient
    os.environ["HF_TOKEN"] = UserSecretsClient().get_secret("HF_TOKEN"); print("HF_TOKEN loaded")
except Exception:
    print("no HF_TOKEN secret (only needed for the upload)")
prev = glob.glob("/kaggle/input/**/runs/singing-v1/resume.pt", recursive=True)
if prev and not os.path.exists(f"{RUN}/resume.pt"):
    shutil.copytree(os.path.dirname(prev[0]), RUN, dirs_exist_ok=True); print("resuming from", prev[0])
'''),
        WRITE_KIT, PIP, DATA_MD, DATA, md("## Training lists"), MANIFESTS,
        md("## Baseline: the app's current model on real songs"), EVAL_BASE,
        md("## Train"), TRAIN_CELL, md("## Export + compare"), EXPORT, SHIP, UPLOAD]
    return cells

def colab():
    cells = [
md("""
# PixelPlay word-sync model: singing fine-tune on Google Colab
Everything is kept in Google Drive under `MyDrive/pixelplay-singing/`, so nothing is downloaded twice and
an interrupted run picks up where it stopped.

**Step A - data, once (no GPU needed):** Runtime → Change runtime type → **CPU**. Set `PREP_ONLY = True`
in the first code cell, then Runtime → Run all. The datasets go to `MyDrive/pixelplay-singing/pp_stage/`.
Zenodo can be slow: if it stops or the session ends, just Run all again - it continues. Done when it says
`DATA READY`.

**Step B - train:** Runtime → Change runtime type → **GPU** (A100 or L4 if your plan offers them, else T4).
Set `PREP_ONLY = False`, then Run all. Keep the tab open; if it disconnects, Run all again - it resumes.

Optional: your own songs with a same-named synced `.lrc` each in `MyDrive/pixelplay-singing/my_music/`
(the app's Export training set folder). A previous model's `best` folder as
`MyDrive/pixelplay-singing/round1-best/` makes this a gentler second round. 🔑 Secrets → `HF_TOKEN` to upload.
"""),
code('''
PREP_ONLY = False                    # True on a CPU runtime = only download the data into Drive
ROOT     = "/content/drive/MyDrive/pixelplay-singing"
PREVIOUS = f"{ROOT}/round1-best"
STEPS, LR = 3000, 1e-5
USE_GTSINGER = False
VERSION  = "w2v2-base-960h-singing-int8-v2"
HF_REPO  = ""
DATA     = f"{ROOT}/pp_stage" if PREP_ONLY else "/content/pp_data"   # training copies Drive data to fast local disk
RUN      = f"{ROOT}/runs/singing-v2"
KIT      = "/content/singing_finetune"
MY_MUSIC = f"{ROOT}/my_music"
PREPARED_SOURCES = [ROOT]                 # pp_data_pack/*.tar from the prep notebook, copied to Drive
from google.colab import drive
drive.mount("/content/drive")
import os, subprocess
for d in (DATA, RUN): os.makedirs(d, exist_ok=True)
BASE = PREVIOUS if os.path.exists(f"{PREVIOUS}/config.json") else "facebook/wav2vec2-base-960h"
if BASE != PREVIOUS: STEPS, LR = 6000, 3e-5   # no previous model: do a full first round
print("starting from:", BASE)
try:
    from google.colab import userdata
    os.environ["HF_TOKEN"] = userdata.get("HF_TOKEN"); print("HF_TOKEN loaded")
except Exception:
    print("no HF_TOKEN secret (only needed for the upload)")
print(subprocess.run("nvidia-smi --query-gpu=name,memory.total --format=csv; df -h /content", shell=True, capture_output=True, text=True).stdout)
'''),
        WRITE_KIT, PIP, DATA_MD, DATA,
        code('''
if PREP_ONLY:
    ok = len(glob.glob(f"{DATA}/musdb16k/*/*/vocals.wav")) >= 50
    raise SystemExit("DATA READY - switch to a GPU runtime, set PREP_ONLY = False and Run all" if ok
                     else "NOT READY yet (Zenodo was slow) - Run all again to continue")
'''),
        md("## Training lists"), MANIFESTS,
        md("## Train (resumes from Drive after a disconnect)"), TRAIN_CELL, md("## Export + compare"), EXPORT, SHIP, UPLOAD]
    return cells

def prep():
    cells = [
md("""
# PixelPlay word-sync model: download the training data once (CPU, no GPU hours)
Downloads and shrinks every dataset (MUSDB18-HQ real songs, their lyrics, children's songs, JamendoLyrics),
then packs them into `pp_data_pack/*.tar` in the Output. The training notebook reads them from there in
a minute instead of downloading for hours.

1. Right panel → **Session options**: Accelerator **None** (CPU only, so no GPU quota is used), Internet **On**.
2. **Save Version → Save & Run All (Commit)**.
3. If it times out (Zenodo can be very slow), open the notebook again, **Add Input → this notebook's own
   Output**, and run it again: it continues where it stopped.
4. When the log ends with `READY`, open the training notebook, **Add Input → this notebook's Output**, run it.
"""),
code('''
DATA = "/kaggle/working/pp_stage"         # kept in the Output while downloading (resumable)
PACK = "/kaggle/working/pp_data_pack"
PREPARED_SOURCES = ["/kaggle/input"]      # a previous (timed-out) version of this notebook, attached as input
USE_GTSINGER = False
import os, subprocess
os.makedirs(DATA, exist_ok=True)
print(subprocess.run("df -h /kaggle/working", shell=True, capture_output=True, text=True).stdout)
'''),
        code('!pip -q install "soundfile>=0.12" musdb huggingface_hub remotezip'),
        DATA_MD, DATA,
        code('''
# Pack into a few tar files (fast to attach and extract), then remove the unpacked copy.
import tarfile, os, glob, shutil, json
os.makedirs(PACK, exist_ok=True)
ok = len(glob.glob(f"{DATA}/musdb16k/*/*/vocals.wav")) >= 50 and len(glob.glob(f"{DATA}/musdb_lyrics/**/*.txt", recursive=True)) >= 100
parts = {"musdb16k": "musdb16k", "accompaniment": "accompaniment", "musdb_lyrics": "musdb_lyrics", "jamendolyrics": "jamendolyrics"}
csd = sorted(glob.glob(f"{DATA}/CSD/**/english", recursive=True))
if ok:
    for name, rel in parts.items():
        if os.path.exists(f"{DATA}/{rel}"):
            with tarfile.open(f"{PACK}/{name}.tar", "w") as t:
                t.add(f"{DATA}/{rel}", arcname=rel, filter=lambda ti: None if "/.git" in ti.name else ti)
    if csd:
        with tarfile.open(f"{PACK}/csd.tar", "w") as t:
            t.add(csd[0], arcname=os.path.relpath(csd[0], DATA))
    json.dump({"songs": len(glob.glob(f"{DATA}/musdb16k/*/*/vocals.wav"))}, open(f"{PACK}/info.json", "w"))
    shutil.rmtree(DATA)
    print(subprocess.run(f"du -sh {PACK}/*", shell=True, capture_output=True, text=True).stdout)
    print("READY - attach this notebook's Output to the training notebook")
else:
    print("NOT READY: MUSDB18 is incomplete. Run this notebook again with its own Output attached; it continues.")
''')]
    return cells

def save(name, cells, colab=False):
    meta = {"kernelspec":{"display_name":"Python 3","language":"python","name":"python3"},"language_info":{"name":"python"},"accelerator":"GPU"}
    if colab: meta["colab"] = {"provenance":[]}
    pathlib.Path(name).write_text(json.dumps({"cells":cells,"metadata":meta,"nbformat":4,"nbformat_minor":5}, indent=1), encoding="utf-8")
    print("wrote", name)
save("kaggle_singing_finetune.ipynb", kaggle())
save("colab_round2.ipynb", colab(), colab=True)
save("kaggle_data_prep.ipynb", prep())
