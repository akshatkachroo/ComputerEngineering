package com.scribesync.scribesync

import android.app.Application
import com.scribesync.scribesync.data.AppDatabase
import com.scribesync.scribesync.data.TranscriptRepository
import com.scribesync.scribesync.engine.WhisperEngine
import com.scribesync.scribesync.util.LocationHelper
import com.scribesync.scribesync.util.NetworkObserver
import com.google.firebase.firestore.FirebaseFirestore

class ScribeSyncApplication : Application() {
    private val database by lazy { AppDatabase.getDatabase(this) }
    
    // In a real app with google-services.json, we would use FirebaseFirestore.getInstance()
    // For now, we'll provide a mock or handle the absence of the JSON
    private val firestore by lazy { 
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            // Mock or dummy instance if Firebase is not initialized
            null
        }
    }

    val repository by lazy { 
        TranscriptRepository(
            database.meetingDao(), 
            firestore ?: throw IllegalStateException("Firestore not initialized")
        ) 
    }
    
    val whisperEngine by lazy { WhisperEngine() }
    val locationHelper by lazy { LocationHelper(this) }
    val networkObserver by lazy { NetworkObserver(this) }
}
