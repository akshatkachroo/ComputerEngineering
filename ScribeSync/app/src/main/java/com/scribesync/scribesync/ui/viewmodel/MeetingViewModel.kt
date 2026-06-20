package com.scribesync.scribesync.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.scribesync.scribesync.ScribeSyncApplication
import androidx.lifecycle.viewModelScope
import com.scribesync.scribesync.data.Meeting
import com.scribesync.scribesync.data.TranscriptEntry
import com.scribesync.scribesync.data.TranscriptRepository
import com.scribesync.scribesync.engine.WhisperEngine
import com.scribesync.scribesync.service.AudioCaptureService
import com.scribesync.scribesync.util.LocationHelper
import com.scribesync.scribesync.util.NetworkObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

class MeetingViewModel(
    application: Application,
    val repository: TranscriptRepository,
    private val whisperEngine: WhisperEngine,
    private val locationHelper: LocationHelper,
    private val networkObserver: NetworkObserver,
    private val audioDataFlow: SharedFlow<FloatArray>
) : AndroidViewModel(application) {

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as ScribeSyncApplication)
                MeetingViewModel(
                    application = application,
                    repository = application.repository,
                    whisperEngine = application.whisperEngine,
                    locationHelper = application.locationHelper,
                    networkObserver = application.networkObserver,
                    audioDataFlow = application.audioDataFlow
                )
            }
        }
    }

    private val _uiState = MutableStateFlow<MeetingUiState>(MeetingUiState.AwaitingAudio)
    val uiState: StateFlow<MeetingUiState> = _uiState.asStateFlow()

    private val _transcript = MutableStateFlow<List<TranscriptEntry>>(emptyList())
    val transcript: StateFlow<List<TranscriptEntry>> = _transcript.asStateFlow()

    private var currentMeetingId: String? = null
    private var nativeContextPtr: Long = 0
    private var transcriptionJob: Job? = null
    private var locationJob: Job? = null
    private var startTime: Long = 0
    private var lastLocation: Pair<Double, Double>? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            networkObserver.networkStatus.collect { status ->
                if (status == NetworkObserver.Status.Available) {
                    launch { repository.syncMeetingsToCloud() }
                    launch { repository.syncTranscriptsToCloud() }
                }
            }
        }
    }

    fun startMeeting(title: String) {
        _uiState.value = MeetingUiState.ActiveRecording
        _transcript.value = emptyList()
        startTime = System.currentTimeMillis()
        lastLocation = null

        val intent = Intent(getApplication(), AudioCaptureService::class.java)
        getApplication<Application>().startForegroundService(intent)

        viewModelScope.launch {
            // Start location capture immediately
            locationJob = launch {
                Log.d("MeetingViewModel", "Starting location capture...")
                // Try to get a fix for up to 10 seconds
                var attempts = 0
                while (lastLocation == null && attempts < 3) {
                    lastLocation = locationHelper.getCurrentLocation()
                    if (lastLocation == null) {
                        attempts++
                        Log.d("MeetingViewModel", "Location attempt $attempts failed, retrying...")
                        delay(2000) // Wait before retrying
                    }
                }
                Log.d("MeetingViewModel", "Location capture finished: $lastLocation")
            }

            val newMeeting = Meeting(
                title = title,
                date = Date(),
                isSynced = false
            )
            val meetingId = newMeeting.id
            currentMeetingId = meetingId
            repository.saveMeeting(newMeeting)

            // Update meeting with location when it's ready
            launch {
                locationJob?.join()
                lastLocation?.let { loc ->
                    repository.getMeetingById(meetingId)?.let {
                        repository.updateMeeting(it.copy(latitude = loc.first, longitude = loc.second))
                    }
                }
            }

            launch(Dispatchers.Default) {
                if (nativeContextPtr == 0L) {
                    nativeContextPtr = whisperEngine.initContext("models/your_model.bin")
                }
                
                if (nativeContextPtr != 0L) {
                    transcriptionJob = launch {
                        audioDataFlow.collect { audioData ->
                            val segments = whisperEngine.transcribeSegments(nativeContextPtr, audioData)
                            if (segments.isNotEmpty()) {
                                processSegments(meetingId, segments)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun processSegments(meetingId: String, segments: List<WhisperEngine.Segment>) {
        Log.d("MeetingViewModel", "Processing ${segments.size} segments for meeting: $meetingId")
        val entries = segments.map { segment ->
            TranscriptEntry(
                meetingId = meetingId,
                speakerLabel = "Speaker ${segment.speakerId}",
                text = segment.text,
                timestampMs = segment.t0,
                isSynced = false
            )
        }
        
        _transcript.value = _transcript.value + entries
        entries.forEach { entry ->
            Log.d("MeetingViewModel", "Saving entry to DB: ${entry.text.take(20)}...")
            repository.saveTranscriptEntry(entry)
        }
    }

    fun stopMeeting() {
        _uiState.value = MeetingUiState.AwaitingAudio
        
        viewModelScope.launch {
            val intent = Intent(getApplication(), AudioCaptureService::class.java)
            getApplication<Application>().stopService(intent)
            
            transcriptionJob?.cancel()
            transcriptionJob = null
            
            // Wait for location to finish if it's still running
            Log.d("MeetingViewModel", "Stopping meeting, waiting for location job...")
            locationJob?.join()
            locationJob = null
            Log.d("MeetingViewModel", "Location job finished, proceeding with stop.")

            if (nativeContextPtr != 0L) {
                whisperEngine.freeContext(nativeContextPtr)
                nativeContextPtr = 0
            }
            
            val duration = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            currentMeetingId?.let { id ->
                repository.getMeetingById(id)?.let { currentMeeting ->
                    val preview = transcript.value.take(3).joinToString(" ") { it.text }
                    repository.updateMeeting(currentMeeting.copy(
                        durationSeconds = duration,
                        transcriptPreview = preview,
                        // Ensure we use the last captured location even if DB fetch was old
                        latitude = currentMeeting.latitude ?: lastLocation?.first,
                        longitude = currentMeeting.longitude ?: lastLocation?.second
                    ))
                }
            }
            
            repository.syncMeetingsToCloud()
            repository.syncTranscriptsToCloud()
        }
    }

    fun deleteMeeting(id: String) {
        viewModelScope.launch {
            repository.deleteMeeting(id)
        }
    }

    fun updateMeetingTitle(id: String, newTitle: String) {
        viewModelScope.launch {
            repository.getMeetingById(id)?.let { meeting ->
                repository.updateMeeting(meeting.copy(title = newTitle, isSynced = false))
                // Trigger sync immediately if possible
                repository.syncMeetingsToCloud()
            }
        }
    }

    sealed class MeetingUiState {
        object AwaitingAudio : MeetingUiState()
        object ActiveRecording : MeetingUiState()
    }
}
