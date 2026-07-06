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
            .filter { it.length > 8 } // drop filler/very short fragments ("okay", "yeah")
            .distinctBy { it.lowercase() } // transcription can produce near-identical repeats

        if (lines.isEmpty()) return@withContext "No content to summarize."
        if (lines.size <= 3) return@withContext lines.joinToString("\n") { "• $it" }

        // Pick the most representative lines by centrality (TextRank-style) rather
        // than always grabbing the first/middle/last position, which is at the
        // mercy of whatever happens to land at those three spots.
        val topCount = (lines.size / 5).coerceIn(3, 8)
        val topLines = rankByCentrality(lines, topCount)

        return@withContext topLines.joinToString("\n") { "• $it" }
    }

    // Builds a sentence-similarity graph (edges weighted by shared words) and
    // runs PageRank over it - lines that share vocabulary with many other lines
    // are treated as more central to the meeting, similar to TextRank.
    private fun rankByCentrality(lines: List<String>, topCount: Int): List<String> {
        val n = lines.size
        val wordSets = lines.map { tokenize(it) }

        val similarity = Array(n) { DoubleArray(n) }
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val sim = jaccardSimilarity(wordSets[i], wordSets[j])
                similarity[i][j] = sim
                similarity[j][i] = sim
            }
        }
        val rowSums = DoubleArray(n) { i -> similarity[i].sum() }

        var scores = DoubleArray(n) { 1.0 / n }
        val damping = 0.85
        repeat(20) {
            val newScores = DoubleArray(n) { (1 - damping) / n }
            for (i in 0 until n) {
                for (j in 0 until n) {
                    if (i == j || rowSums[j] <= 0.0) continue
                    newScores[i] += damping * (similarity[j][i] / rowSums[j]) * scores[j]
                }
            }
            scores = newScores
        }

        // Rank by score, but present the chosen lines back in their original
        // chronological order so the summary still reads top-to-bottom.
        return lines.indices
            .sortedByDescending { scores[it] }
            .take(topCount)
            .sorted()
            .map { lines[it] }
    }

    private fun tokenize(text: String): Set<String> {
        return text.lowercase()
            .split(Regex("\\W+"))
            .filter { it.length > 2 && it !in STOP_WORDS }
            .toSet()
    }

    private fun jaccardSimilarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.intersect(b).size
        val union = a.union(b).size
        return if (union == 0) 0.0 else intersection.toDouble() / union
    }

    companion object {
        private val STOP_WORDS = setOf(
            "the", "and", "for", "that", "this", "with", "have", "are", "was",
            "were", "but", "not", "you", "your", "they", "them", "then", "than",
            "will", "would", "could", "should", "there", "their", "what", "when",
            "just", "like", "know", "think", "going", "gonna", "yeah", "okay"
        )
    }
}
