# Eisbad – geführte Atem- und Kälte-Session (Android)

Kern-MVP: Atemrunden, Atemhalten, Erholungsatem, Einstieg und Kälte-Timer mit Sprachansagen
und eigener Musik. Das Handy liegt am Beckenrand, Ton läuft über Kopfhörer (Foreground-Service,
läuft auch bei gesperrtem Bildschirm).

## Starten
1. Ordner `eisbad` in Android Studio öffnen (Ladybug oder neuer, JDK 17). Gradle-Sync abwarten.
2. Handy per USB anschließen oder Emulator wählen, dann **Run**.

## Modi
Oben in der App wählen: **Atmung + Eisbad** (ganzer Ablauf), **Nur Atmung** (Runden, Atemhalten,
Erholungsatem, danach Abschluss-Ansage) oder **Nur Eisbad** (Einstiegszeit, dann Kälte-Timer).
Es werden nur die Einstellungen des gewählten Modus angezeigt. Der Verlauf merkt sich den Modus.

## Aussehen
Vier Farbthemen (Schiefer, Wald, Beere, Hell) unter **Aussehen**; Auswahl gilt sofort.

## Stimmen
Die Stimmen stehen in `app/src/main/assets/voices.json` (id, Anzeigename, edge-tts-Stimme für de/en,
Tempo, Tonhöhe). In der App unter **Stimme & Musik** wählen, jede Auswahl spielt eine Hörprobe.
Die Clips liegen in `app/src/main/assets/voice/<stimme>/<de|en>/<key>.mp3`. Fehlt ein Clip, nimmt die
App die alten Standard-Clips (`voice/<de|en>/<key>.mp3`) und zuletzt die Systemstimme.

**Kostenlos erzeugen (nur mit dem Handy):** GitHub > Actions > "Stimmen erzeugen (kostenlos)" >
Run workflow (Standard `all` erzeugt alle Stimmen). Danach baut der Workflow PWA und APK automatisch neu.
Neue Texte: `phrases.json` bearbeiten, Workflow erneut starten. Anderes Tempo/andere Stimme: in
`voices.json` ändern und den Workflow mit `force = true` starten.

Lokal: `pip install edge-tts && python3 tools/generate_voice_edge.py --set florian`.

Alternative mit Bezahldiensten (alte Clips in `voice/<de|en>/`): `tools/generate_voice.py`
(ElevenLabs oder OpenAI), Workflow "Stimme erzeugen".

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
Die Dateien im Repo-Root (index.html, app.js, sw.js ...) sind eine installierbare Web-App (offline-fähig, mit Verlauf, Profilen, Export).
GitHub Pages (Branch main, Root) liefert sie direkt aus:
`https://<benutzername>.github.io/eisbad/`
Schnelltest: URL mit `?speed=20` öffnen, dann läuft eine Session 20-fach schneller.
Nach Updates die App einmal schließen und neu öffnen (der Service Worker lädt die neue Version im Hintergrund).
