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
}
