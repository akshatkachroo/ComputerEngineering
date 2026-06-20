package com.scribesync.scribesync.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await

class TranscriptRepository(
    private val meetingDao: MeetingDao,
    private val firestore: FirebaseFirestore?
) {
    // Flow is observed on IO thread to prevent UI hangs during startup queries
    val allMeetings: Flow<List<Meeting>> = meetingDao.getAllMeetings().flowOn(Dispatchers.IO)

    fun getTranscript(meetingId: String): Flow<List<TranscriptEntry>> =
        meetingDao.getTranscriptForMeeting(meetingId).flowOn(Dispatchers.IO)

    suspend fun saveMeeting(meeting: Meeting) {
        meetingDao.insertMeeting(meeting)
    }

    suspend fun saveTranscriptEntry(entry: TranscriptEntry) {
        meetingDao.insertTranscriptEntry(entry)
    }

    suspend fun getMeetingById(id: String): Meeting? = meetingDao.getMeetingById(id)

    suspend fun deleteMeeting(id: String) {
        meetingDao.deleteMeeting(id)
        try {
            firestore?.collection("meetings")?.document(id)?.delete()?.await()
        } catch (e: Exception) { }
    }

    suspend fun syncMeetingsToCloud() {
        val db = firestore ?: return
        val unsyncedMeetings = meetingDao.getUnsyncedMeetings()
        for (meeting in unsyncedMeetings) {
            try {
                val cloudMeeting = meeting.copy(isSynced = true)
                Log.d("TranscriptRepository", "Syncing meeting to cloud: $cloudMeeting")
                db.collection("meetings")
                    .document(meeting.id)
                    .set(cloudMeeting)
                    .await()
                meetingDao.markMeetingAsSynced(meeting.id)
            } catch (e: Exception) {
                Log.e("TranscriptRepository", "Error syncing meeting", e)
            }
        }
    }

    suspend fun syncTranscriptsToCloud() {
        val db = firestore ?: return
        val unsyncedEntries = meetingDao.getUnsyncedEntries()
        if (unsyncedEntries.isEmpty()) return
        for (entry in unsyncedEntries) {
            try {
                val cloudEntry = entry.copy(isSynced = true)
                Log.d("TranscriptRepository", "Syncing entry to cloud: $cloudEntry")
                db.collection("transcripts")
                    .add(cloudEntry)
                    .await()
                meetingDao.markTranscriptEntriesAsSynced(listOf(entry.id))
            } catch (e: Exception) {
                Log.e("TranscriptRepository", "Error syncing transcript", e)
            }
        }
    }
}
