# AI APK Studio

Native Android-MVP für ein mobiles KI-Entwicklungsstudio im **neomorphen Design**. Ziel: App-Idee beschreiben → KI ändert Projektdateien → Gradle/Termux Studio baut → Buildfehler werden automatisch an die KI zurückgespielt → APK installieren.

## Umgesetzter Funktionsumfang

- Kotlin + Jetpack Compose + Material 3
- Neomorphes Light/Dark UI mit eigenen `NeoCard`/`NeoActionButton`-Komponenten
- Room: Projekte, Chats, KI-Provider und Builds
- DataStore: Dark Mode und Anzahl der Reparaturdurchgänge
- Android Keystore: API-Keys AES/GCM-verschlüsselt
- NVIDIA NIM und andere OpenAI-kompatible `/chat/completions`-APIs
- Quick-App-Template (WebView + HTML/CSS/JS)
- Native-Android-Template (Kotlin + Jetpack Compose)
- automatische Auswahl Quick App / Native Android
- Termux `RUN_COMMAND` mit stdout/stderr/exitCode-Rückgabe
- echtes Lesen/Schreiben/Löschen von Projektdateien in Termux
- Dateibaum + Quelltextvorschau
- strukturierter KI-Agent (`files`, `delete`, `build` als JSON-Plan)
- Build-Engine mit bevorzugter Termux-Studio-Nutzung und Gradle-Fallback
- bis zu 1–8 automatische Build-Reparaturdurchgänge
- Buildlogs und APK-Pfad in Room
- Übergabe der fertigen APK an den Android-Package-Installer
- lokales Git pro Projekt: Snapshot nach Template/KI-Reparaturen, Historie und Undo
- Systemdiagnose und Build-Engine-Installation aus der App

## Architektur

```text
AI APK Studio (Compose UI)
        │
        ├── Room / DataStore / Android Keystore
        │
        ├── OpenAICompatibleClient
        │       └── NVIDIA NIM / eigener kompatibler Endpoint
        │
        └── Termux RUN_COMMAND
                ├── Projektdateien
                ├── lokales Git
                └── Termux Studio / Gradle
                        └── APK
```

## Android-Build des AI-APK-Studio-Projekts

Voraussetzungen auf einem PC/CI:

- JDK 17+
- Android SDK 36
- Gradle 8.11.1 passend zu AGP 8.10.x
- Internetzugriff für AndroidX/Maven-Artefakte

In Android Studio das Projekt öffnen und `app` bauen oder mit einer passenden Gradle-Installation:

```bash
gradle assembleDebug
```

APK-Ausgabe:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Einrichtung auf dem Android-Gerät

1. Aktuelles **Termux von F-Droid oder GitHub** installieren (nicht die veraltete Play-Store-Ausgabe).
2. In Termux `~/.termux/termux.properties` anlegen bzw. ergänzen:

```text
allow-external-apps=true
```

3. Termux neu starten und AI APK Studio die `RUN_COMMAND`-Berechtigung gewähren.
4. In AI APK Studio → **Einstellungen → Build Engine installieren**. Die App installiert/aktualisiert Termux Studio und führt anschließend `studio doctor` aus.
5. NVIDIA oder einen anderen OpenAI-kompatiblen Provider einrichten.

Alternativ kann `termux/install-build-engine.sh` manuell in Termux ausgeführt werden.

## NVIDIA NIM

Standardwerte:

```text
Name: NVIDIA NIM
Base URL: https://integrate.api.nvidia.com/v1
API-Key: nvapi-...
Model: Modell-ID von build.nvidia.com
```

Der Schlüssel wird nur verschlüsselt im Android Keystore/verschlüsselten Payload gespeichert und nicht in Projekte oder Git-Commits geschrieben.

## Agent-Ablauf

```text
Nutzerauftrag
   ↓
Projektdateien auswählen/lesen
   ↓
KI liefert strukturierten JSON-Plan
   ↓
Dateien schreiben/löschen
   ↓
Git-Snapshot
   ↓
Build
   ↓
Fehler? ── ja ─→ Buildlog + betroffene Dateien → KI-Reparatur → neuer Snapshot → Build
   │
   nein
   ↓
APK-Pfad speichern
   ↓
Installieren
```

Die Agent-Ausgabe muss ausschließlich ein JSON-Objekt mit `summary`, `files`, `delete` und `build` enthalten. Pfade werden auf das aktive Projekt beschränkt; `..`, absolute Pfade und NUL-Zeichen werden abgewiesen.

## Wichtige Sicherheitsgrenzen

- API-Keys werden nicht in Logs, Projektdateien oder Git geschrieben.
- Dateizugriffe des Agenten werden auf relative Projektpfade eingeschränkt.
- Shell-Befehle werden von der App selbst vorgegeben; der KI-Agent erhält keine freie Shell.
- Jede erfolgreiche KI-Dateiänderung wird lokal versioniert und kann zurückgesetzt werden.

## Noch nicht im MVP

- vollständiger manueller Codeeditor (Dateien können derzeit gelesen und vom Agenten geschrieben werden)
- GitHub Push/Pull UI
- AAB/Play-Store-Veröffentlichung
- Vision/Screenshot-Anhänge
- Anthropic-/Gemini-proprietäre APIs (OpenAI-kompatible Gateways funktionieren bereits)
- Cloud-Synchronisierung / Benutzerkonto

Diese Punkte sind bewusst nicht als funktionslose Buttons eingebaut.
