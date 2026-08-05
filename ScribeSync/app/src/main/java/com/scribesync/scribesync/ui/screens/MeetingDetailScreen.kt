package com.scribesync.scribesync.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scribesync.scribesync.data.Contact
import com.scribesync.scribesync.data.Meeting
import com.scribesync.scribesync.data.TranscriptEntry
import com.scribesync.scribesync.ui.components.*
import com.scribesync.scribesync.ui.viewmodel.AuthViewModel
import com.scribesync.scribesync.ui.viewmodel.ContactsViewModel
import com.scribesync.scribesync.ui.viewmodel.MeetingViewModel
import com.scribesync.scribesync.util.SummaryModelManager
import com.scribesync.scribesync.util.SummaryService
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MeetingDetailScreen(
    viewModel: MeetingViewModel,
    meetingId: String,
    onBack: () -> Unit,
    authViewModel: AuthViewModel,
    onAskAboutMeeting: (String) -> Unit,
    onSeeTasks: (String) -> Unit,
    contactsViewModel: ContactsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = ContactsViewModel.Factory)
) {
    val meetings by viewModel.repository.allMeetings.collectAsState(initial = emptyList())
    val meeting = meetings.find { it.id == meetingId }
    val transcript by viewModel.repository.getTranscript(meetingId).collectAsState(initial = emptyList())
    val groupedTranscript = remember(transcript) { groupConsecutiveBySpeaker(transcript) }
    val allContacts by contactsViewModel.contacts.collectAsState()
    val actionItems by viewModel.repository.getActionItems(meetingId).collectAsState(initial = emptyList())
    val confirmedCount = remember(actionItems) { actionItems.count { it.isConfirmed } }

    val attendees = allContacts.filter { it.contactUserId in (meeting?.attendeeIds ?: emptyList()) }
    val currentUser by authViewModel.currentUser.collectAsState()
    val isOwner = meeting != null && meeting.ownerId.isNotEmpty() && meeting.ownerId == currentUser?.uid
    
    val summarizingMeetingId by viewModel.summarizingMeetingId.collectAsState()
    val summaryPhase by viewModel.summaryPhase.collectAsState()
    val modelDownloadState by viewModel.modelDownloadState.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var showAddAttendeeDialog by remember { mutableStateOf(false) }
    
    var newTitle by remember { mutableStateOf(meeting?.title ?: "") }
    var newTag by remember { mutableStateOf("") }

    if (meeting == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Meeting not found")
        }
        return
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Title") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Meeting Title") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateMeetingTitle(meetingId, newTitle)
                    showEditDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddTagDialog) {
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            title = { Text("Add Tag") },
            text = {
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    label = { Text("Tag Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newTag.isNotBlank()) {
                        val updatedTags = (meeting.tags + newTag.trim()).distinct()
                        viewModel.updateMeetingTags(meetingId, updatedTags)
                    }
                    newTag = ""
                    showAddTagDialog = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddAttendeeDialog) {
        AddAttendeeDialog(
            allContacts = allContacts,
            currentAttendeeIds = meeting.attendeeIds.toSet(),
            onDismiss = { showAddAttendeeDialog = false },
            onAddAttendee = { contact ->
                viewModel.sendAttendeeRequest(meetingId, contact.contactUserId)
                showAddAttendeeDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Recording") },
            text = { Text("Are you sure? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMeeting(meetingId)
                    showDeleteDialog = false
                    onBack()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meeting Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onAskAboutMeeting(meetingId) }) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Ask AI")
                    }
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Title")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { MeetingHeaderSection(meeting = meeting) }

            item {
                TasksButton(
                    taskCount = confirmedCount,
                    onClick = { onSeeTasks(meetingId) }
                )
            }

            item {
                Text("Tags", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    meeting.tags.forEach { tag ->
                        AssistChip(onClick = { }, label = { Text(tag) }, trailingIcon = {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(AssistChipDefaults.IconSize).clickable {
                                viewModel.updateMeetingTags(meetingId, meeting.tags.filter { it != tag })
                            })
                        })
                    }
                    AssistChip(onClick = { showAddTagDialog = true }, label = { Text("Add Tag") }, leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(AssistChipDefaults.IconSize)) })
                }
            }

            item {
                Text("Attendees", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    attendees.forEach { contact ->
                        AssistChip(
                            onClick = { },
                            label = { Text(contact.username) },
                            trailingIcon = if (isOwner) {
                                {
                                    Icon(Icons.Default.Close, null, modifier = Modifier.size(AssistChipDefaults.IconSize).clickable {
                                        viewModel.updateMeetingAttendees(meetingId, meeting.attendeeIds.filter { it != contact.contactUserId })
                                    })
                                }
                            } else null
                        )
                    }
                    AssistChip(onClick = { showAddAttendeeDialog = true }, label = { Text("Add Attendee") }, leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(AssistChipDefaults.IconSize)) })
                }
            }

            if (!meeting.summary.isNullOrEmpty()) {
                item { SummarySection(summary = meeting.summary) }
            } else if (summarizingMeetingId == meetingId) {
                item { SummaryInProgressSection(phase = summaryPhase, downloadState = modelDownloadState) }
            }

            item { HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant) }
            item { Text("Transcript", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

            if (transcript.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), Alignment.Center) { Text("No transcript available", color = MaterialTheme.colorScheme.outline) } }
            } else {
                items(groupedTranscript.size) { index ->
                    val group = groupedTranscript[index]
                    val prevGroup = if (index > 0) groupedTranscript[index - 1] else null
                    TranscriptDetailItem(
                        group = group,
                        showSpeaker = prevGroup == null || prevGroup.last().speakerLabel != group.first().speakerLabel
                    )
                }
            }
        }
    }
}

@Composable
fun MeetingHeaderSection(meeting: Meeting) {
    Column {
        Text(meeting.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Today at ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(meeting.date)} · ${formatDurationForHeader(meeting.durationSeconds)} recording",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SyncStatusBadge(isSynced = meeting.isSynced)
            Spacer(Modifier.width(8.dp))
            OwnerBadge(ownerName = meeting.ownerName)
        }
    }
}

@Composable
private fun SummaryInProgressSection(phase: SummaryService.Phase, downloadState: SummaryModelManager.DownloadState) {
    val label = when {
        downloadState is SummaryModelManager.DownloadState.Downloading -> "Downloading model… ${downloadState.percent}%"
        downloadState is SummaryModelManager.DownloadState.Verifying -> "Verifying model…"
        phase is SummaryService.Phase.LoadingModel -> "Loading model…"
        phase is SummaryService.Phase.Generating -> "Generating summary…"
        else -> "Preparing summary…"
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SummarySection(summary: String) {
    val failed = summary.startsWith(SummaryService.FAILED_PREFIX)
    val body = if (failed) "Summary failed: ${summary.removePrefix(SummaryService.FAILED_PREFIX).trim()}" else summary
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (failed) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = if (failed) Icons.Default.Warning else Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(if (failed) "AI Summary Unavailable" else "AI Summary", style = MaterialTheme.typography.titleSmall, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun TranscriptDetailItem(group: List<TranscriptEntry>, showSpeaker: Boolean) {
    val firstEntry = group.first()
    val speakerColor = when (firstEntry.speakerLabel) {
        "Speaker 1" -> MaterialTheme.colorScheme.primary
        "Speaker 2" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showSpeaker) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = speakerColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        firstEntry.speakerLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = speakerColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    formatTimestampDetail(firstEntry.timestampMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(Modifier.height(4.dp))
        } else {
            // Smaller indicator for same speaker after a gap
            Text(
                formatTimestampDetail(firstEntry.timestampMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        group.forEach { entry -> Text(entry.text, style = MaterialTheme.typography.bodyMedium) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAttendeeDialog(
    allContacts: List<Contact>,
    currentAttendeeIds: Set<String>,
    onDismiss: () -> Unit,
    onAddAttendee: (Contact) -> Unit
) {
    val availableContacts = allContacts.filter { it.contactUserId !in currentAttendeeIds }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Attendee") },
        text = {
            if (availableContacts.isEmpty()) {
                Text(
                    if (allContacts.isEmpty()) {
                        "You don't have any contacts yet. Add some from the Contacts tab first."
                    } else {
                        "Everyone in your contacts has already been added."
                    },
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(availableContacts, key = { it.id }) { contact ->
                        Text(
                            contact.username,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAddAttendee(contact) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

private fun formatDurationForHeader(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatTimestampDetail(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}
