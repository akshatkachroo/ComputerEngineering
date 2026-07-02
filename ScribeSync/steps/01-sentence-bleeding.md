# Sentence bleeding across window boundaries

## Root cause

`MeetingViewModel.startMeeting()` (`ui/viewmodel/MeetingViewModel.kt`) transcribes with a
fixed-size sliding window regardless of where speech actually pauses:

- `windowSize = 16000 * 8` (8s), `stepSize = 16000 * 7` (7s) → only 1s of overlap
- Each window is sent to `whisper_full` as an **independent** inference call (`whisper_jni.cpp:
  Java_..._transcribeSegments`); the only continuity between calls is the `initial_prompt` text
  (last 200 chars of transcript so far), which nudges decoding but does not stitch tokens
- Dedup in `processSegments()` only drops an entry if its trimmed text is an **exact** match to
  one of the last 5 entries — a sentence split mid-word by the window cut produces two different
  strings, so neither half gets filtered, and the same audio in the 1s overlap can get
  transcribed twice with slightly different wording (also not an exact match, so it survives)

So: any sentence that happens to straddle the 7s window boundary gets cut in half or repeated
with variation.

## Fix options (cheapest → most involved)

1. **Cut on silence, not on a fixed clock.** Use the RMS/VAD calculation that already exists in
   the same loop to find a quiet point near the target step size and cut there instead of always
   at exactly 7s. Whisper.cpp's own `examples/stream/stream.cpp` does this — worth diffing our
   loop against it since our JNI layer already vendors that source tree.
2. **Use segment timestamps to trim the overlap deterministically**, rather than relying on text
   dedup. `whisper_full_get_segment_t0/t1` (already read in `whisper_jni.cpp`) give per-segment
   times relative to the window. On each call, discard/merge any segment whose `t0` falls inside
   the previous window's overlap region instead of comparing rendered strings.
3. **Carry partial audio instead of discarding it.** Right now `stepSize` samples are dropped
   from the front of `audioBuffer` unconditionally. If the last segment in a window doesn't end
   cleanly (e.g. `t1` is close to the window edge), keep that trailing audio in the buffer for
   the next window instead of only advancing by a fixed step.
4. **Increase overlap** (e.g. 8s window / 5s step) as a stopgap while a real fix is built — cheap
   to test, doesn't fix duplication but reduces how often a sentence is exactly bisected.

Start with (1) or (2); they attack the actual cause. (4) is a five-minute experiment worth doing
first to confirm the diagnosis.
