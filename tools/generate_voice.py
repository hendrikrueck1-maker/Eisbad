#!/usr/bin/env python3
"""Erzeugt alle Sprachansagen als MP3 aus app/src/main/assets/phrases.json.

Beispiele:
  export ELEVENLABS_API_KEY=...   export ELEVENLABS_VOICE_ID=...
  python3 tools/generate_voice.py --provider elevenlabs --lang de en

  export OPENAI_API_KEY=...
  python3 tools/generate_voice.py --provider openai --voice onyx --lang de

  python3 tools/generate_voice.py --dry-run     # zeigt nur, was erzeugt würde
"""
import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PHRASES = ROOT / "app/src/main/assets/phrases.json"
OUT = ROOT / "app/src/main/assets/voice"

OPENAI_STYLE = (
    "Sprich mit einer tiefen, ruhigen, warmen und zugleich motivierenden Stimme. "
    "Langsames, gleichmäßiges Tempo, klare Betonung, wie ein erfahrener Coach."
)


def post(url, headers, payload):
    req = urllib.request.Request(
        url, data=json.dumps(payload).encode("utf-8"), headers=headers, method="POST"
    )
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()


def elevenlabs(text, voice_id, key):
    return post(
        f"https://api.elevenlabs.io/v1/text-to-speech/{voice_id}?output_format=mp3_44100_128",
        {"xi-api-key": key, "Content-Type": "application/json", "Accept": "audio/mpeg"},
        {
            "text": text,
            "model_id": "eleven_multilingual_v2",
            "voice_settings": {
                "stability": 0.55,
                "similarity_boost": 0.8,
                "style": 0.25,
                "use_speaker_boost": True,
            },
        },
    )


def openai(text, voice, key, lang):
    style = OPENAI_STYLE if lang == "de" else (
        "Speak in a deep, calm, warm yet motivating voice. Slow, steady pace, like an experienced coach."
    )
    return post(
        "https://api.openai.com/v1/audio/speech",
        {"Authorization": f"Bearer {key}", "Content-Type": "application/json"},
        {
            "model": "gpt-4o-mini-tts",
            "voice": voice,
            "input": text,
            "instructions": style,
            "response_format": "mp3",
        },
    )


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--provider", choices=["elevenlabs", "openai"], default="elevenlabs")
    ap.add_argument("--lang", nargs="+", default=["de", "en"])
    ap.add_argument("--voice", default=None, help="ElevenLabs voice_id oder OpenAI-Stimme (z. B. onyx)")
    ap.add_argument("--force", action="store_true", help="vorhandene Dateien überschreiben")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()

    phrases = json.loads(PHRASES.read_text(encoding="utf-8"))

    if a.provider == "elevenlabs":
        key = os.environ.get("ELEVENLABS_API_KEY")
        voice = a.voice or os.environ.get("ELEVENLABS_VOICE_ID")
    else:
        key = os.environ.get("OPENAI_API_KEY")
        voice = a.voice or "onyx"
    if not a.dry_run and (not key or not voice):
        sys.exit("API-Key bzw. Stimme fehlt (siehe Kopf der Datei).")

    jobs = []
    for lang in a.lang:
        for k, texts in phrases.items():
            if lang not in texts:
                sys.exit(f"Text fehlt: {k}/{lang}")
            target = OUT / lang / f"{k}.mp3"
            if target.exists() and not a.force:
                continue
            jobs.append((lang, k, texts[lang], target))

    print(f"{len(jobs)} Clips zu erzeugen")
    for i, (lang, k, text, target) in enumerate(jobs, 1):
        print(f"[{i}/{len(jobs)}] {lang}/{k}: {text}")
        if a.dry_run:
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        for attempt in range(3):
            try:
                audio = (
                    elevenlabs(text, voice, key)
                    if a.provider == "elevenlabs"
                    else openai(text, voice, key, lang)
                )
                target.write_bytes(audio)
                break
            except urllib.error.HTTPError as e:
                print(f"  Fehler {e.code}: {e.read()[:200]!r}")
                if e.code in (401, 403):
                    sys.exit("Zugang abgelehnt – API-Key prüfen.")
                time.sleep(2 * (attempt + 1))
        else:
            sys.exit(f"Abbruch bei {lang}/{k}")
        time.sleep(0.3)
    print("Fertig. Danach die App neu bauen.")


if __name__ == "__main__":
    main()
