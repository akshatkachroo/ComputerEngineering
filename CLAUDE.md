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

An Android meeting recorder with accounts: captures microphone audio via a foreground service, transcribes it on-device in real time with a bundled Whisper.cpp model, and summarizes it on-device via a locally-run LLM (llama.cpp). Meetings/transcripts are stored locally in Room and optionally synced to Firebase Firestore when online. Sign-in is required (Firebase Auth, email/password) before reaching the app; signed-in users can add contacts and invite them to meetings as attendees, and browse meetings from a Calendar tab. `ScribeSync/README.md` predates most of this (auth, contacts, calendar, LLM summarization) — treat its "Feature Roadmap" and "Project Structure" sections as stale rather than authoritative.

Known limitation: no real speaker diarization. `WhisperEngine`/`whisper_jni.cpp` has tinydiarize (tdrz) turn-detection plumbing fully wired up but disabled (`params.tdrz_enable = false`) — no `base`/`tiny` tdrz model exists (only an official `small.en` fine-tune at ~487MB and an unverified community quantization), so every entry is still labeled "Speaker 1". Don't re-suggest a smaller tdrz model if this comes up again.

## Build & run

Open the `ScribeSync/` directory in Android Studio (Hedgehog+, JDK 17, SDK 35, NDK/CMake for the native module) and run from there — this is the normal workflow since it drives Gradle sync, the emulator/device picker, and logcat.

Command-line build from `ScribeSync/`:
```
./gradlew assembleDebug        # macOS/Linux
gradlew.bat assembleDebug      # Windows
```
Output: `app/build/outputs/apk/debug/app-debug.apk`.

There are no unit or instrumented tests in the app source yet (only the default JUnit/Espresso dependencies are wired up in `app/build.gradle.kts`).

**Firebase caveat:** the Google Services Gradle plugin is applied unconditionally, so building requires a `google-services.json` in `ScribeSync/app/` registered for application ID `com.scribesync.scribesync`, with Firebase Auth (email/password) and Firestore enabled — the app now gates the whole UI behind sign-in (`MainActivity`), so it's no longer optional the way pure recording/transcription is. Never commit that file or other Firebase credentials.

**Model assets:** `app/src/main/assets/models/` bundles two Whisper models — `ggml-tiny.en-q8_0.bin` (43.5MB) is the one actually loaded (`MeetingViewModel`); `ggml-base-q8_0.bin` (82MB) is a leftover from before the model swap and is unused. Together they still make clones/syncs slow. Summarization uses a third model, Qwen2.5-1.5B-Instruct (GGUF, Q4_K_M, ~1.1GB) — this one is **not** bundled; `SummaryModelManager` downloads and SHA-256-verifies it into app-internal storage on first use (resumable via HTTP Range), so the first summary on a fresh install needs one-time internet access.

## Architecture

Single-Activity Compose app, manual DI via an `Application` subclass (no Hilt/Koin). `MainActivity` gates the whole UI: while `AuthViewModel.currentUser` is null it shows `LoginScreen`, otherwise `NavGraph`.

- **`ScribeSyncApplication`** (`ScribeSyncApplication.kt`) is the composition root: lazily builds the Room `AppDatabase`, `FirebaseFirestore` and `FirebaseAuth` (both nullable — fall back to `null` if Firebase isn't initialized), `TranscriptRepository`, `AuthRepository`, `ContactRepository`, `AttendeeRequestRepository`, `WhisperEngine`, `LlamaEngine`, `LocationHelper`, `NetworkObserver`, `SummaryModelManager`, `SummaryService`, and a shared `MutableSharedFlow<FloatArray>` (`audioDataFlow`) that audio data is broadcast through app-wide.
- **`MeetingViewModel`** (`ui/viewmodel/`) is the single source of truth for recording state and owns the whole recording pipeline: it starts `AudioCaptureService`, drains its audio flow through a `Channel`, and does silence-based phrase chunking (200ms analysis slices, cuts a chunk after 500ms of silence, 25s safety cap — replaced an earlier fixed 8s/7s sliding-window approach that bled sentences across chunk boundaries). Chunks feed `WhisperEngine` under a `Mutex` (native context is single-threaded), filtering known Whisper hallucination strings (e.g. "music", "blank_audio"). Speaker numbering increments only when the (currently disabled) tdrz turn flag fires, so in practice everything lands under "Speaker 1" — see the diarization note above. On stop it drains the remainder, frees the native context, generates a summary via `SummaryService`, and triggers Firestore sync. Read this file first when touching anything about the recording/transcription flow — the ordering of cancel → drain → join → free-context in `stopMeeting()` is deliberate and avoids native use-after-free.
- **`AudioCaptureService`** (`service/`) is a foreground `Service` (mic type) that owns the `AudioRecord`, reads 16kHz mono PCM, converts to normalized `FloatArray`, and emits both to its own flow and directly into `ScribeSyncApplication.audioDataFlow`.
- **`WhisperEngine`** (`engine/WhisperEngine.kt`) is a thin JNI bridge (`external fun`) into the native library `scribesync`, built from `app/src/main/cpp/whisper_jni.cpp` against the vendored `whisper.cpp` sources. Context init/transcribe/free are pointer-passing calls (`Long` as native pointer) — the Kotlin side is responsible for the lock and lifecycle discipline described above.
- **`LlamaEngine`** (`engine/LlamaEngine.kt`) mirrors that pattern as a JNI bridge into a *separate* native library, `summarizer`, built from `app/src/main/cpp/llama_jni.cpp` against vendored llama.cpp sources. `app/src/main/cpp/CMakeLists.txt` builds `scribesync` (whisper) and `summarizer` (llama) as two independent shared libraries because each bundles its own copy of ggml — llama's ggml is statically linked into `summarizer` with its symbols hidden (`--exclude-libs,ALL`) so the two never collide at runtime. `SummaryService` (`util/`) drives inference: it calls `SummaryModelManager` to ensure the Qwen model is downloaded (see Model assets above), then runs `LlamaEngine.generate()` off the main thread with a fixed system prompt (overview / key points / decisions / action items) and a head+tail transcript clip to fit the 4096-token context window. Failures are surfaced explicitly (`SummaryResult.Failure`, or a `[summary-failed]`-prefixed `Meeting.summary`) rather than silently degraded to a templated summary.
- **Persistence** (`data/`): Room entities `Meeting`, `TranscriptEntry` (FK cascade-delete on meeting), `UserProfile`, and `Contact`, at `AppDatabase` version 8. `Meeting` now also carries `tags`, `ownerId`/`ownerName`, and `attendeeIds` (contact ids invited post-recording). Access goes through `MeetingDao`/`ContactDao`/`UserProfileDao`, wrapped by `TranscriptRepository` (meetings/transcripts), `ContactRepository`, and `AuthRepository` (also owns the Firestore `users` collection and a local `UserProfile` cache). `AttendeeRequestRepository` is Firestore-only (no local table) — it writes invite docs under `meetings/{id}/attendeeRequests` and exposes a live `collectionGroup` listener (`getPendingRequestsForUser`) so an invitee's device learns about requests created on someone else's device. Sync methods (`syncMeetingsToCloud`/`syncTranscriptsToCloud`/`syncContactsToCloud`) push only rows where `isSynced == false`. `AppDatabase` uses `fallbackToDestructiveMigration()` — schema changes during development wipe local data instead of requiring a migration.
- **Navigation** (`navigation/NavGraph.kt`): a bottom nav bar (Meetings/Calendar/Contacts/Settings, with a pending-invite badge on Contacts) wraps `home`, `calendar`, `contacts`, `settings`, plus two pushed routes off the Meetings tab — `recording/{title}` (title URL-encoded) and `meeting_detail/{meetingId}`. All routes share the same `MeetingViewModel` and `AuthViewModel` instances (each created once via its `Factory` off the `Application`).
- **`ui/theme/`**: dark navy/teal Material3 theme; `ui/components/` has reusable `SyncStatusBadge` and `OwnerBadge`, plus `TranscriptGrouping` (merges consecutive same-speaker `TranscriptEntry` rows into one visual block, used by both `RecordingScreen` and `MeetingDetailScreen`).
- **`util/`**: `LocationHelper` (one-shot last-known-location fetch, retried during recording start), `NetworkObserver` (drives the auto-sync-when-online behavior across ViewModels and repositories), `SummaryModelManager` and `SummaryService` (see above).

## Branches

`main` is the actively developed branch with the full pipeline described above. Other branches in this repo (e.g. `recording-ui`) may be stale forks from early in the project (mock-only UI, no transcription/persistence) — diff against `main` before assuming a branch reflects current app state.
