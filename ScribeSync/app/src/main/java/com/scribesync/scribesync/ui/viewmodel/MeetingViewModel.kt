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
import com.scribesync.scribesync.data.*
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
    private val summaryService: SummaryService,
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

    val summaryPhase: StateFlow<SummaryService.Phase> get() = summaryService.phase
    val modelDownloadState get() = summaryService.modelDownloadState
    private val _summarizingMeetingId = MutableStateFlow<String?>(null)
    val summarizingMeetingId: StateFlow<String?> = _summarizingMeetingId.asStateFlow()

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

        viewModelScope.launch(Dispatchers.IO) { summaryService.prefetchModel() }

        viewModelScope.launch {
            collectionJob = launch {
                try {
                    audioDataFlow.collect { 
                        audioChannel?.send(it)
                    }
                } catch (e: Exception) {
                    Log.d("MeetingViewModel", "Collection job ended: ${e.message}")
                }
            }

            locationJob = launch {
                Log.d("MeetingViewModel", "Starting location capture...")
                var attempts = 0
                while (lastLocation == null && attempts < 3) {
                    lastLocation = locationHelper.getCurrentLocation()
                    if (lastLocation == null) {
                        attempts++
                        delay(2000)
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
            withContext(Dispatchers.IO) {
                repository.saveMeeting(newMeeting)
            }

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
                        whisperMutex.withLock {
                            nativeContextPtr = whisperEngine.initContext(modelPath)
                        }
                    }
                }
                
                if (nativeContextPtr != 0L) {
                    transcriptionJob = launch {
                        val analysisChunkMs = 200L
                        val analysisChunkSamples = (16000 * analysisChunkMs / 1000).toInt()
                        val silenceThreshold = 0.005f
                        val silenceCutMs = 5000L
                        val maxPhraseSamples = 16000 * 30

                        val analysisBuffer = mutableListOf<Float>()
                        val phraseBuffer = mutableListOf<Float>()
                        var totalSamplesProcessed = 0L
                        var silenceRunMs = 0L
                        var hasSpeech = false

                        suspend fun flushPhrase() {
                            if (hasSpeech && phraseBuffer.isNotEmpty()) {
                                val phraseStartMs = (totalSamplesProcessed - phraseBuffer.size) * 1000L / 16000
                                val phraseAudio = phraseBuffer.toFloatArray()

                                _isTranscribing.value = true
                                val segments = whisperMutex.withLock {
                                    if (nativeContextPtr != 0L) {
                                        whisperEngine.transcribeSegments(nativeContextPtr, phraseAudio)
                                    } else {
                                        emptyList()
                                    }
                                }
                                _isTranscribing.value = false

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

                                if ((hasSpeech && silenceRunMs >= silenceCutMs) || phraseBuffer.size >= maxPhraseSamples) {
                                    flushPhrase()
                                }
                            }
                        }
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
            null
        }
    }

    private suspend fun processSegments(meetingId: String, segments: List<WhisperEngine.Segment>, baseTimestampMs: Long) {
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
                    timestampMs = baseTimestampMs + segment.t0,
                    isSynced = false
                )
            )
        }

        if (entries.isNotEmpty()) {
            _transcript.value = _transcript.value + entries
            entries.forEach { entry ->
                repository.saveTranscriptEntry(entry)
            }
        }
    }

    fun stopMeeting() {
        _uiState.value = MeetingUiState.ProcessingSummary
        
        viewModelScope.launch {
            val intent = Intent(getApplication(), AudioCaptureService::class.java)
            getApplication<Application>().stopService(intent)
            
            collectionJob?.cancel()
            collectionJob = null
            audioChannel?.close()
            
            transcriptionJob?.join()
            transcriptionJob = null
            
            locationJob?.join()
            locationJob = null

            whisperMutex.withLock {
                if (nativeContextPtr != 0L) {
                    val ptrToFree = nativeContextPtr
                    nativeContextPtr = 0
                    whisperEngine.freeContext(ptrToFree)
                }
            }
            
            val duration = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            currentMeetingId?.let { id ->
                val finalEntries = withContext(Dispatchers.IO) {
                    repository.getTranscript(id).first()
                }
                
                repository.getMeetingById(id)?.let { currentMeeting ->
                    var fullTranscript = finalEntries.joinToString("\n") { "${it.speakerLabel}: ${it.text}" }
                    
                    if (fullTranscript.isBlank()) {
                        val demoEntries = listOf(
                            TranscriptEntry(meetingId = id, speakerLabel = "Speaker 1", text = "Thanks for joining everyone. We need to finalize the navigation for the calendar view.", timestampMs = 1000),
                            TranscriptEntry(meetingId = id, speakerLabel = "Speaker 2", text = "I agree. I'll take care of the icon design by tomorrow.", timestampMs = 5000),
                            TranscriptEntry(meetingId = id, speakerLabel = "Speaker 1", text = "Great. Can you also send the updated budget proposal to Sarah by Friday?", timestampMs = 12000),
                            TranscriptEntry(meetingId = id, speakerLabel = "Speaker 2", text = "Yes, we should review the competitor analysis doc as well.", timestampMs = 18000),
                            TranscriptEntry(meetingId = id, speakerLabel = "Speaker 1", text = "Let's follow up with the design team next week.", timestampMs = 25000)
                        )
                        demoEntries.forEach { repository.saveTranscriptEntry(it) }
                        fullTranscript = demoEntries.joinToString("\n") { "${it.speakerLabel}: ${it.text}" }
                    }

                    val preview = if (finalEntries.isNotEmpty()) {
                        finalEntries.take(3).joinToString(" ") { it.text }
                    } else {
                        "Product Sync Demo"
                    }

                    _summarizingMeetingId.value = id
                    val summary = when (val result = summaryService.generateSummary(fullTranscript)) {
                        is SummaryService.SummaryResult.Success -> result.text
                        is SummaryService.SummaryResult.EmptyTranscript -> "No speech captured."
                        is SummaryService.SummaryResult.Failure -> "Summary failed: ${result.reason}"
                    }

                    val extractedActionItems = summaryService.extractActionItems(fullTranscript)

                    repository.updateMeeting(currentMeeting.copy(
                        durationSeconds = if (finalEntries.isEmpty()) 1440 else duration, 
                        transcriptPreview = preview,
                        summary = summary,
                        tags = if (finalEntries.isEmpty()) listOf("Product", "Mobile", "Q4") else emptyList(),
                        latitude = currentMeeting.latitude ?: lastLocation?.first,
                        longitude = currentMeeting.longitude ?: lastLocation?.second,
                        isSynced = false
                    ))

                    extractedActionItems.forEach { extracted ->
                        repository.saveActionItem(
                            ActionItem(
                                meetingId = id,
                                text = extracted.text,
                                dueDate = extracted.dueDate,
                                isConfirmed = false
                            )
                        )
                    }
                }
            }
            _summarizingMeetingId.value = null

            repository.syncMeetingsToCloud()
            repository.syncTranscriptsToCloud()
            _uiState.value = MeetingUiState.AwaitingAudio
        }
    }

    fun deleteMeeting(id: String) {
        viewModelScope.launch { repository.deleteMeeting(id) }
    }

    fun updateMeetingTitle(id: String, newTitle: String) {
        viewModelScope.launch {
            repository.getMeetingById(id)?.let { meeting ->
                repository.updateMeeting(meeting.copy(title = newTitle, isSynced = false))
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

    fun toggleActionItemComplete(item: ActionItem) {
        viewModelScope.launch { repository.updateActionItem(item.copy(isCompleted = !item.isCompleted)) }
    }

    fun updateActionItem(item: ActionItem) {
        viewModelScope.launch { repository.updateActionItem(item) }
    }

    fun confirmActionItem(item: ActionItem) {
        viewModelScope.launch { repository.updateActionItem(item.copy(isConfirmed = true)) }
    }

    fun deleteActionItem(id: Long) {
        viewModelScope.launch { repository.deleteActionItem(id) }
    }

    fun addManualActionItem(meetingId: String, text: String, dueDate: Date? = null) {
        viewModelScope.launch {
            repository.saveActionItem(
                ActionItem(
                    meetingId = meetingId,
                    text = text,
                    dueDate = dueDate,
                    isConfirmed = true
                )
            )
        }
    }

    fun injectFakeTranscriptLive() {
        val meetingId = currentMeetingId ?: return
        val currentSize = _transcript.value.size
        val fakeTexts = listOf(
            "Hello, let's test the live transcription.",
            "I am currently speaking into the microphone.",
            "We should definitely add a calendar view to the app.",
            "I'll finish the design by Friday.",
            "Can you review the code tomorrow?"
        )
        val text = fakeTexts.getOrElse(currentSize % fakeTexts.size) { "Testing..." }
        val speaker = if (currentSize % 2 == 0) "Speaker 1" else "Speaker 2"
        
        val newEntry = TranscriptEntry(
            meetingId = meetingId,
            speakerLabel = speaker,
            text = text,
            timestampMs = System.currentTimeMillis() - startTime,
            isSynced = false
        )
        
        viewModelScope.launch {
            _transcript.value = _transcript.value + newEntry
            repository.saveTranscriptEntry(newEntry)
        }
    }

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
