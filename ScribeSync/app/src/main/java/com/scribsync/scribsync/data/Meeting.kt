package com.scribsync.scribsync.data

import java.util.Date
import java.util.UUID

data class Meeting(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val date: Date = Date(),
    val durationSeconds: Int = 0,
    val transcriptPreview: String = ""
)

data class TranscriptEntry(
    val speakerLabel: String,
    val text: String,
    val timestampMs: Long = 0L
)
