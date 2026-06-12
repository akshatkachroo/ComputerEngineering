# ScribeSync

An offline-first enterprise meeting logger for Android. Captures, transcribes, and diarizes meetings entirely on-device — no internet, no cloud, no data leaving your phone.

## Prerequisites

| Tool | Version |
|---|---|
| Android Studio | Hedgehog (2023.1.1) or newer |
| JDK | 17 or newer |
| Android SDK | API 35 (Android 15) |
| Android device / emulator | API 26+ (Android 8.0) |

## Running Locally

### 1. Clone and open

```bash
git clone <repo-url>
```

Open **Android Studio** → `File` → `Open` → select the `ScribeSync/` folder (not the repo root).

### 2. Sync Gradle

Android Studio will prompt you to sync — click **Sync Now**. This downloads all dependencies and the Gradle distribution (~150 MB on first run).

If the sync bar does not appear automatically: `File` → `Sync Project with Gradle Files`.

### 3. Run on a device or emulator

- **Physical device**: enable USB Debugging (`Settings → Developer Options → USB Debugging`), connect via USB, select it in the device dropdown.
- **Emulator**: `Device Manager` → `Create Device` → pick a Pixel profile with API 26+.

Click the green **Run** button (or `Shift+F10`). The app will build and launch.

### 4. Grant microphone permission

On first launch tap **New Recording** — the system will prompt for microphone access. Grant it to enable live transcription.

---

## Building from the Command Line

Requires the Gradle wrapper JAR. If it is missing, regenerate it:

```bash
# requires Gradle 8.7+ installed globally
gradle wrapper --gradle-version 8.7
```

Then build a debug APK:

```bash
# macOS / Linux
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

The output APK is at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Project Structure

```
ScribeSync/
├── app/src/main/java/com/scribesync/scribesync/
│   ├── MainActivity.kt          # Entry point, edge-to-edge setup
│   ├── data/
│   │   └── Meeting.kt           # Core data models (Meeting, TranscriptEntry)
│   ├── navigation/
│   │   └── NavGraph.kt          # Compose Navigation routes
│   └── ui/
│       ├── screens/
│       │   ├── HomeScreen.kt    # Meeting list + new recording FAB
│       │   └── RecordingScreen.kt  # Live recording + transcript view
│       └── theme/
│           ├── Color.kt
│           ├── Theme.kt
│           └── Type.kt
└── app/src/main/res/
    ├── drawable/                # Vector launcher icons
    ├── mipmap-anydpi-v26/       # Adaptive icon descriptors
    └── values/                  # strings.xml, themes.xml
```

## Feature Roadmap

- [ ] On-device STT via Whisper.cpp or ONNX Runtime
- [ ] Speaker diarization (on-device)
- [ ] Room database persistence for meeting history
- [ ] Export to Markdown / PDF
- [ ] Search across past transcripts
- [ ] Meeting summary via on-device LLM
