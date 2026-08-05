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
        object ResolvingSpeakers : Phase()
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

        private val SPEAKER_RESOLVE_PROMPT = """
            You are a conversation analyzer. The following are "voice fingerprints" from a meeting, showing sample sentences for each speaker label (Speaker 1, Speaker 2, etc.).
            
            Determine which labels refer to the same person based on speech patterns, vocabulary, and context.
            Provide the result as a simple list of mappings, one per line, like this:
            Speaker 3 -> Speaker 1
            Speaker 4 -> Speaker 2
            
            Only include speakers that should be merged. If a speaker is unique, do not list it.
            Do not include any other text in your response.
        """.trimIndent()

        private val TURN_SPLIT_PROMPT = """
            The following text was attributed to one speaker, but it might contain multiple people speaking. 
            If you detect a change in speaker based on context or conversational flow, split the text and label the parts as "PART A" and "PART B".
            
            Format your response exactly like this:
            [PART A]: text from the first person
            [PART B]: text from the second person
            
            If it's truly just one person, return the original text without labels.
        """.trimIndent()
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

    suspend fun resolveSpeakerMapping(transcript: String): Map<String, String> {
        if (transcript.isBlank()) return emptyMap()

        val model = modelManager.ensureModel().getOrNull() ?: return emptyMap()

        // 1. Generate fingerprints: collect samples for every speaker label found
        val fingerprints = mutableMapOf<String, MutableList<String>>()
        transcript.lines().forEach { line ->
            if (line.contains(":")) {
                val label = line.substringBefore(":").trim()
                val text = line.substringAfter(":").trim()
                if (label.startsWith("Speaker") && text.length > 10) {
                    val list = fingerprints.getOrPut(label) { mutableListOf() }
                    if (list.size < 3) list.add(text)
                }
            }
        }

        if (fingerprints.isEmpty()) return emptyMap()

        val fingerprintText = fingerprints.entries.joinToString("\n") { (label, samples) ->
            "[$label]: \"${samples.joinToString(" ") { it.take(150) }}\""
        }

        return try {
            withContext(Dispatchers.Default) {
                _phase.value = Phase.LoadingModel
                val contextPtr = llamaEngine.initContext(model.absolutePath)
                if (contextPtr == 0L) return@withContext emptyMap<String, String>()
                try {
                    _phase.value = Phase.ResolvingSpeakers
                    val output = llamaEngine.generate(contextPtr, SPEAKER_RESOLVE_PROMPT, "Fingerprints:\n\n$fingerprintText", 256)?.trim()
                    
                    if (output.isNullOrBlank()) return@withContext emptyMap<String, String>()
                    
                    Log.d(TAG, "Speaker mapping output: $output")
                    
                    val mapping = mutableMapOf<String, String>()
                    output.lines().forEach { line ->
                        if (line.contains("->")) {
                            val parts = line.split("->").map { it.trim() }
                            if (parts.size == 2 && parts[0].startsWith("Speaker") && parts[1].startsWith("Speaker")) {
                                mapping[parts[0]] = parts[1]
                            }
                        }
                    }
                    mapping
                } finally {
                    llamaEngine.freeContext(contextPtr)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving speakers", e)
            emptyMap()
        } finally {
            _phase.value = Phase.Idle
        }
    }

    suspend fun splitMergedTurns(text: String): List<String> {
        if (text.length < 300) return listOf(text) // Too short to be worth splitting

        val model = modelManager.ensureModel().getOrNull() ?: return listOf(text)

        return try {
            withContext(Dispatchers.Default) {
                _phase.value = Phase.LoadingModel
                val contextPtr = llamaEngine.initContext(model.absolutePath)
                if (contextPtr == 0L) return@withContext listOf(text)
                try {
                    _phase.value = Phase.Generating
                    val output = llamaEngine.generate(contextPtr, TURN_SPLIT_PROMPT, "Text:\n\n$text", 512)?.trim()
                    
                    if (output.isNullOrBlank() || !output.contains("[PART A]")) return@withContext listOf(text)
                    
                    val parts = mutableListOf<String>()
                    if (output.contains("[PART A]:")) parts.add(output.substringAfter("[PART A]:").substringBefore("[PART B]:").trim())
                    if (output.contains("[PART B]:")) parts.add(output.substringAfter("[PART B]:").trim())
                    
                    if (parts.isEmpty()) listOf(text) else parts
                } finally {
                    llamaEngine.freeContext(contextPtr)
                }
            }
        } catch (e: Exception) {
            listOf(text)
        } finally {
            _phase.value = Phase.Idle
        }
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
