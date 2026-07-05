package com.scribesync.scribesync.data

// Firestore-only - lives at meetings/{meetingId}/attendeeRequests/{inviteeUserId}.
// No local Room table: the invitee's device has no local copy of someone
// else's meeting, so there is nothing to persist locally except what the
// live query below reads directly from Firestore.
data class AttendeeRequest(
    val meetingId: String = "",
    val meetingTitle: String = "",
    val meetingOwnerId: String = "",
    val meetingOwnerName: String = "",
    val inviteeUserId: String = "",
    val status: String = "pending",
    val createdAt: Long = System.currentTimeMillis()
)
