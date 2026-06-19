package com.scribesync.scribesync

import android.app.Application
import android.util.Log
import com.scribesync.scribesync.data.AppDatabase
import com.scribesync.scribesync.data.TranscriptRepository
import com.scribesync.scribesync.engine.WhisperEngine
import com.scribesync.scribesync.util.LocationHelper
import com.scribesync.scribesync.util.NetworkObserver
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableSharedFlow

class ScribeSyncApplication : Application() {
    private val TAG = "ScribeSyncApp"

    val database by lazy { AppDatabase.getDatabase(this) }
    
    val firestore by lazy { 
        try {
            if (FirebaseApp.getApps(this).isNotEmpty()) {
                FirebaseFirestore.getInstance()
            } else null
        } catch (e: Exception) { null }
    }

    val repository by lazy { 
        TranscriptRepository(database.meetingDao(), firestore) 
    }
    
    val whisperEngine by lazy { WhisperEngine() }
    val locationHelper by lazy { LocationHelper(this) }
    val networkObserver by lazy { NetworkObserver(this) }
    
    val audioDataFlow = MutableSharedFlow<FloatArray>(extraBufferCapacity = 64)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application onCreate finished")
    }
}
