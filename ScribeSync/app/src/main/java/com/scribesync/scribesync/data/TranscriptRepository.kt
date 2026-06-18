package com.scribesync.scribesync.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class TranscriptRepository(
    private val meetingDao: MeetingDao,
    private val firestore: FirebaseFirestore
) {
    val allMeetings: Flow<List<Meeting>> = meetingDao.getAllMeetings()

    fun getTranscript(meetingId: String): Flow<List<TranscriptEntry>> =
        meetingDao.getTranscriptForMeeting(meetingId)

    suspend fun saveMeeting(meeting: Meeting) {
        meetingDao.insertMeeting(meeting)
    }

    suspend fun saveTranscriptEntry(entry: TranscriptEntry) {
        meetingDao.insertTranscriptEntry(entry)
    }

    suspend fun syncMeetingsToCloud() {
        val unsyncedMeetings = meetingDao.getUnsyncedMeetings()
        for (meeting in unsyncedMeetings) {
            try {
                firestore.collection("meetings")
                    .document(meeting.id)
                    .set(meeting)
                    .await()
                meetingDao.markMeetingAsSynced(meeting.id)
            } catch (e: Exception) {
                // Log or handle error
            }
        }
    }

    suspend fun syncTranscriptsToCloud() {
        val unsyncedEntries = meetingDao.getUnsyncedEntries()
        if (unsyncedEntries.isEmpty()) return

        // Batch sync would be better, but starting with individual for simplicity
        for (entry in unsyncedEntries) {
            try {
                firestore.collection("transcripts")
                    .add(entry)
                    .await()
                meetingDao.markTranscriptEntriesAsSynced(listOf(entry.id))
            } catch (e: Exception) {
                // Log or handle error
            }
        }
    }
}
