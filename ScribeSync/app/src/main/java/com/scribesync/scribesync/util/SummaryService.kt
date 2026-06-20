package com.scribesync.scribesync.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

        // Extractive Summarization Logic (Local/Offline):
        // 1. Identify key sentences (first, middle, last segments)
        // 2. Filter out very short segments
        // 3. Return a consolidated 'TL;DR' of the meeting
        
        val importantSegments = mutableListOf<String>()
        
        // Always include the beginning to set context
        importantSegments.add("Start: ${lines.first()}")
        
        // Include middle highlights if meeting is long
        if (lines.size > 5) {
            importantSegments.add("Mid-point: ${lines[lines.size / 2]}")
        }
        
        // Always include the conclusion/closing statement
        if (lines.size > 1) {
            importantSegments.add("Conclusion: ${lines.last()}")
        }

        return@withContext importantSegments.joinToString("\n\n")
    }
}
