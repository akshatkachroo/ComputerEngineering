package com.scribesync.scribesync.data

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AttendeeRequestRepository(
    private val firestore: FirebaseFirestore?
) {
    suspend fun sendRequest(meeting: Meeting, inviteeUserId: String): Result<Unit> = runCatching {
        val db = firestore ?: error("Firebase is not available")
        val request = AttendeeRequest(
            meetingId = meeting.id,
            meetingTitle = meeting.title,
            meetingOwnerId = meeting.ownerId,
            meetingOwnerName = meeting.ownerName,
            inviteeUserId = inviteeUserId,
            status = "pending"
        )
        db.collection("meetings")
            .document(meeting.id)
            .collection("attendeeRequests")
            .document(inviteeUserId)
            .set(request)
            .await()
    }

    // Live - the invitee's app has no other way to learn about a request
    // created on someone else's device.
    fun getPendingRequestsForUser(uid: String): Flow<List<AttendeeRequest>> = callbackFlow {
        val db = firestore
        if (db == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration = db.collectionGroup("attendeeRequests")
            .whereEqualTo("inviteeUserId", uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("AttendeeRequestRepository", "Error listening for requests", error)
                    return@addSnapshotListener
                }
                val requests = snapshot?.documents?.mapNotNull {
                    it.toObject(AttendeeRequest::class.java)
                } ?: emptyList()
                trySend(requests)
            }

        awaitClose { registration.remove() }
    }

    suspend fun respondToRequest(request: AttendeeRequest, accept: Boolean): Result<Unit> = runCatching {
        val db = firestore ?: error("Firebase is not available")
        db.collection("meetings")
            .document(request.meetingId)
            .collection("attendeeRequests")
            .document(request.inviteeUserId)
            .update("status", if (accept) "accepted" else "declined")
            .await()

        if (accept) {
            // Use a merge-set rather than update() so this still works even
            // if the creator hasn't synced this meeting document yet.
            db.collection("meetings")
                .document(request.meetingId)
                .set(mapOf("attendeeIds" to FieldValue.arrayUnion(request.inviteeUserId)), SetOptions.merge())
                .await()
        }
    }
}
