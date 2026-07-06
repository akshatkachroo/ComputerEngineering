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
    for (entry in entries) {
        val currentGroup = groups.lastOrNull()
        if (currentGroup != null && currentGroup.last().speakerLabel == entry.speakerLabel) {
            currentGroup.add(entry)
        } else {
            groups.add(mutableListOf(entry))
        }
    }
    return groups
}
