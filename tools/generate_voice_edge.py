#!/usr/bin/env python3
"""Kostenlose Stimmen für alle Ansagen. Die Stimmen stehen in app/src/main/assets/voices.json,
jede landet in voice/<stimme>/<de|en>/<key>.mp3.

Zwei Engines:
  edge   Microsoft-Neural-Stimmen (edge-tts), Tempo/Tonhöhe per rate/pitch
  piper  lokal laufende Piper-Stimmen (Open Source). Danach wird die Tonhöhe per ffmpeg
         abgesenkt und der Klang gefärbt (fx: warm = warm/ruhig, smoky = dunkel/rauchig).

  pip install edge-tts piper-tts==1.3.0     (ffmpeg muss installiert sein)
  python3 tools/generate_voice_edge.py                    # alle Stimmen, nur fehlende Clips
  python3 tools/generate_voice_edge.py --set rauchig      # nur eine Stimme
  python3 tools/generate_voice_edge.py --force            # alles neu
"""
import argparse
import asyncio
import json
import subprocess
import sys
import tempfile
import time
import urllib.request
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"
PHRASES = ASSETS / "phrases.json"
VOICES = ASSETS / "voices.json"
OUT = ASSETS / "voice"
MODELS = ROOT / "tools/.models"

# Klangfärbung nach der Tonhöhen-Absenkung (ffmpeg-Filter)
FX = {
    "warm": (
        "highpass=f=60,bass=g=4:f=130:w=0.8,lowpass=f=8500,"
        "acompressor=threshold=0.1:ratio=3:attack=20:release=250:makeup=2,"
        "aecho=0.8:0.5:55:0.12,loudnorm=I=-17:TP=-2:LRA=7"
    ),
    "smoky": (
        "highpass=f=60,bass=g=7:f=110:w=0.9,highshelf=f=4500:g=-7,lowpass=f=6500,"
        "volume=2.2,asoftclip=type=tanh,volume=0.6,"
        "acompressor=threshold=0.1:ratio=4:attack=20:release=250:makeup=2,"
        "aecho=0.8:0.55:70|120:0.16|0.08,loudnorm=I=-17:TP=-2:LRA=7"
    ),
}


# ---------- edge ----------
async def edge_one(text, voice, rate, pitch, target):
    import edge_tts
    for attempt in range(5):
        try:
            await edge_tts.Communicate(text, voice, rate=rate, pitch=pitch).save(str(target))
            if target.stat().st_size > 1000:
                return True
        except Exception as e:  # Netzwerk-Hänger
            print("   Fehler:", e)
        await asyncio.sleep(2 * (attempt + 1))
    target.unlink(missing_ok=True)
    return False


async def run_edge(items, parallel):
    sem = asyncio.Semaphore(parallel)
    failed = []

    async def work(n, it):
        sid, lang, key, text, s, target = it
        async with sem:
            target.parent.mkdir(parents=True, exist_ok=True)
            ok = await edge_one(text, s[lang], s["rate"], s["pitch"], target)
            print(f"[edge {n}/{len(items)}] {sid}/{lang}/{key} {'ok' if ok else 'FEHLER'}")
            if not ok:
                failed.append(f"{sid}/{lang}/{key}")

    await asyncio.gather(*(work(n, it) for n, it in enumerate(items, 1)))
    return failed


# ---------- piper ----------
def download(url, dest):
    """Lädt eine Datei mit Wiederholung (Hugging Face antwortet gelegentlich mit 429)."""
    req = urllib.request.Request(url, headers={"User-Agent": "eisbad-voice/1.0"})
    for attempt in range(8):
        try:
            with urllib.request.urlopen(req, timeout=300) as r, open(dest, "wb") as f:
                while chunk := r.read(1 << 20):
                    f.write(chunk)
            return
        except Exception as e:
            wait = 15 * (attempt + 1)
            print(f"   Download-Fehler ({e!r}), neuer Versuch in {wait} s")
            time.sleep(wait)
    raise RuntimeError(f"Download fehlgeschlagen: {url}")


def fetch_model(name):
    MODELS.mkdir(parents=True, exist_ok=True)
    onnx = MODELS / f"{name}.onnx"
    if onnx.exists() and onnx.stat().st_size > 1_000_000:
        return onnx
    locale, voice, quality = name.split("-")
    base = f"https://huggingface.co/rhasspy/piper-voices/resolve/main/{locale.split('_')[0]}/{locale}/{voice}/{quality}/{name}"
    for ext in (".onnx.json", ".onnx"):
        print("Lade", base + ext)
        download(base + ext, MODELS / f"{name}{ext}")
    return onnx


def run_piper(items):
    from piper import PiperVoice, SynthesisConfig
    failed = []
    voices = {}
    broken = set()
    for n, (sid, lang, key, text, s, target) in enumerate(items, 1):
        try:
            name = s["model"][lang]
            if name in broken:
                raise RuntimeError(f"Modell {name} nicht verfügbar")
            if name not in voices:
                onnx = fetch_model(name)
                sr = json.loads((MODELS / f"{name}.onnx.json").read_text())["audio"]["sample_rate"]
                voices[name] = (PiperVoice.load(str(onnx)), sr)
            voice, sr = voices[name]
            target.parent.mkdir(parents=True, exist_ok=True)
            with tempfile.TemporaryDirectory() as td:
                raw = Path(td) / "raw.wav"
                with wave.open(str(raw), "wb") as wf:
                    voice.synthesize_wav(text, wf, syn_config=SynthesisConfig(length_scale=s["length_scale"]))
                f = s["pitch"]
                af = f"asetrate={int(sr * f)},atempo={1 / f:.4f},aresample=44100," + FX[s["fx"]]
                subprocess.run(
                    ["ffmpeg", "-y", "-loglevel", "error", "-i", str(raw), "-af", af,
                     "-ac", "1", "-b:a", "96k", str(target)], check=True)
            ok = target.exists() and target.stat().st_size > 1000
        except Exception as e:
            print("   Fehler:", repr(e))
            if "Download" in str(e):
                broken.add(s["model"][lang])
            ok = False
        print(f"[piper {n}/{len(items)}] {sid}/{lang}/{key} {'ok' if ok else 'FEHLER'}")
        if not ok:
            target.unlink(missing_ok=True)
            failed.append(f"{sid}/{lang}/{key}")
    return failed


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--set", nargs="+", default=["all"], help="Stimmen-IDs aus voices.json oder 'all'")
    ap.add_argument("--lang", nargs="+", default=["de", "en"])
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--parallel", type=int, default=3)
    ap.add_argument("--only", nargs="*", help="nur diese Schlüssel (zum Testen)")
    a = ap.parse_args()

    sets = {s["id"]: s for s in json.loads(VOICES.read_text(encoding="utf-8"))["sets"]}
    ids = list(sets) if "all" in a.set else a.set
    for i in ids:
        if i not in sets:
            sys.exit(f"Unbekannte Stimme '{i}'. Verfügbar: {', '.join(sets)}")

    phrases = json.loads(PHRASES.read_text(encoding="utf-8"))
    edge_items, piper_items = [], []
    for sid in ids:
        s = sets[sid]
        for lang in a.lang:
            for key, texts in phrases.items():
                if a.only and key not in a.only:
                    continue
                target = OUT / sid / lang / f"{key}.mp3"
                if target.exists() and not a.force:
                    continue
                (piper_items if s["engine"] == "piper" else edge_items).append(
                    (sid, lang, key, texts[lang], s, target))
    print(f"{len(edge_items)} edge-Clips, {len(piper_items)} piper-Clips")

    failed = []
    if piper_items:
        failed += run_piper(piper_items)
    if edge_items:
        failed += asyncio.run(run_edge(edge_items, a.parallel))
    if failed:
        sys.exit(f"{len(failed)} Clips fehlgeschlagen, bitte Lauf wiederholen: {', '.join(failed[:10])} ...")
    print("Fertig.")


main()
