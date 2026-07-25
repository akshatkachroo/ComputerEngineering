package com.scribesync.scribesync.data

import androidx.room.Embedded

data class MeetingSearchResult(
    @Embedded val meeting: Meeting,
    val matchedTranscriptText: String? = null
)
