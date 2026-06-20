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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.util.Date

class MeetingViewModel(
    application: Application,
    val repository: TranscriptRepository,
    private val whisperEngine: WhisperEngine,
    private val locationHelper: LocationHelper,
    private val networkObserver: NetworkObserver,
    private val summaryService: com.scribesync.scribesync.util.SummaryService,
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
                    summaryService = application.summaryService,
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
    private val whisperMutex = Mutex()
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
                    val modelName = "ggml-tiny.en-q8_0.bin"
                    val modelPath = copyAssetToInternalStorage(modelName)
                    if (modelPath != null) {
                        Log.d("MeetingViewModel", "Initializing Whisper with model: $modelPath")
                        whisperMutex.withLock {
                            nativeContextPtr = whisperEngine.initContext(modelPath)
                        }
                    } else {
                        Log.e("MeetingViewModel", "Failed to copy model asset: $modelName")
                    }
                }
                
                if (nativeContextPtr != 0L) {
                    transcriptionJob = launch {
                        Log.d("MeetingViewModel", "Transcription collector started")
                        val audioBuffer = mutableListOf<Float>()
                        
                        // Sliding Window Parameters
                        val windowSize = 16000 * 5 // 5 seconds of audio
                        val stepSize = 16000 * 3   // Move 3 seconds forward (2s overlap)
                        val silenceThreshold = 0.01f // RMS energy threshold
                        
                        audioDataFlow.collect { audioData ->
                            audioBuffer.addAll(audioData.toList())
                            
                            // Process when we have at least one window
                            while (audioBuffer.size >= windowSize) {
                                val window = audioBuffer.take(windowSize).toFloatArray()
                                
                                // Simple VAD: Check RMS energy of the window
                                var sumSquares = 0f
                                for (sample in window) sumSquares += sample * sample
                                val rms = Math.sqrt((sumSquares / window.size).toDouble()).toFloat()
                                
                                if (rms > silenceThreshold) {
                                    Log.d("MeetingViewModel", "Transcribing window, RMS: $rms")
                                    val segments = whisperMutex.withLock {
                                        if (nativeContextPtr != 0L) {
                                            whisperEngine.transcribeSegments(nativeContextPtr, window)
                                        } else {
                                            emptyList()
                                        }
                                    }
                                    
                                    // Filter out common Whisper hallucination tokens
                                    val validSegments = segments.filter { segment ->
                                        val text = segment.text.lowercase()
                                        !text.contains("music") && 
                                        !text.contains("blank_audio") && 
                                        !text.contains("thank you") && // Common hallucination in quiet
                                        text.isNotBlank()
                                    }

                                    if (validSegments.isNotEmpty()) {
                                        processSegments(meetingId, validSegments)
                                    }
                                } else {
                                    Log.d("MeetingViewModel", "Skipping silent window, RMS: $rms")
                                }
                                
                                // Move the window forward by stepSize
                                repeat(stepSize) { if (audioBuffer.isNotEmpty()) audioBuffer.removeAt(0) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun copyAssetToInternalStorage(assetName: String): String? {
        val modelDir = File(getApplication<Application>().filesDir, "models")
        if (!modelDir.exists()) modelDir.mkdirs()
        
        val outFile = File(modelDir, assetName)
        if (outFile.exists()) return outFile.absolutePath
        
        return try {
            getApplication<Application>().assets.open("models/$assetName").use { inputStream ->
                FileOutputStream(outFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            outFile.absolutePath
        } catch (e: Exception) {
            Log.e("MeetingViewModel", "Error copying asset", e)
            null
        }
    }

    private suspend fun processSegments(meetingId: String, segments: List<WhisperEngine.Segment>) {
        Log.d("MeetingViewModel", "Processing ${segments.size} segments for meeting: $meetingId")
        
        // Use a set to avoid duplicating text from overlapping windows
        val existingTexts = _transcript.value.map { it.text.trim() }.toSet()
        
        val entries = segments
            .filter { it.text.trim().isNotEmpty() && !existingTexts.contains(it.text.trim()) }
            .map { segment ->
                TranscriptEntry(
                    meetingId = meetingId,
                    speakerLabel = "Speaker 1",
                    text = segment.text.trim(),
                    timestampMs = System.currentTimeMillis() - startTime, // Use relative meeting time
                    isSynced = false
                )
            }
        
        if (entries.isNotEmpty()) {
            _transcript.value = _transcript.value + entries
            entries.forEach { entry ->
                Log.d("MeetingViewModel", "Saving entry to DB: ${entry.text.take(20)}...")
                repository.saveTranscriptEntry(entry)
            }
        }
    }

    fun stopMeeting() {
        _uiState.value = MeetingUiState.ProcessingSummary
        
        viewModelScope.launch {
            val intent = Intent(getApplication(), AudioCaptureService::class.java)
            getApplication<Application>().stopService(intent)
            
            // 1. Cancel the transcription job FIRST to stop sending data to native context
            transcriptionJob?.cancel()
            transcriptionJob = null
            
            // 2. Wait for the location job to finish
            Log.d("MeetingViewModel", "Stopping meeting, waiting for location job...")
            locationJob?.join()
            locationJob = null
            Log.d("MeetingViewModel", "Location job finished, proceeding with stop.")

            // 3. Free the native context AFTER the job is definitely stopped
            whisperMutex.withLock {
                if (nativeContextPtr != 0L) {
                    val ptrToFree = nativeContextPtr
                    nativeContextPtr = 0
                    whisperEngine.freeContext(ptrToFree)
                }
            }
            
            val duration = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            currentMeetingId?.let { id ->
                repository.getMeetingById(id)?.let { currentMeeting ->
                    val fullTranscript = transcript.value.joinToString("\n") { "${it.speakerLabel}: ${it.text}" }
                    val preview = transcript.value.take(3).joinToString(" ") { it.text }
                    
                    val summary = summaryService.generateSummary(fullTranscript)
                    
                    repository.updateMeeting(currentMeeting.copy(
                        durationSeconds = duration,
                        transcriptPreview = preview,
                        summary = summary,
                        // Ensure we use the last captured location even if DB fetch was old
                        latitude = currentMeeting.latitude ?: lastLocation?.first,
                        longitude = currentMeeting.longitude ?: lastLocation?.second,
                        isSynced = false
                    ))
                }
            }
            
            repository.syncMeetingsToCloud()
            repository.syncTranscriptsToCloud()
            _uiState.value = MeetingUiState.AwaitingAudio
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
        object ProcessingSummary : MeetingUiState()
    }
}
