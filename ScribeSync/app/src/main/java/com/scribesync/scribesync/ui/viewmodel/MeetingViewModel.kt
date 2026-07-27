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
import com.scribesync.scribesync.data.AttendeeRequestRepository
import com.scribesync.scribesync.data.AuthRepository
import com.scribesync.scribesync.data.Meeting
import com.scribesync.scribesync.data.TranscriptEntry
import com.scribesync.scribesync.data.TranscriptRepository
import com.scribesync.scribesync.engine.WhisperEngine
import com.scribesync.scribesync.service.AudioCaptureService
import com.scribesync.scribesync.util.LocationHelper
import com.scribesync.scribesync.util.NetworkObserver
import com.scribesync.scribesync.util.SummaryService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private val audioDataFlow: SharedFlow<FloatArray>,
    private val authRepository: AuthRepository,
    private val attendeeRequestRepository: AttendeeRequestRepository
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
                    audioDataFlow = application.audioDataFlow,
                    authRepository = application.authRepository,
                    attendeeRequestRepository = application.attendeeRequestRepository
                )
            }
        }
    }

    private val _uiState = MutableStateFlow<MeetingUiState>(MeetingUiState.AwaitingAudio)
    val uiState: StateFlow<MeetingUiState> = _uiState.asStateFlow()

    private val _transcript = MutableStateFlow<List<TranscriptEntry>>(emptyList())
    val transcript: StateFlow<List<TranscriptEntry>> = _transcript.asStateFlow()

    // Progress of on-device summarization, surfaced by the meeting detail screen while
    // the summary for [summarizingMeetingId] is still being produced.
    val summaryPhase: StateFlow<SummaryService.Phase> get() = summaryService.phase
    val modelDownloadState get() = summaryService.modelDownloadState
    private val _summarizingMeetingId = MutableStateFlow<String?>(null)
    val summarizingMeetingId: StateFlow<String?> = _summarizingMeetingId.asStateFlow()

    // True only while a phrase is actively being run through whisper_full -
    // lets the UI show a "Transcribing..." indicator during the processing
    // gap after each pause in speech, so it doesn't look stalled.
    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    private var currentMeetingId: String? = null
    private var nativeContextPtr: Long = 0
    private val whisperMutex = Mutex()
    private var transcriptionJob: Job? = null
    private var locationJob: Job? = null
    private var startTime: Long = 0
    private var lastLocation: Pair<Double, Double>? = null
    private var audioChannel: Channel<FloatArray>? = null
    private var collectionJob: Job? = null
    private var currentSpeakerNumber: Int = 1

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
        currentSpeakerNumber = 1
        _isTranscribing.value = false
        audioChannel = Channel(Channel.UNLIMITED)

        val intent = Intent(getApplication(), AudioCaptureService::class.java)
        getApplication<Application>().startForegroundService(intent)

        // Fetch the summary model (weights only - never meeting data) in the background
        // so it is usually ready by the time the meeting ends. Failures are retried and
        // reported honestly when the summary is actually requested.
        viewModelScope.launch(Dispatchers.IO) { summaryService.prefetchModel() }

        viewModelScope.launch {
            // Forward flow to channel for controlled draining on stop
            collectionJob = launch {
                try {
                    audioDataFlow.collect { 
                        audioChannel?.send(it)
                    }
                } catch (e: Exception) {
                    Log.d("MeetingViewModel", "Collection job ended: ${e.message}")
                }
            }

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
                isSynced = false,
                ownerName = authRepository.currentUsername.value ?: "You",
                ownerId = authRepository.currentUser.value?.uid ?: ""
            )
            val meetingId = newMeeting.id
            currentMeetingId = meetingId
            // Use withContext to ensure the meeting is fully saved before proceeding
            withContext(Dispatchers.IO) {
                repository.saveMeeting(newMeeting)
            }

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
                    val modelName = "ggml-small.en-tdrz.bin"
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

                        // Phrase-based chunking: instead of cutting on a fixed
                        // timer (which bisects words mid-syllable), accumulate
                        // audio into a single phrase buffer and only cut once
                        // we've seen a real pause. This means every chunk we
                        // hand to Whisper is a naturally-bounded phrase, so the
                        // old overlap/dedup logic is no longer needed.
                        val analysisChunkMs = 200L
                        val analysisChunkSamples = (16000 * analysisChunkMs / 1000).toInt()
                        val silenceThreshold = 0.005f
                        val silenceCutMs = 5000L
                        val maxPhraseSamples = 16000 * 30 // 30s is the sweet spot for Whisper context

                        val analysisBuffer = mutableListOf<Float>()
                        val phraseBuffer = mutableListOf<Float>()
                        var totalSamplesProcessed = 0L
                        var silenceRunMs = 0L
                        var hasSpeech = false

                        suspend fun flushPhrase() {
                            if (hasSpeech && phraseBuffer.isNotEmpty()) {
                                val phraseStartMs = (totalSamplesProcessed - phraseBuffer.size) * 1000L / 16000
                                val phraseAudio = phraseBuffer.toFloatArray()
                                Log.d("MeetingViewModel", "Transcribing phrase of ${phraseAudio.size} samples at ${phraseStartMs}ms")

                                _isTranscribing.value = true
                                val segments = whisperMutex.withLock {
                                    if (nativeContextPtr != 0L) {
                                        whisperEngine.transcribeSegments(nativeContextPtr, phraseAudio)
                                    } else {
                                        emptyList()
                                    }
                                }
                                _isTranscribing.value = false

                                // Filter out common Whisper hallucination tokens
                                val nonHallucinatedSegments = segments.filter { segment ->
                                    val text = segment.text.lowercase()
                                    !text.contains("music") &&
                                    !text.contains("blank_audio") &&
                                    !text.contains("thank you") &&
                                    text.isNotBlank()
                                }

                                if (nonHallucinatedSegments.isNotEmpty()) {
                                    processSegments(meetingId, nonHallucinatedSegments, phraseStartMs)
                                }
                            }
                            phraseBuffer.clear()
                            silenceRunMs = 0L
                            hasSpeech = false
                        }

                        audioChannel?.consumeAsFlow()?.collect { audioData ->
                            analysisBuffer.addAll(audioData.toList())

                            while (analysisBuffer.size >= analysisChunkSamples) {
                                val slice = analysisBuffer.subList(0, analysisChunkSamples).toFloatArray()
                                analysisBuffer.subList(0, analysisChunkSamples).clear()

                                var sumSquares = 0f
                                for (sample in slice) sumSquares += sample * sample
                                val rms = Math.sqrt((sumSquares / slice.size).toDouble()).toFloat()

                                phraseBuffer.addAll(slice.toList())
                                totalSamplesProcessed += slice.size

                                if (rms > silenceThreshold) {
                                    hasSpeech = true
                                    silenceRunMs = 0L
                                } else {
                                    silenceRunMs += analysisChunkMs
                                }

                                val shouldCutForSilence = hasSpeech && silenceRunMs >= silenceCutMs
                                val shouldCutForLength = phraseBuffer.size >= maxPhraseSamples

                                if (shouldCutForSilence || shouldCutForLength) {
                                    flushPhrase()
                                }
                            }
                        }

                        // Channel closed (recording stopped) - flush whatever phrase was in progress
                        flushPhrase()
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

    private suspend fun processSegments(meetingId: String, segments: List<WhisperEngine.Segment>, baseTimestampMs: Long) {
        Log.d("MeetingViewModel", "Processing ${segments.size} segments for meeting: $meetingId at $baseTimestampMs ms")

        // Chunks are now silence-isolated phrases rather than overlapping
        // windows, so there's no boundary duplication left to filter out.
        val entries = mutableListOf<TranscriptEntry>()
        for (segment in segments) {
            val text = segment.text.trim()
            if (text.isEmpty()) continue
            if (segment.isNewSpeaker) currentSpeakerNumber++
            entries.add(
                TranscriptEntry(
                    meetingId = meetingId,
                    speakerLabel = "Speaker $currentSpeakerNumber",
                    text = text,
                    // Per-segment timestamp: phrase start plus intra-phrase offset
                    timestampMs = baseTimestampMs + segment.t0,
                    isSynced = false
                )
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
            
            // 1. Stop feeding new data and signal EOF
            collectionJob?.cancel()
            collectionJob = null
            audioChannel?.close()
            
            // 2. Wait for the transcription job to finish processing the remainder
            Log.d("MeetingViewModel", "Stopping meeting, waiting for transcription job to drain...")
            transcriptionJob?.join()
            transcriptionJob = null
            
            // 3. Wait for the location job to finish
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
                // Ensure all transcript entries are fully saved before summarizing
                val finalEntries = withContext(Dispatchers.IO) {
                    repository.getTranscript(id).first()
                }
                
                repository.getMeetingById(id)?.let { currentMeeting ->
                    val fullTranscript = finalEntries.joinToString("\n") { "${it.speakerLabel}: ${it.text}" }
                    val preview = finalEntries.take(3).joinToString(" ") { it.text }

                    _summarizingMeetingId.value = id
                    val summary = when (val result = summaryService.generateSummary(fullTranscript)) {
                        is SummaryService.SummaryResult.Success -> result.text
                        is SummaryService.SummaryResult.EmptyTranscript ->
                            "No speech was captured in this meeting."
                        is SummaryService.SummaryResult.Failure -> {
                            Log.e("MeetingViewModel", "Summary failed: ${result.reason}")
                            "${SummaryService.FAILED_PREFIX} ${result.reason}"
                        }
                    }

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
            _summarizingMeetingId.value = null

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

    fun updateMeetingTags(id: String, newTags: List<String>) {
        viewModelScope.launch {
            repository.getMeetingById(id)?.let { meeting ->
                repository.updateMeeting(meeting.copy(tags = newTags, isSynced = false))
                repository.syncMeetingsToCloud()
            }
        }
    }

    // Attendees are only ever edited post-recording, from the meeting detail
    // screen - never during setup or the live recording flow. Only used to
    // remove attendees now; adding goes through sendAttendeeRequest below
    // since the invitee has to accept first.
    fun updateMeetingAttendees(id: String, newAttendeeIds: List<String>) {
        viewModelScope.launch {
            repository.getMeetingById(id)?.let { meeting ->
                repository.updateMeeting(meeting.copy(attendeeIds = newAttendeeIds, isSynced = false))
                repository.syncMeetingsToCloud()
            }
        }
    }

    fun sendAttendeeRequest(meetingId: String, inviteeUserId: String) {
        viewModelScope.launch {
            repository.getMeetingById(meetingId)?.let { meeting ->
                attendeeRequestRepository.sendRequest(meeting, inviteeUserId)
            }
        }
    }

    // Called from a live listener while the owner has this meeting's detail
    // screen open, so accepted invites show up without a manual refresh.
    fun mergeRemoteAttendeeIds(meetingId: String, remoteIds: List<String>) {
        viewModelScope.launch {
            repository.getMeetingById(meetingId)?.let { meeting ->
                val merged = (meeting.attendeeIds + remoteIds).distinct()
                if (merged != meeting.attendeeIds) {
                    repository.updateMeeting(meeting.copy(attendeeIds = merged, isSynced = false))
                }
            }
        }
    }

    sealed class MeetingUiState {
        object AwaitingAudio : MeetingUiState()
        object ActiveRecording : MeetingUiState()
        object ProcessingSummary : MeetingUiState()
    }
}
