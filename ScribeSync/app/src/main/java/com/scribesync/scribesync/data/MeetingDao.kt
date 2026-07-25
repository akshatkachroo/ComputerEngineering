package com.scribesync.scribesync.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {
    @Query("SELECT * FROM meetings ORDER BY date DESC")
    fun getAllMeetings(): Flow<List<Meeting>>

    @Query(
        """
        SELECT meetings.*,
            (
                SELECT transcript_entries.text
                FROM transcript_entries
                WHERE transcript_entries.meetingId = meetings.id
                    AND transcript_entries.text LIKE :pattern ESCAPE '\'
                ORDER BY transcript_entries.timestampMs ASC
                LIMIT 1
            ) AS matchedTranscriptText
        FROM meetings
        WHERE meetings.title LIKE :pattern ESCAPE '\'
            OR meetings.transcriptPreview LIKE :pattern ESCAPE '\'
            OR COALESCE(meetings.summary, '') LIKE :pattern ESCAPE '\'
            OR meetings.ownerName LIKE :pattern ESCAPE '\'
            OR meetings.tags LIKE :pattern ESCAPE '\'
            OR EXISTS (
                SELECT 1
                FROM transcript_entries
                WHERE transcript_entries.meetingId = meetings.id
                    AND transcript_entries.text LIKE :pattern ESCAPE '\'
            )
        ORDER BY meetings.date DESC
        """
    )
    fun searchMeetings(pattern: String): Flow<List<MeetingSearchResult>>

    @Query("SELECT * FROM meetings WHERE id = :id")
    suspend fun getMeetingById(id: String): Meeting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeeting(meeting: Meeting)

    @Update
    suspend fun updateMeeting(meeting: Meeting)

    @Query("DELETE FROM meetings WHERE id = :id")
    suspend fun deleteMeeting(id: String)

    // Transcript Entries
    @Query("SELECT * FROM transcript_entries WHERE meetingId = :meetingId ORDER BY timestampMs ASC")
    fun getTranscriptForMeeting(meetingId: String): Flow<List<TranscriptEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscriptEntry(entry: TranscriptEntry)

    @Query("SELECT * FROM transcript_entries WHERE isSynced = 0")
    suspend fun getUnsyncedEntries(): List<TranscriptEntry>

    @Query("UPDATE transcript_entries SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markTranscriptEntriesAsSynced(ids: List<Long>)
    
    @Query("SELECT * FROM meetings WHERE isSynced = 0")
    suspend fun getUnsyncedMeetings(): List<Meeting>

    @Query("UPDATE meetings SET isSynced = 1 WHERE id = :id")
    suspend fun markMeetingAsSynced(id: String)

    @Query("SELECT * FROM meetings WHERE tags LIKE '%' || :tag || '%'")
    fun getMeetingsByTag(tag: String): Flow<List<Meeting>>
}
