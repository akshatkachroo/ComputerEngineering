package com.scribesync.scribesync.ui.viewmodel

import android.app.Application
import android.content.Intent
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
    private var startTime: Long = 0

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

        val intent = Intent(getApplication(), AudioCaptureService::class.java)
        getApplication<Application>().startForegroundService(intent)

        viewModelScope.launch {
            val newMeeting = Meeting(
                title = title,
                date = Date(),
                isSynced = false
            )
            val meetingId = newMeeting.id
            currentMeetingId = meetingId
            repository.saveMeeting(newMeeting)

            // Capture location in background
            launch {
                val location = locationHelper.getCurrentLocation()
                if (location != null) {
                    repository.getMeetingById(meetingId)?.let {
                        repository.saveMeeting(it.copy(latitude = location.first, longitude = location.second))
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
        entries.forEach { repository.saveTranscriptEntry(it) }
    }

    fun stopMeeting() {
        _uiState.value = MeetingUiState.AwaitingAudio
        
        viewModelScope.launch {
            val intent = Intent(getApplication(), AudioCaptureService::class.java)
            getApplication<Application>().stopService(intent)
            
            transcriptionJob?.cancel()
            transcriptionJob = null

            if (nativeContextPtr != 0L) {
                whisperEngine.freeContext(nativeContextPtr)
                nativeContextPtr = 0
            }
            
            val duration = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            currentMeetingId?.let { id ->
                repository.getMeetingById(id)?.let { currentMeeting ->
                    val preview = transcript.value.take(3).joinToString(" ") { it.text }
                    repository.saveMeeting(currentMeeting.copy(
                        durationSeconds = duration,
                        transcriptPreview = preview
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

    sealed class MeetingUiState {
        object AwaitingAudio : MeetingUiState()
        object ActiveRecording : MeetingUiState()
    }
}
