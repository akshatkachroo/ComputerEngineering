package com.scribesync.scribesync.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(tableName = "meetings")
data class Meeting(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val date: Date = Date(),
    val durationSeconds: Int = 0,
    val transcriptPreview: String = "",
    val summary: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isSynced: Boolean = false,
    val tags: List<String> = emptyList()
)

@Entity(
    tableName = "transcript_entries",
    foreignKeys = [
        ForeignKey(
            entity = Meeting::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["meetingId"])]
)
data class TranscriptEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meetingId: String,
    val speakerLabel: String,
    val text: String,
    val timestampMs: Long = 0L,
    val isSynced: Boolean = false
)
