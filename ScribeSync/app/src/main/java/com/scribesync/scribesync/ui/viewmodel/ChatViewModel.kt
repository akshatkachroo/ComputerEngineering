package com.scribesync.scribesync.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scribesync.scribesync.ScribeSyncApplication
import com.scribesync.scribesync.data.TranscriptRepository
import com.scribesync.scribesync.engine.LlamaEngine
import com.scribesync.scribesync.util.ChatMessage
import com.scribesync.scribesync.util.ChatRole
import com.scribesync.scribesync.util.ChatService
import com.scribesync.scribesync.util.SummaryModelManager
import com.scribesync.scribesync.util.SummaryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Chat about a single meeting, scoped to whichever nav destination opened it (see
 * [factory]) rather than the Activity-wide lifetime the app's other ViewModels use -
 * it owns a session-scoped native chat context that must be freed when the user backs
 * out of this specific screen, not when the app closes.
 */
class ChatViewModel(
    application: Application,
    private val meetingId: String,
    private val repository: TranscriptRepository,
    summaryModelManager: SummaryModelManager
) : AndroidViewModel(application) {

    companion object {
        fun factory(meetingId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ScribeSyncApplication
                ChatViewModel(
                    application = application,
                    meetingId = meetingId,
                    repository = application.repository,
                    summaryModelManager = application.summaryModelManager
                )
            }
        }
    }

    sealed class SessionState {
        object Loading : SessionState()
        object Ready : SessionState()
        object EmptyTranscript : SessionState()
        data class Error(val reason: String) : SessionState()
    }

    private val chatService = ChatService(summaryModelManager, LlamaEngine())

    private val _meetingTitle = MutableStateFlow("Meeting")
    val meetingTitle: StateFlow<String> = _meetingTitle.asStateFlow()

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Loading)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    val phase: StateFlow<ChatService.Phase> get() = chatService.phase
    val modelDownloadState: StateFlow<SummaryModelManager.DownloadState> get() = chatService.modelDownloadState

    init {
        viewModelScope.launch {
            val meeting = repository.getMeetingById(meetingId)
            _meetingTitle.value = meeting?.title ?: "Meeting"

            val transcriptEntries = repository.getTranscript(meetingId).first()
            val summary = meeting?.summary
            val context = if (!summary.isNullOrBlank() && !summary.startsWith(SummaryService.FAILED_PREFIX)) {
                summary
            } else {
                transcriptEntries.joinToString("\n") { "${it.speakerLabel}: ${it.text}" }
            }

            val wordCount = context.split(Regex("\\s+")).count { it.isNotBlank() }
            if (context.isBlank() || wordCount < 3) {
                _sessionState.value = SessionState.EmptyTranscript
                return@launch
            }

            val result = chatService.startSession(context)
            if (result.isFailure) {
                _sessionState.value = SessionState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to start chat session"
                )
                return@launch
            }
            _sessionState.value = SessionState.Ready
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _sessionState.value != SessionState.Ready) return

        val historyBeforeSend = _messages.value
        _messages.value = historyBeforeSend + ChatMessage(ChatRole.User, trimmed)

        viewModelScope.launch {
            when (val result = chatService.sendMessage(historyBeforeSend, trimmed)) {
                is ChatService.ChatResult.Success -> {
                    _messages.value = _messages.value + ChatMessage(ChatRole.Assistant, result.text)
                }
                is ChatService.ChatResult.Failure -> {
                    _messages.value = _messages.value + ChatMessage(
                        ChatRole.Assistant,
                        "Sorry, I couldn't generate a reply (${result.reason})."
                    )
                }
            }
        }
    }

    // viewModelScope is already cancelled by the time onCleared() runs, so the native
    // context is freed on an independent scope. ChatService's internal mutex still
    // serializes this behind any in-flight sendMessage() call, avoiding a native
    // use-after-free even if the user backs out mid-reply.
    override fun onCleared() {
        super.onCleared()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            chatService.endSession()
        }
    }
}
