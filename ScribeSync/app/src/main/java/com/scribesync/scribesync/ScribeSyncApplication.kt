package com.scribesync.scribesync

import android.app.Application
import android.util.Log
import com.scribesync.scribesync.data.AppDatabase
import com.scribesync.scribesync.data.AttendeeRequestRepository
import com.scribesync.scribesync.data.AuthRepository
import com.scribesync.scribesync.data.ContactRepository
import com.scribesync.scribesync.data.TranscriptRepository
import com.scribesync.scribesync.engine.LlamaEngine
import com.scribesync.scribesync.engine.WhisperEngine
import com.scribesync.scribesync.util.LocationHelper
import com.scribesync.scribesync.util.NetworkObserver
import com.scribesync.scribesync.util.SummaryModelManager
import com.scribesync.scribesync.util.SummaryService
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
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

    val firebaseAuth by lazy {
        try {
            if (FirebaseApp.getApps(this).isNotEmpty()) {
                FirebaseAuth.getInstance()
            } else null
        } catch (e: Exception) { null }
    }

    val repository by lazy {
        TranscriptRepository(database.meetingDao(), firestore)
    }

    val authRepository by lazy {
        AuthRepository(firebaseAuth, firestore, database.userProfileDao())
    }

    val contactRepository by lazy {
        ContactRepository(database.contactDao(), firestore)
    }

    val attendeeRequestRepository by lazy {
        AttendeeRequestRepository(firestore)
    }

    val whisperEngine by lazy { WhisperEngine() }
    val locationHelper by lazy { LocationHelper(this) }
    val networkObserver by lazy { NetworkObserver(this) }
    val summaryModelManager by lazy { SummaryModelManager(this) }
    val summaryService by lazy { SummaryService(summaryModelManager, LlamaEngine()) }
    
    val audioDataFlow = MutableSharedFlow<FloatArray>(extraBufferCapacity = 256)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application onCreate finished")
    }
}
