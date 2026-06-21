# ScribeSync

ScribeSync is a local-first Android meeting recorder that captures microphone audio, transcribes speech on-device, and keeps a browsable history of meeting transcripts and summaries.

The project is built with Kotlin and Jetpack Compose. Speech-to-text runs locally through Whisper.cpp, so active transcription does not require an internet connection. Meeting data is stored on the device first and can optionally sync to Firebase Firestore when Firebase is configured and a network connection is available.

## Current Features

- Foreground microphone recording with a persistent Android notification
- On-device transcription using Whisper.cpp and the bundled quantized Whisper base model
- Live transcript display during a meeting
- Local meeting and transcript storage with Room
- Meeting history with transcript previews, durations, and timestamps
- Meeting detail view with rename and delete controls
- Meeting-location capture using Android location services
- Basic offline extractive summaries after recording
- Optional Firebase Firestore synchronization when online

## Current Limitations

- Speaker diarization is not implemented; transcript entries are currently labelled `Speaker 1`.
- Summaries use a simple local beginning/middle/end extraction rather than an LLM.
- Transcription quality and latency depend on the Android device and are still being tuned.
- Location coordinates are stored with a meeting but are not currently shown in the interface.
- Cloud sync is optional, but when enabled it sends meeting and transcript data to Firebase. The app should not be described as strictly on-device in that configuration.
- Export and transcript search are not implemented yet.

## How It Works

1. The user creates a meeting and grants microphone and location permissions.
2. A foreground service records 16 kHz mono PCM audio.
3. Audio is processed in overlapping windows by the native Whisper.cpp engine.
4. Transcript segments are displayed live and saved locally in Room.
5. When recording stops, the remaining audio is processed and a basic summary is generated.
6. If Firebase is configured and the device is online, unsynced meetings and transcript entries are uploaded to Firestore.

## Technology Stack

- Kotlin and Jetpack Compose
- Android foreground services and `AudioRecord`
- Whisper.cpp, GGML, JNI, CMake, and the Android NDK
- Room for local persistence
- Firebase Firestore for optional cloud sync
- Google Play Services Location
- Kotlin coroutines and Flow

## Repository Structure

```text
.
├── ScribeSync/                  # Android Studio project
│   ├── app/src/main/java/       # Kotlin application code
│   ├── app/src/main/cpp/        # JNI bridge and Whisper.cpp sources
│   └── app/src/main/assets/     # Bundled Whisper model
└── docs/                        # Team contract and meeting notes
```

## Run the App

### Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17 or newer
- Android SDK 35
- An Android device or emulator running API 26 or newer

### Setup

1. Clone the repository.
2. In Android Studio, open the `ScribeSync/` directory rather than the repository root.
3. Allow Gradle to sync and install any requested Android SDK, CMake, or NDK components.
4. Select a device or emulator and run the app.
5. Start a new recording and grant microphone and location permissions when prompted.

The bundled Whisper model is approximately 82 MB, so cloning, syncing, and building the project may take longer than a typical small Android app.

### Optional Firebase Sync

The recording and transcription features do not depend on Firebase at runtime. However, the current Gradle configuration applies the Google Services plugin, so building the project as-is requires an Android Firebase project for the application ID `com.scribesync.scribesync` and its `google-services.json` file in `ScribeSync/app/`. Do not commit credentials or private configuration that should remain local.

## Team

- Akshat Kachroo (`akshatkachroo`)
- Eric Wang (`ericyifwang`)
- Ryan Gong (`ryanyufangong`)
- James Chung (`h3chung`)
- Daniel Chen (`dym-chen`)
- Nidhi Elango (`nidhielango`)

Project documentation is available in [`docs/`](./docs/).
