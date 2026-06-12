package com.scribsync.scribsync.data

import java.util.Date
import java.util.UUID

data class Meeting(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val date: Date = Date(),
    val durationSeconds: Int = 0,
    val transcriptEntries: List<TranscriptEntry> = emptyList()
) {
    val transcriptPreview: String
        get() = transcriptEntries.firstOrNull()?.text?.take(120) ?: ""

    val speakerCount: Int
        get() = transcriptEntries.map { it.speakerLabel }.distinct().size
}

data class TranscriptEntry(
    val speakerLabel: String,
    val text: String,
    val timestampMs: Long = 0L
)
