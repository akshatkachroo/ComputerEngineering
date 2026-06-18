package com.scribesync.scribesync.ui.viewmodel

import androidx.lifecycle.ViewModel
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
    val repository: TranscriptRepository,
    private val whisperEngine: WhisperEngine,
    private val locationHelper: LocationHelper,
    private val networkObserver: NetworkObserver,
    private val audioDataFlow: SharedFlow<FloatArray>
) : ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as ScribeSyncApplication)
                MeetingViewModel(
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

    init {
        observeNetwork()
    }

    fun startMeeting(title: String) {
        viewModelScope.launch {
            _uiState.value = MeetingUiState.ActiveRecording
            val location = locationHelper.getCurrentLocation()
            val newMeeting = Meeting(
                title = title,
                date = Date(),
                latitude = location?.first,
                longitude = location?.second,
                isSynced = false
            )
            val meetingId = newMeeting.id
            currentMeetingId = meetingId
            repository.saveMeeting(newMeeting)

            // Initialize whisper engine on background thread
            launch(Dispatchers.Default) {
                // In real app, model path would be from assets
                nativeContextPtr = whisperEngine.initContext("models/your_model.bin")
                
                if (nativeContextPtr != 0L) {
                    // Start consuming audio data and transcribing in real-time
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
        
        // Update UI stream
        _transcript.value = _transcript.value + entries
        
        // Save to Room for persistence and later cloud sync
        entries.forEach { repository.saveTranscriptEntry(it) }
    }

    fun stopMeeting() {
        viewModelScope.launch {
            _uiState.value = MeetingUiState.ProcessingDiarization
            
            transcriptionJob?.cancel()
            transcriptionJob = null

            // Logic to finalize transcript and free native memory
            if (nativeContextPtr != 0L) {
                whisperEngine.freeContext(nativeContextPtr)
                nativeContextPtr = 0
            }
            
            _uiState.value = MeetingUiState.CloudSynchronizing
            repository.syncMeetingsToCloud()
            repository.syncTranscriptsToCloud()
            
            _uiState.value = MeetingUiState.AwaitingAudio
        }
    }

    private fun observeNetwork() {
        viewModelScope.launch {
            networkObserver.networkStatus.collect { status ->
                if (status == NetworkObserver.Status.Available) {
                    launch { repository.syncMeetingsToCloud() }
                    launch { repository.syncTranscriptsToCloud() }
                }
            }
        }
    }

    sealed class MeetingUiState {
        object AwaitingAudio : MeetingUiState()
        object ActiveRecording : MeetingUiState()
        object ProcessingDiarization : MeetingUiState()
        object CloudSynchronizing : MeetingUiState()
    }
}
