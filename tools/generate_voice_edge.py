#!/usr/bin/env python3
"""Kostenlose Stimmen: erzeugt alle Ansagen mit Microsoft-Neural-Stimmen (edge-tts).
Kein Account und kein API-Key nötig. Die Stimmen stehen in app/src/main/assets/voices.json,
jede Stimme landet in einem eigenen Ordner: voice/<stimme>/<de|en>/<key>.mp3

  pip install edge-tts
  python3 tools/generate_voice_edge.py                    # alle Stimmen, nur fehlende Clips
  python3 tools/generate_voice_edge.py --set florian      # nur eine Stimme
  python3 tools/generate_voice_edge.py --force            # alles neu

Hinweis: edge-tts nutzt den Vorlese-Dienst von Microsoft Edge (inoffiziell, für den
persönlichen Gebrauch gedacht).
"""
import argparse
import asyncio
import json
import sys
from pathlib import Path

import edge_tts

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"
PHRASES = ASSETS / "phrases.json"
VOICES = ASSETS / "voices.json"
OUT = ASSETS / "voice"


async def one(text, voice, rate, pitch, target):
    for attempt in range(4):
        try:
            await edge_tts.Communicate(text, voice, rate=rate, pitch=pitch).save(str(target))
            if target.stat().st_size > 1000:
                return True
        except Exception as e:  # Netzwerk-Hänger
            print("   Fehler:", e)
        await asyncio.sleep(2 * (attempt + 1))
    target.unlink(missing_ok=True)
    return False


async def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--set", nargs="+", default=["all"], help="Stimmen-IDs aus voices.json oder 'all'")
    ap.add_argument("--lang", nargs="+", default=["de", "en"])
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--parallel", type=int, default=4)
    a = ap.parse_args()

    sets = {s["id"]: s for s in json.loads(VOICES.read_text(encoding="utf-8"))["sets"]}
    ids = list(sets) if "all" in a.set else a.set
    for i in ids:
        if i not in sets:
            sys.exit(f"Unbekannte Stimme '{i}'. Verfügbar: {', '.join(sets)}")

    phrases = json.loads(PHRASES.read_text(encoding="utf-8"))
    todo = []
    for sid in ids:
        s = sets[sid]
        for lang in a.lang:
            for key, texts in phrases.items():
                target = OUT / sid / lang / f"{key}.mp3"
                if target.exists() and not a.force:
                    continue
                todo.append((sid, lang, key, texts[lang], s[lang], s["rate"], s["pitch"], target))
    print(f"{len(todo)} Clips")

    sem = asyncio.Semaphore(a.parallel)
    failed = []

    async def work(n, item):
        sid, lang, key, text, voice, rate, pitch, target = item
        async with sem:
            target.parent.mkdir(parents=True, exist_ok=True)
            ok = await one(text, voice, rate, pitch, target)
            print(f"[{n}/{len(todo)}] {sid}/{lang}/{key} {'ok' if ok else 'FEHLER'}")
            if not ok:
                failed.append(f"{sid}/{lang}/{key}")

    await asyncio.gather(*(work(n, it) for n, it in enumerate(todo, 1)))
    if failed:
        sys.exit(f"{len(failed)} Clips fehlgeschlagen, bitte Lauf wiederholen: {', '.join(failed[:10])} ...")
    print("Fertig.")


asyncio.run(main())
