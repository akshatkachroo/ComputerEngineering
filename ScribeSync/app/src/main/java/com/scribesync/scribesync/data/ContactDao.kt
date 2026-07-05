package com.scribesync.scribesync.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE ownerId = :ownerId ORDER BY username ASC")
    fun getContactsForOwner(ownerId: String): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE ownerId = :ownerId AND contactUserId = :contactUserId LIMIT 1")
    suspend fun findByContactUserId(ownerId: String, contactUserId: String): Contact?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact)

    @Update
    suspend fun updateContact(contact: Contact)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteContact(id: String)

    @Query("SELECT * FROM contacts WHERE ownerId = :ownerId AND isSynced = 0")
    suspend fun getUnsyncedContacts(ownerId: String): List<Contact>

    @Query("UPDATE contacts SET isSynced = 1 WHERE id = :id")
    suspend fun markContactAsSynced(id: String)
}
