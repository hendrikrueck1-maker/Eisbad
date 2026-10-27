#!/usr/bin/env python3
"""Kostenlose Variante: erzeugt alle Ansagen mit Microsoft-Neural-Stimmen (edge-tts).
Kein Account und kein API-Key nötig.

  pip install edge-tts
  python3 tools/generate_voice_edge.py                 # alles, nur fehlende Clips
  python3 tools/generate_voice_edge.py --force         # alles neu
  python3 tools/generate_voice_edge.py --pitch -14Hz   # noch tiefer

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
PHRASES = ROOT / "app/src/main/assets/phrases.json"
OUT = ROOT / "app/src/main/assets/voice"
VOICES = {"de": "de-DE-ConradNeural", "en": "en-US-ChristopherNeural"}


async def one(text, voice, rate, pitch, target):
    for attempt in range(4):
        try:
            await edge_tts.Communicate(text, voice, rate=rate, pitch=pitch).save(str(target))
            if target.stat().st_size > 1000:
                return True
        except Exception as e:  # Netzwerk-Hänger
            print("   Fehler:", e)
        await asyncio.sleep(2 * (attempt + 1))
    return False


async def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--lang", nargs="+", default=["de", "en"])
    ap.add_argument("--rate", default="-12%", help="Tempo, z. B. -12%% = etwas langsamer")
    ap.add_argument("--pitch", default="-10Hz", help="Tonhöhe, negativ = tiefer")
    ap.add_argument("--force", action="store_true")
    a = ap.parse_args()

    phrases = json.loads(PHRASES.read_text(encoding="utf-8"))
    todo = []
    for lang in a.lang:
        for key, texts in phrases.items():
            target = OUT / lang / f"{key}.mp3"
            if target.exists() and not a.force:
                continue
            todo.append((lang, key, texts[lang], target))
    print(f"{len(todo)} Clips")
    for i, (lang, key, text, target) in enumerate(todo, 1):
        target.parent.mkdir(parents=True, exist_ok=True)
        print(f"[{i}/{len(todo)}] {lang}/{key}")
        if not await one(text, VOICES[lang], a.rate, a.pitch, target):
            sys.exit(f"Abbruch bei {lang}/{key}")
        await asyncio.sleep(0.2)
    print("Fertig.")


asyncio.run(main())
