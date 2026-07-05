package com.scribesync.scribesync.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scribesync.scribesync.ScribeSyncApplication
import com.scribesync.scribesync.data.AttendeeRequest
import com.scribesync.scribesync.data.AttendeeRequestRepository
import com.scribesync.scribesync.data.AuthRepository
import com.scribesync.scribesync.data.Contact
import com.scribesync.scribesync.data.ContactRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModel(
    application: Application,
    private val contactRepository: ContactRepository,
    private val authRepository: AuthRepository,
    private val attendeeRequestRepository: AttendeeRequestRepository
) : AndroidViewModel(application) {

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as ScribeSyncApplication)
                ContactsViewModel(
                    application = application,
                    contactRepository = application.contactRepository,
                    authRepository = application.authRepository,
                    attendeeRequestRepository = application.attendeeRequestRepository
                )
            }
        }
    }

    val contacts: StateFlow<List<Contact>> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else contactRepository.getContacts(user.uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingRequests: StateFlow<List<AttendeeRequest>> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else attendeeRequestRepository.getPendingRequestsForUser(user.uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun respondToRequest(request: AttendeeRequest, accept: Boolean) {
        viewModelScope.launch {
            attendeeRequestRepository.respondToRequest(request, accept)
        }
    }

    private val _searchState = MutableStateFlow<ContactSearchState>(ContactSearchState.Idle)
    val searchState: StateFlow<ContactSearchState> = _searchState.asStateFlow()

    fun searchAndAddContact(email: String) {
        val myUid = authRepository.currentUser.value?.uid ?: return
        val trimmedEmail = email.trim()

        if (trimmedEmail.isBlank()) {
            _searchState.value = ContactSearchState.Error("Enter an email to search")
            return
        }
        if (trimmedEmail.equals(authRepository.currentUser.value?.email, ignoreCase = true)) {
            _searchState.value = ContactSearchState.Error("You can't add yourself")
            return
        }

        _searchState.value = ContactSearchState.Loading
        viewModelScope.launch {
            val profile = authRepository.findUserByEmail(trimmedEmail)
            if (profile == null) {
                _searchState.value = ContactSearchState.Error("No user found with that email")
                return@launch
            }
            if (contactRepository.findExistingContact(myUid, profile.uid) != null) {
                _searchState.value = ContactSearchState.Error("${profile.username} is already in your contacts")
                return@launch
            }
            contactRepository.saveContact(
                Contact(
                    ownerId = myUid,
                    contactUserId = profile.uid,
                    username = profile.username,
                    email = profile.email
                )
            )
            contactRepository.syncContactsToCloud(myUid)
            _searchState.value = ContactSearchState.Success
        }
    }

    fun clearSearchState() {
        _searchState.value = ContactSearchState.Idle
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            contactRepository.deleteContact(contact)
        }
    }

    sealed class ContactSearchState {
        object Idle : ContactSearchState()
        object Loading : ContactSearchState()
        object Success : ContactSearchState()
        data class Error(val message: String) : ContactSearchState()
    }
}
