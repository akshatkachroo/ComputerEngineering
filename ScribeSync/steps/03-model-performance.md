# Transcription is too slow

## Current setup

- Model: `app/src/main/assets/models/ggml-base-q8_0.bin` — Whisper **base**, q8_0 quantized
  (~82 MB asset)
- Inference: CPU only. `CMakeLists.txt` builds with `add_definitions(-DGGML_USE_CPU)` — no
  GPU/NNAPI/Vulkan backend, no `-DGGML_USE_LLAMAFILE` acceleration beyond what that flag already
  enables (it's on)
- `whisper_jni.cpp`: `n_threads = 4`, `WHISPER_SAMPLING_GREEDY` with `best_of = 1` — already the
  fast end of decoding options
- Every ~7s, a full 8s audio window is run through `whisper_full` from scratch (see
  `01-sentence-bleeding.md`) — no incremental/cached computation between windows

So the model itself (base) is the single biggest lever left, since the decode parameters are
already tuned for speed.

## Fix options

1. **Swap to a smaller model first — cheapest change, biggest win.** Try `ggml-tiny.en-q5_1.bin`
   or `ggml-tiny-q8_0.bin` (English-only `tiny.en` if the team doesn't need multilingual — it's
   both smaller and more accurate than multilingual `tiny`). Tiny is roughly 4-6x fewer
   parameters than base; expect a real latency drop at some accuracy cost. This just means
   dropping a new file into `assets/models/` and updating the `modelName` constant in
   `MeetingViewModel.startMeeting()` (`"ggml-base-q8_0.bin"`). Cheap enough to A/B directly on a
   test device.
2. **Try a lower quantization of the current model** (q4_0/q4_1 of base) if tiny's accuracy loss
   is unacceptable — smaller/faster than q8_0 but the size/accuracy trade curve is model-specific,
   worth benchmarking both directions (smaller model vs. more aggressive quant of same model).
3. **Shrink the window** in `MeetingViewModel` once whichever model is chosen — tiny models don't
   need an 8s context to be accurate, so a smaller window (e.g. 4-5s) reduces per-call latency
   and end-to-end lag, independent of the model swap.
4. **Confirm the native build is actually optimized.** `CMakeLists.txt` doesn't explicitly set a
   CMake build type or `-O3`/NEON dot-product flags beyond the arch-specific quant/repack source
   files already included — verify Gradle is invoking the CMake external build in `Release` (not
   the default unspecified type, which can mean no optimization) for release APKs.
5. **Longer-term: GPU or NNAPI backend.** whisper.cpp/ggml support alternate backends
   (Vulkan, OpenCL) that this build doesn't compile in (`GGML_USE_CPU` only). Real perf upside but
   a much bigger lift (new CMake target, backend-specific code paths) — treat as a stretch goal,
   not the first thing to try.

Recommended order: (1) model swap, benchmark on a real device → (3) re-tune window size for the
new model → (2) if accuracy from tiny isn't good enough, try intermediate quant levels → (4) as a
sanity check regardless → (5) only if there's time left.
