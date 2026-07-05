package com.scribesync.scribesync.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await

class ContactRepository(
    private val contactDao: ContactDao,
    private val firestore: FirebaseFirestore?
) {
    fun getContacts(ownerId: String): Flow<List<Contact>> =
        contactDao.getContactsForOwner(ownerId).flowOn(Dispatchers.IO)

    suspend fun findExistingContact(ownerId: String, contactUserId: String): Contact? =
        contactDao.findByContactUserId(ownerId, contactUserId)

    suspend fun saveContact(contact: Contact) = contactDao.insertContact(contact)

    suspend fun updateContact(contact: Contact) = contactDao.updateContact(contact)

    suspend fun deleteContact(contact: Contact) {
        contactDao.deleteContact(contact.id)
        try {
            firestore?.collection("users")
                ?.document(contact.ownerId)
                ?.collection("contacts")
                ?.document(contact.id)
                ?.delete()
                ?.await()
        } catch (e: Exception) {
            Log.e("ContactRepository", "Failed to delete contact from cloud", e)
        }
    }

    suspend fun syncContactsToCloud(ownerId: String) {
        val db = firestore ?: return
        val unsynced = contactDao.getUnsyncedContacts(ownerId)
        for (contact in unsynced) {
            try {
                val cloudContact = contact.copy(isSynced = true)
                db.collection("users")
                    .document(ownerId)
                    .collection("contacts")
                    .document(contact.id)
                    .set(cloudContact)
                    .await()
                contactDao.markContactAsSynced(contact.id)
            } catch (e: Exception) {
                Log.e("ContactRepository", "Error syncing contact", e)
            }
        }
    }
}
