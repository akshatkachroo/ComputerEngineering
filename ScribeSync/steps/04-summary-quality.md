# Summary quality

## Root cause

`SummaryService.generateSummary()` (`util/SummaryService.kt`) is not really a summarizer — it
takes whatever lines contain a `:` (i.e. `"Speaker 1: ..."` lines from the transcript), and
returns exactly three of them verbatim: the first line, the middle line, and the last line,
labeled "Start" / "Mid-point" / "Conclusion". There's no scoring, no compression, no
paraphrasing — it's a fixed 3-line excerpt, so quality is entirely at the mercy of whichever
three lines happen to land at those positions (often mid-sentence fragments given the
windowing issue in `01-sentence-bleeding.md`).

## Fix options

1. **Cheapest real improvement, still fully offline: proper extractive scoring.** Replace the
   first/middle/last picks with a scored selection — e.g. a small TextRank/TF-IDF pass over the
   transcript lines (sentence similarity graph, pick top-N by centrality) rather than positional
   picks. No ML dependency, no new native code, just better algorithm in the same Kotlin file.
   Also worth deduping near-identical lines before scoring, since bleeding (`01-`) currently
   produces near-duplicate fragments that would otherwise get double-counted.
2. **Structure the output better** even before changing the selection algorithm — e.g. bullet
   points / action items instead of "Start / Mid-point / Conclusion" labels, which read as
   arbitrary rather than meaningful sections.
3. **On-device abstractive summarization via a small LLM.** Bigger lift: bundle a small
   instruct/summarization model (GGUF via llama.cpp, or a TFLite/ONNX distilled summarizer) and
   add a JNI bridge analogous to `WhisperEngine`/`whisper_jni.cpp`. Real quality jump but doubles
   the native-model surface area and app size — only worth it if (1) still isn't good enough.
4. **Cloud LLM summarization, online-only.** Reuses the existing "optional, online-only" pattern
   already established for Firestore sync (`NetworkObserver` + `TranscriptRepository`'s
   sync-when-available flow) — call an LLM API only when online, store the result back locally
   like a synced field. Fastest way to get high-quality summaries, but it's a positioning change:
   the README currently frames Firestore sync as the only thing that leaves the device, and this
   would mean transcript content leaving the device for summarization too. Flag this trade-off to
   the team explicitly before choosing this path over (3).

Recommended order: (1) first — it's a same-file, no-new-dependency change and will visibly help.
Only pursue (3) or (4) if extractive summarization is still clearly insufficient, and treat that
as a team decision (native model size vs. cloud/privacy trade-off) rather than a solo call.
