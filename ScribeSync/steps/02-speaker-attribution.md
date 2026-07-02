# "One person shows as multiple people"

## Root cause

There is no diarization in the app at all today — it's not misfiring, it doesn't exist yet:

- `whisper_jni.cpp` hardcodes the speaker id on every segment: `(jint)1` (see comment
  `// Default to Speaker 1 for now (diarization is a future feature)`)
- `MeetingViewModel.processSegments()` hardcodes `speakerLabel = "Speaker 1"` on every
  `TranscriptEntry` it saves
- `WhisperEngine.Segment.speakerId` exists as a field but nothing ever sets it to anything but
  the default

What teammates are actually seeing as "speaker changes" is a **UI artifact**: `RecordingScreen`'s
`TranscriptEntryItem` and `MeetingDetailScreen`'s equivalent both render **one bubble per
`TranscriptEntry` row**, and a new row is created for every ~7s transcription window (see
`01-sentence-bleeding.md`). A single person talking continuously for a minute produces ~8
separate rows/bubbles, which reads as "this looks like several different turns." So the "maybe
just splits on silence" hunch is right, but it's a rendering/segmentation issue, not a
diarization-accuracy bug — there's no diarization to be inaccurate yet.

## Fix options

**Cheap fix (do this first): merge adjacent same-speaker entries in the UI.** In
`TranscriptEntryItem` (or the list-building code above it in `RecordingScreen.kt` /
`MeetingDetailScreen.kt`), group consecutive `TranscriptEntry` rows that share `speakerLabel`
into a single visual block instead of one bubble per row. This alone will remove most of the
"looks like multiple people" impression even before real diarization exists, since right now
every row is trivially "Speaker 1".

**Real fix: implement actual diarization**, roughly in order of effort:
1. Cheapest heuristic: split speaker turns on long silence gaps (> ~1.5s) *combined* with a
   pitch/energy profile change, no ML — crude but better than nothing, and reuses the RMS
   calculation already in `MeetingViewModel`.
2. On-device speaker embedding model (e.g. a small speaker-verification TFLite/ONNX model) run
   per segment, clustered online (cosine distance threshold) to assign a stable speaker id —
   wire the result into the already-unused `WhisperEngine.Segment.speakerId` field and thread it
   through to `TranscriptEntry.speakerLabel` instead of the hardcoded string.
3. Full diarization pipeline (VAD + embedding + clustering, e.g. a pyannote-style model ported to
   mobile) — significant scope, likely a stretch goal rather than a near-term fix.

Do the UI merge immediately regardless of which diarization option (if any) gets picked — it's
low-risk and directly addresses the reported symptom.
