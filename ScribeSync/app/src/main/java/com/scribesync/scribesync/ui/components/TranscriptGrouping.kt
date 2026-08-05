package com.scribesync.scribesync.ui.components

import com.scribesync.scribesync.data.TranscriptEntry

/**
 * Groups consecutive entries that share the same speaker into one block, so
 * a single person talking across several short phrases renders as one
 * visual turn instead of a new bubble per transcribed chunk.
 */
fun groupConsecutiveBySpeaker(entries: List<TranscriptEntry>): List<List<TranscriptEntry>> {
    if (entries.isEmpty()) return emptyList()

    val groups = mutableListOf<MutableList<TranscriptEntry>>()
    val MAX_GAP_MS = 10000L
    for (entry in entries) {
        val lastGroup = groups.lastOrNull()
        val lastEntry = lastGroup?.lastOrNull()

        val sameSpeaker = lastEntry != null && lastEntry.speakerLabel == entry.speakerLabel
        val smallGap = lastEntry != null && (entry.timestampMs - lastEntry.timestampMs) < MAX_GAP_MS

        if (sameSpeaker && smallGap) {
            lastGroup!!.add(entry)
        } else {
            groups.add(mutableListOf(entry))
        }
    }
    return groups
}
