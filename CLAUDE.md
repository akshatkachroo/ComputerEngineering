# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

This repo contains one Android app plus course documentation:

```
.
├── ScribeSync/          # Android Studio project (open THIS directory in Android Studio, not the repo root)
└── docs/                # Team contract and weekly meeting notes for the course project
```

`docs/` is coursework bookkeeping (meeting minutes, team contract) — not application source. Don't treat it as project config.

## What ScribeSync is

An Android meeting recorder: captures microphone audio via a foreground service, transcribes it on-device in real time with a bundled Whisper.cpp model, stores meetings/transcripts locally in Room, and optionally syncs to Firebase Firestore when online. See `ScribeSync/README.md` for the full feature list and current limitations (no real diarization yet — speakers are all labeled "Speaker 1"; summaries are naive extractive text, not LLM-based; no export/search yet).

## Build & run

Open the `ScribeSync/` directory in Android Studio (Hedgehog+, JDK 17, SDK 35, NDK/CMake for the native module) and run from there — this is the normal workflow since it drives Gradle sync, the emulator/device picker, and logcat.

Command-line build from `ScribeSync/`:
```
./gradlew assembleDebug        # macOS/Linux
gradlew.bat assembleDebug      # Windows
```
Output: `app/build/outputs/apk/debug/app-debug.apk`.

There are no unit or instrumented tests in the app source yet (only the default JUnit/Espresso dependencies are wired up in `app/build.gradle.kts`).

**Firebase caveat:** the Google Services Gradle plugin is applied unconditionally, so building requires a `google-services.json` in `ScribeSync/app/` registered for application ID `com.scribesync.scribesync`, even though recording/transcription work fully offline. Never commit that file or other Firebase credentials.

**Model asset:** `app/src/main/assets/models/ggml-base-q8_0.bin` is an ~82 MB bundled Whisper model — expect slower clones/syncs.

## Architecture

Single-Activity Compose app, manual DI via an `Application` subclass (no Hilt/Koin).

- **`ScribeSyncApplication`** (`ScribeSyncApplication.kt`) is the composition root: lazily builds the Room `AppDatabase`, `FirebaseFirestore` (nullable — falls back to `null` if Firebase isn't initialized), `TranscriptRepository`, `WhisperEngine`, `LocationHelper`, `NetworkObserver`, `SummaryService`, and a shared `MutableSharedFlow<FloatArray>` (`audioDataFlow`) that audio data is broadcast through app-wide.
- **`MeetingViewModel`** (`ui/viewmodel/`) is the single source of truth for recording state and owns the whole recording pipeline: it starts `AudioCaptureService`, drains its audio flow through a `Channel`, does simple RMS-based VAD over an 8s sliding window (7s step, 1s overlap) to skip silence, feeds windows to `WhisperEngine` under a `Mutex` (native context is single-threaded), filters known Whisper hallucination strings (e.g. "music", "blank_audio"), appends resulting `TranscriptEntry`s, and on stop drains the remainder, frees the native context, generates a summary, and triggers Firestore sync. Read this file first when touching anything about the recording/transcription flow — the ordering of cancel → drain → join → free-context in `stopMeeting()` is deliberate and avoids native use-after-free.
- **`AudioCaptureService`** (`service/`) is a foreground `Service` (mic type) that owns the `AudioRecord`, reads 16kHz mono PCM, converts to normalized `FloatArray`, and emits both to its own flow and directly into `ScribeSyncApplication.audioDataFlow`.
- **`WhisperEngine`** (`engine/WhisperEngine.kt`) is a thin JNI bridge (`external fun`) into the native library `scribesync`, built from `app/src/main/cpp/whisper_jni.cpp` against the vendored `whisper.cpp` sources via `app/src/main/cpp/CMakeLists.txt`. Context init/transcribe/free are pointer-passing calls (`Long` as native pointer) — the Kotlin side is responsible for the lock and lifecycle discipline described above.
- **Persistence** (`data/`): Room entities `Meeting` and `TranscriptEntry` (FK cascade-delete on meeting), accessed through `MeetingDao` and wrapped by `TranscriptRepository`, which also owns Firestore sync (`syncMeetingsToCloud`/`syncTranscriptsToCloud`, pushing only rows where `isSynced == false`). `AppDatabase` uses `fallbackToDestructiveMigration()` — schema changes during development wipe local data instead of requiring a migration.
- **Navigation** (`navigation/NavGraph.kt`): three Compose routes — `home`, `recording/{title}` (title URL-encoded), `meeting_detail/{meetingId}` — all backed by the same shared `MeetingViewModel` instance (created once via `MeetingViewModel.Factory` off the `Application`).
- **`util/`**: `LocationHelper` (one-shot last-known-location fetch, retried during recording start), `NetworkObserver` (drives the auto-sync-when-online behavior in both the ViewModel and repository), `SummaryService` (non-LLM extractive summary).

## Branches

`main` is the actively developed branch with the full pipeline described above. Other branches in this repo (e.g. `recording-ui`) may be stale forks from early in the project (mock-only UI, no transcription/persistence) — diff against `main` before assuming a branch reflects current app state.
