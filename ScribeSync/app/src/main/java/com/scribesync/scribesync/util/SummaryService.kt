package com.scribesync.scribesync.util

import android.util.Log
import com.scribesync.scribesync.engine.LlamaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * SummaryService - fully on-device meeting summarization via a local LLM (llama.cpp).
 *
 * Privacy contract: no meeting content (transcript, audio, or summary) ever leaves the
 * device. The only network activity in this path is the one-time download of public
 * model weights, handled by [SummaryModelManager].
 *
 * Failure is never masked with a templated/heuristic summary: callers receive a
 * [SummaryResult.Failure] with the reason, which the UI renders as a distinct error state.
 */
class SummaryService(
    private val modelManager: SummaryModelManager,
    private val llamaEngine: LlamaEngine
) {

    sealed class SummaryResult {
        data class Success(val text: String) : SummaryResult()
        data class Failure(val reason: String) : SummaryResult()
        /** Transcript was empty or near-empty; no inference was run. */
        object EmptyTranscript : SummaryResult()
    }

    /** Compute-side progress; download progress is on [modelDownloadState]. */
    sealed class Phase {
        object Idle : Phase()
        object LoadingModel : Phase()
        object Generating : Phase()
    }

    companion object {
        private const val TAG = "SummaryService"

        /**
         * Prefix stored in Meeting.summary when generation failed, so the detail screen can
         * render a visible error state. Kept as an in-band marker because adding a status
         * column would trigger Room's destructive migration and wipe local data.
         */
        const val FAILED_PREFIX = "[summary-failed]"

        private const val MAX_SUMMARY_TOKENS = 512

        // The on-device context window is 4096 tokens; clip very long transcripts,
        // keeping the head and tail where meetings concentrate agenda and action items.
        private const val MAX_TRANSCRIPT_CHARS = 9000
        private const val CLIP_HEAD_CHARS = 5500
        private const val CLIP_TAIL_CHARS = 3400

        private const val SYSTEM_PROMPT = """You summarize meeting transcripts produced by on-device speech recognition (they may contain recognition errors and odd punctuation).

Write a summary with this structure, in plain text:
- A 1-2 sentence overview of what the meeting was about.
- "Key points:" followed by a bulleted list of topics discussed.
- "Decisions:" followed by decisions made. Omit this section if there were none.
- "Action items:" followed by tasks, each with an owner when one is identifiable. Omit this section if there were none.

Never reproduce the transcript verbatim - condense it into new wording.
If the transcript is trivial (a microphone test, counting, a handful of throwaway sentences), respond with a single honest sentence such as "Short test recording; no substantive content." instead of padding it into a fake summary."""
    }

    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    /** Download progress of the one-time model fetch, for the UI. */
    val modelDownloadState: StateFlow<SummaryModelManager.DownloadState> = modelManager.state

    /**
     * Opportunistically downloads the model ahead of the first summary (e.g. when a
     * recording starts) so it is usually ready by the time the meeting ends. Failures
     * are swallowed; generateSummary() retries and reports honestly.
     */
    suspend fun prefetchModel() {
        if (!modelManager.isModelReady()) {
            modelManager.ensureModel()
        }
    }

    suspend fun generateSummary(transcript: String): SummaryResult {
        val wordCount = transcript.split(Regex("\\s+")).count { it.isNotBlank() }
        if (transcript.isBlank() || wordCount < 3) {
            Log.i(TAG, "Transcript empty or near-empty ($wordCount words); skipping inference")
            return SummaryResult.EmptyTranscript
        }

        // One-time download on first use; if the device is offline and the model is
        // absent, this fails honestly instead of faking a summary.
        val model = modelManager.ensureModel().getOrElse { e ->
            return SummaryResult.Failure(
                "Summary model not available: ${e.message ?: "download failed"}. " +
                    "Connect to the internet once to download it (~1.1 GB)."
            )
        }

        return try {
            withContext(Dispatchers.Default) {
                _phase.value = Phase.LoadingModel
                Log.i(TAG, "Loading summary model (${model.length()} bytes)")
                val contextPtr = llamaEngine.initContext(model.absolutePath)
                if (contextPtr == 0L) {
                    Log.e(TAG, "Native model load failed")
                    return@withContext SummaryResult.Failure("Failed to load the on-device summary model")
                }
                try {
                    _phase.value = Phase.Generating
                    val clipped = clipTranscript(transcript)
                    Log.i(TAG, "Generating summary on-device ($wordCount words, ${clipped.length} chars after clipping)")
                    val startMs = System.currentTimeMillis()
                    val output = llamaEngine.generate(
                        contextPtr,
                        SYSTEM_PROMPT,
                        "Summarize this meeting transcript:\n\n$clipped",
                        MAX_SUMMARY_TOKENS
                    )?.trim()
                    Log.i(TAG, "Inference finished in ${System.currentTimeMillis() - startMs} ms")

                    if (output.isNullOrBlank()) {
                        Log.e(TAG, "Model produced no output")
                        SummaryResult.Failure("The on-device model produced no output")
                    } else {
                        SummaryResult.Success(output)
                    }
                } finally {
                    llamaEngine.freeContext(contextPtr)
                }
            }
        } catch (e: Throwable) {
            // Throwable: a 1.5B model on a low-RAM device can throw OutOfMemoryError.
            Log.e(TAG, "On-device summary generation failed", e)
            SummaryResult.Failure(e.message ?: e.javaClass.simpleName)
        } finally {
            _phase.value = Phase.Idle
        }
    }

    private fun clipTranscript(transcript: String): String =
        if (transcript.length <= MAX_TRANSCRIPT_CHARS) {
            transcript
        } else {
            transcript.take(CLIP_HEAD_CHARS) +
                "\n[... middle of transcript omitted for length ...]\n" +
                transcript.takeLast(CLIP_TAIL_CHARS)
        }
}
