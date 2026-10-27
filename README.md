# Eisbad – geführte Atem- und Kälte-Session (Android)

Kern-MVP: Atemrunden, Atemhalten, Erholungsatem, Einstieg und Kälte-Timer mit Sprachansagen
und eigener Musik. Das Handy liegt am Beckenrand, Ton läuft über Kopfhörer (Foreground-Service,
läuft auch bei gesperrtem Bildschirm).

## Starten
1. Ordner `eisbad` in Android Studio öffnen (Ladybug oder neuer, JDK 17). Gradle-Sync abwarten.
2. Handy per USB anschließen oder Emulator wählen, dann **Run**.

## Stimme erzeugen (einmalig)
Die App nutzt vorab erzeugte Clips aus `app/src/main/assets/voice/<de|en>/<key>.mp3`.
Fehlt ein Clip, spricht die Android-Systemstimme (tiefer gestellt) als Notlösung.

```
# ElevenLabs: in der Voice Library eine tiefe, ruhige Männerstimme wählen, voice_id kopieren
export ELEVENLABS_API_KEY=...
export ELEVENLABS_VOICE_ID=...
python3 tools/generate_voice.py --provider elevenlabs --lang de en

# oder OpenAI (Stimme "onyx" ist tief)
export OPENAI_API_KEY=...
python3 tools/generate_voice.py --provider openai --voice onyx --lang de en
```
Danach die App neu bauen. Texte ändern: `app/src/main/assets/phrases.json` bearbeiten und das
Skript mit `--force` erneut laufen lassen. 54 Ansagen × 2 Sprachen = 108 kurze Clips.

## Wie sich die Ansagen anpassen
- Atmung: Start, ein Drittel, zwei Drittel, letzte Atemzüge (passt zur Zahl der Atemzüge)
- Atemhalten: alle 30 s, "noch zehn Sekunden"; Taste **Weiter** beendet vorzeitig
- Kälte: Start, volle Minuten, Halbzeit, Restzeit (60/30/10 s), dazwischen Motivation; alles
  berechnet aus der gewählten Dauer. Der Timer startet nach der Einstiegszeit automatisch.

## Musik
Über **Musik wählen** eine eigene Audiodatei auswählen (läuft in Schleife, wird beim Sprechen leiser).

## Offen für spätere Versionen
Verlauf/Statistik, Atemhalte-Messung, Profile, App-Oberfläche in English.

## Nur mit dem Handy (ohne Android Studio)
1. Neues privates Repo bei GitHub anlegen und den Projektinhalt hochladen (Branch `main`).
2. Repo > Settings > Secrets and variables > Actions: `ELEVENLABS_API_KEY` (oder `OPENAI_API_KEY`) anlegen.
3. Actions > "Stimme erzeugen" > Run workflow, Stimme eintragen. Die Clips landen im Repo.
4. Actions > "APK bauen" (startet nach jedem Push automatisch) > fertigen Lauf öffnen > Artifact `eisbad-apk` herunterladen.
5. ZIP entpacken, `app-debug.apk` öffnen und installieren (Installation aus dieser Quelle erlauben).

## PWA-Version (empfohlen fürs Handy)
Ordner `web/` ist eine installierbare Web-App (offline-fähig, mit Verlauf, Profilen, Export).
Sie wird bei jedem Push per GitHub Pages veröffentlicht (Workflow "PWA veröffentlichen"):
`https://<benutzername>.github.io/eisbad/`
Einmalig nötig: Repo auf **Public** stellen (Pages ist für private Repos nur im Bezahltarif verfügbar) und unter
Settings → Pages → Source **GitHub Actions** wählen. Im Chrome-Menü "App installieren" bzw. "Zum Startbildschirm".
Schnelltest: URL mit `?speed=20` öffnen, dann läuft eine Session 20-fach schneller.
Stimme kostenlos neu erzeugen: `pip install edge-tts && python3 tools/generate_voice_edge.py --force`
