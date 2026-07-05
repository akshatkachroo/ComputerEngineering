package com.scribesync.scribesync.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import java.util.regex.Pattern

/**
 * SummaryService - Implements on-device summarization logic.
 * Following the "Thick-Client" paradigm where 100% of AI processing is local.
 */
class SummaryService {

    suspend fun generateSummary(transcript: String): String? = withContext(Dispatchers.Default) {
        if (transcript.isBlank()) return@withContext null

        val lines = transcript.split("\n")
            .filter { it.contains(":") }
            .map { it.substringAfter(":").trim() }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) return@withContext "No content to summarize."

        val importantSegments = mutableListOf<String>()
        
        importantSegments.add("Start: ${lines.first()}")
        
        if (lines.size > 5) {
            importantSegments.add("Mid-point: ${lines[lines.size / 2]}")
        }
        
        if (lines.size > 1) {
            importantSegments.add("Conclusion: ${lines.last()}")
        }

        return@withContext importantSegments.joinToString("\n\n")
    }

    data class ExtractedTask(val text: String, val dueDate: Date?)

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
        
        // Relative dates
        if (text.contains("by tomorrow")) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            return calendar.time
        }
        
        if (text.contains("by friday")) {
            return getNextDayOfWeek(Calendar.FRIDAY)
        }
        if (text.contains("by monday")) {
            return getNextDayOfWeek(Calendar.MONDAY)
        }
        if (text.contains("by tuesday")) {
            return getNextDayOfWeek(Calendar.TUESDAY)
        }
        if (text.contains("by wednesday")) {
            return getNextDayOfWeek(Calendar.WEDNESDAY)
        }
        if (text.contains("by thursday")) {
            return getNextDayOfWeek(Calendar.THURSDAY)
        }
        if (text.contains("by saturday")) {
            return getNextDayOfWeek(Calendar.SATURDAY)
        }
        if (text.contains("by sunday")) {
            return getNextDayOfWeek(Calendar.SUNDAY)
        }
        
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
}
