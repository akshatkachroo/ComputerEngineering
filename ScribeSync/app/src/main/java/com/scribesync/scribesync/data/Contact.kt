package com.scribesync.scribesync.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

// A contact is always a real registered account found by email search
// (see AuthRepository.findUserByEmail) - not a freeform entry.
@Entity(tableName = "contacts", indices = [Index(value = ["ownerId"])])
data class Contact(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val ownerId: String,
    val contactUserId: String,
    val username: String,
    val email: String,
    val isSynced: Boolean = false
)
