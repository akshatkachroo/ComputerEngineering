package com.scribesync.scribesync.util

import android.util.Log
import com.scribesync.scribesync.engine.LlamaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.*

/**
 * SummaryService - fully on-device meeting summarization via a local LLM (llama.cpp).
 */
class SummaryService(
    private val modelManager: SummaryModelManager,
    private val llamaEngine: LlamaEngine
) {

    sealed class SummaryResult {
        data class Success(val text: String) : SummaryResult()
        data class Failure(val reason: String) : SummaryResult()
        object EmptyTranscript : SummaryResult()
    }

    sealed class Phase {
        object Idle : Phase()
        object LoadingModel : Phase()
        object Generating : Phase()
    }

    companion object {
        private const val TAG = "SummaryService"
        const val FAILED_PREFIX = "[summary-failed]"
        private const val MAX_SUMMARY_TOKENS = 512
        private const val MAX_TRANSCRIPT_CHARS = 9000
        private const val CLIP_HEAD_CHARS = 5500
        private const val CLIP_TAIL_CHARS = 3400

        private const val SYSTEM_PROMPT = """You summarize meeting transcripts..."""
    }

    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    data class ExtractedTask(val text: String, val dueDate: Date?)

    val modelDownloadState: StateFlow<SummaryModelManager.DownloadState> = modelManager.state

    suspend fun prefetchModel() {
        if (!modelManager.isModelReady()) {
            modelManager.ensureModel()
        }
    }

    suspend fun extractActionItems(transcript: String): List<ExtractedTask> = withContext(Dispatchers.Default) {
        if (transcript.isBlank()) return@withContext emptyList()

        val triggers = listOf(
            "i will", "we should", "you should", "let's", "to-do", 
            "action item", "follow up", "need to", "must", "can you",
            "could you", "please", "assigned to"
        )

        val sentences = transcript.split(".", "?", "!", "\n")
            .filter { it.contains(":") }
            .map { it.substringAfter(":").trim() }
            .filter { it.isNotBlank() }

        val actionItems = mutableListOf<ExtractedTask>()

        for (sentence in sentences) {
            val lowerSentence = sentence.lowercase()
            if (triggers.any { lowerSentence.contains(it) }) {
                val cleanSentence = sentence.trim().replaceFirstChar { it.uppercase() }
                if (cleanSentence.length > 5 && actionItems.none { it.text == cleanSentence }) {
                    val dueDate = parseDueDate(lowerSentence)
                    actionItems.add(ExtractedTask(cleanSentence, dueDate))
                }
            }
        }
        return@withContext actionItems.take(10)
    }

    private fun parseDueDate(text: String): Date? {
        val calendar = Calendar.getInstance()
        if (text.contains("by tomorrow")) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            return calendar.time
        }
        if (text.contains("by friday")) return getNextDayOfWeek(Calendar.FRIDAY)
        if (text.contains("by monday")) return getNextDayOfWeek(Calendar.MONDAY)
        if (text.contains("by tuesday")) return getNextDayOfWeek(Calendar.TUESDAY)
        if (text.contains("by wednesday")) return getNextDayOfWeek(Calendar.WEDNESDAY)
        if (text.contains("by thursday")) return getNextDayOfWeek(Calendar.THURSDAY)
        if (text.contains("by saturday")) return getNextDayOfWeek(Calendar.SATURDAY)
        if (text.contains("by sunday")) return getNextDayOfWeek(Calendar.SUNDAY)
        
        if (text.contains("by end of the week")) {
            calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek + 6)
            return calendar.time
        }
        return null
    }

    private fun getNextDayOfWeek(dayOfWeek: Int): Date {
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_WEEK)
        var daysUntil = dayOfWeek - currentDay
        if (daysUntil <= 0) daysUntil += 7
        calendar.add(Calendar.DAY_OF_YEAR, daysUntil)
        return calendar.time
    }

    suspend fun generateSummary(transcript: String): SummaryResult {
        val wordCount = transcript.split(Regex("\\s+")).count { it.isNotBlank() }
        if (transcript.isBlank() || wordCount < 3) return SummaryResult.EmptyTranscript

        val model = modelManager.ensureModel().getOrElse { e ->
            return SummaryResult.Failure("Summary model not available: ${e.message}")
        }

        return try {
            withContext(Dispatchers.Default) {
                _phase.value = Phase.LoadingModel
                val contextPtr = llamaEngine.initContext(model.absolutePath)
                if (contextPtr == 0L) return@withContext SummaryResult.Failure("Failed to load model")
                try {
                    _phase.value = Phase.Generating
                    val output = llamaEngine.generate(contextPtr, SYSTEM_PROMPT, "Summarize:\n\n$transcript", MAX_SUMMARY_TOKENS)?.trim()
                    if (output.isNullOrBlank()) SummaryResult.Failure("No output")
                    else SummaryResult.Success(output)
                } finally {
                    llamaEngine.freeContext(contextPtr)
                }
            }
        } catch (e: Throwable) {
            SummaryResult.Failure(e.message ?: "Error")
        } finally {
            _phase.value = Phase.Idle
        }
    }
}
