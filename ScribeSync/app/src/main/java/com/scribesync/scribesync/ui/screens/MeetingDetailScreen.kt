package com.scribesync.scribesync.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scribesync.scribesync.data.Contact
import com.scribesync.scribesync.data.Meeting
import com.scribesync.scribesync.data.TranscriptEntry
import com.scribesync.scribesync.ui.components.OwnerBadge
import com.scribesync.scribesync.ui.components.groupConsecutiveBySpeaker
import com.scribesync.scribesync.ui.components.SyncStatusBadge
import com.scribesync.scribesync.util.SummaryModelManager
import com.scribesync.scribesync.util.SummaryService
import com.scribesync.scribesync.ui.viewmodel.AuthViewModel
import com.scribesync.scribesync.ui.viewmodel.ContactsViewModel
import com.scribesync.scribesync.ui.viewmodel.MeetingViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MeetingDetailScreen(
    viewModel: MeetingViewModel,
    meetingId: String,
    onBack: () -> Unit,
    authViewModel: AuthViewModel,
    contactsViewModel: ContactsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = ContactsViewModel.Factory)
) {
    val meetings by viewModel.repository.allMeetings.collectAsState(initial = emptyList())
    val meeting = meetings.find { it.id == meetingId }
    val transcript by viewModel.repository.getTranscript(meetingId).collectAsState(initial = emptyList())
    val groupedTranscript = remember(transcript) { groupConsecutiveBySpeaker(transcript) }
    val allContacts by contactsViewModel.contacts.collectAsState()
    val attendees = allContacts.filter { it.contactUserId in (meeting?.attendeeIds ?: emptyList()) }
    val currentUser by authViewModel.currentUser.collectAsState()
    val isOwner = meeting != null && meeting.ownerId.isNotEmpty() && meeting.ownerId == currentUser?.uid
    val summarizingMeetingId by viewModel.summarizingMeetingId.collectAsState()
    val summaryPhase by viewModel.summaryPhase.collectAsState()
    val modelDownloadState by viewModel.modelDownloadState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(meetingId, isOwner) {
        if (isOwner) {
            viewModel.repository.observeRemoteAttendeeIds(meetingId).collect { remoteIds ->
                viewModel.mergeRemoteAttendeeIds(meetingId, remoteIds)
            }
        }
    }

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
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
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
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddAttendeeDialog) {
        AddAttendeeDialog(
            allContacts = allContacts,
            currentAttendeeIds = meeting.attendeeIds.toSet(),
            onDismiss = { showAddAttendeeDialog = false },
            onAddAttendee = { contact ->
                // This sends a request rather than adding them directly -
                // they show up as an attendee only once they accept.
                viewModel.sendAttendeeRequest(meetingId, contact.contactUserId)
                showAddAttendeeDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Recording") },
            text = { Text("Are you sure you want to delete this recording? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMeeting(meetingId)
                    showDeleteDialog = false
                    onBack()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
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
                    IconButton(
                        onClick = {
                            shareMeetingExport(
                                context = context,
                                meeting = meeting,
                                transcript = transcript
                            )
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Export transcript and summary")
                    }
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Title")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                MeetingInfoSection(meeting = meeting)
            }

            if (meeting.latitude != null && meeting.longitude != null) {
                item {
                    MeetingLocationSection(
                        meeting = meeting,
                        onOpenInMaps = { openMeetingLocation(context, meeting) }
                    )
                }
            }

            item {
                Text("Tags", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    meeting.tags.forEach { tag ->
                        AssistChip(
                            onClick = { },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove tag",
                                    modifier = Modifier
                                        .size(AssistChipDefaults.IconSize)
                                        .clickable {
                                            val updatedTags = meeting.tags.filter { it != tag }
                                            viewModel.updateMeetingTags(meetingId, updatedTags)
                                        }
                                )
                            }
                        )
                    }
                    AssistChip(
                        onClick = { showAddTagDialog = true },
                        label = { Text("Add Tag") },
                        leadingIcon = {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                        }
                    )
                }
            }

            item {
                Text("Attendees", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    attendees.forEach { contact ->
                        AssistChip(
                            onClick = { },
                            label = { Text(contact.username) },
                            trailingIcon = if (isOwner) {
                                {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove attendee",
                                        modifier = Modifier
                                            .size(AssistChipDefaults.IconSize)
                                            .clickable {
                                                viewModel.updateMeetingAttendees(
                                                    meetingId,
                                                    meeting.attendeeIds.filter { it != contact.contactUserId }
                                                )
                                            }
                                    )
                                }
                            } else null
                        )
                    }
                    AssistChip(
                        onClick = { showAddAttendeeDialog = true },
                        label = { Text("Add Attendee") },
                        leadingIcon = {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                        }
                    )
                }
            }

            if (!meeting.summary.isNullOrEmpty()) {
                item {
                    SummarySection(summary = meeting.summary)
                }
            } else if (summarizingMeetingId == meetingId) {
                item {
                    SummaryInProgressSection(
                        phase = summaryPhase,
                        downloadState = modelDownloadState
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
            }

            if (transcript.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No transcript available", color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                items(groupedTranscript) { group ->
                    TranscriptDetailItem(group = group)
                }
            }
        }
    }
}

private fun shareMeetingExport(
    context: Context,
    meeting: Meeting,
    transcript: List<TranscriptEntry>
) {
    val exportText = buildMeetingExport(meeting, transcript)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "${meeting.title} transcript")
        putExtra(Intent.EXTRA_TEXT, exportText)
    }
    context.startActivity(
        Intent.createChooser(shareIntent, "Export transcript and summary")
    )
}

private fun buildMeetingExport(meeting: Meeting, transcript: List<TranscriptEntry>): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd h:mm a", Locale.getDefault())
    val cleanSummary = meeting.summary
        ?.takeIf { it.isNotBlank() }
        ?.let {
            if (it.startsWith(SummaryService.FAILED_PREFIX)) {
                "Summary generation failed: ${it.removePrefix(SummaryService.FAILED_PREFIX).trim()}"
            } else {
                it.trim()
            }
        }
        ?: "No summary available."

    val transcriptText = if (transcript.isEmpty()) {
        "No transcript available."
    } else {
        transcript.joinToString("\n") { entry ->
            "[${formatTimestamp(entry.timestampMs)}] ${entry.speakerLabel}: ${entry.text.trim()}"
        }
    }

    return buildString {
        appendLine("# ${meeting.title}")
        appendLine()
        appendLine("- Date: ${dateFormat.format(meeting.date)}")
        appendLine("- Duration: ${formatDuration(meeting.durationSeconds)}")
        appendLine("- Owner: ${meeting.ownerName}")
        if (meeting.latitude != null && meeting.longitude != null) {
            appendLine("- Location: ${formatCoordinates(meeting.latitude, meeting.longitude)}")
        }
        if (meeting.tags.isNotEmpty()) {
            appendLine("- Tags: ${meeting.tags.joinToString(", ")}")
        }
        appendLine()
        appendLine("## AI Summary")
        appendLine()
        appendLine(cleanSummary)
        appendLine()
        appendLine("## Transcript")
        appendLine()
        appendLine(transcriptText)
    }
}

private fun openMeetingLocation(context: Context, meeting: Meeting) {
    val latitude = meeting.latitude ?: return
    val longitude = meeting.longitude ?: return
    val coordinates = "$latitude,$longitude"
    val label = Uri.encode(meeting.title)
    val mapIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("geo:$coordinates?q=$coordinates($label)")
    )

    runCatching {
        context.startActivity(mapIntent)
    }.onFailure {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=$coordinates")
            )
        )
    }
}

/**
 * Shown while the on-device summary for this meeting is still being produced.
 * The first summary may be preceded by a one-time model download.
 */
@Composable
private fun SummaryInProgressSection(
    phase: SummaryService.Phase,
    downloadState: SummaryModelManager.DownloadState
) {
    val label = when {
        downloadState is SummaryModelManager.DownloadState.Downloading ->
            "Downloading summary model… ${downloadState.percent}% (one-time, ~1.1 GB)"
        downloadState is SummaryModelManager.DownloadState.Verifying ->
            "Verifying summary model…"
        phase is SummaryService.Phase.LoadingModel ->
            "Loading summary model…"
        phase is SummaryService.Phase.Generating ->
            "Generating summary on-device…"
        else -> "Preparing summary…"
    }
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            if (downloadState is SummaryModelManager.DownloadState.Downloading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { downloadState.percent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SummarySection(summary: String) {
    // A failed generation is stored with a marker prefix so it renders as an
    // explicit error state instead of masquerading as a real summary.
    val failed = summary.startsWith(SummaryService.FAILED_PREFIX)
    val body = if (failed) {
        "Summary generation failed: ${summary.removePrefix(SummaryService.FAILED_PREFIX).trim()}"
    } else {
        summary
    }
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (failed) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (failed) {
                        androidx.compose.material.icons.Icons.Default.Warning
                    } else {
                        androidx.compose.material.icons.Icons.Default.Edit
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (failed) "AI Summary Unavailable" else "AI Summary",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (failed) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
            )
        }
    }
}

@Composable
private fun MeetingInfoSection(meeting: com.scribesync.scribesync.data.Meeting) {
    val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.getDefault())
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            meeting.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            dateFormat.format(meeting.date),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Duration: ${formatDuration(meeting.durationSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.width(8.dp))
            SyncStatusBadge(isSynced = meeting.isSynced)
        }
        Spacer(Modifier.height(4.dp))
        OwnerBadge(ownerName = meeting.ownerName)
    }
}

@Composable
private fun MeetingLocationSection(
    meeting: Meeting,
    onOpenInMaps: () -> Unit
) {
    val latitude = meeting.latitude ?: return
    val longitude = meeting.longitude ?: return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Recorded location",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                formatCoordinates(latitude, longitude),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        IconButton(onClick = onOpenInMaps) {
            Icon(Icons.Default.Map, contentDescription = "Open recorded location in maps")
        }
    }
}

@Composable
private fun TranscriptDetailItem(group: List<TranscriptEntry>) {
    val firstEntry = group.first()
    val speakerColor = when (firstEntry.speakerLabel) {
        "Speaker 1" -> MaterialTheme.colorScheme.primary
        "Speaker 2" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }

    Column(modifier = Modifier.fillMaxWidth()) {
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
                formatTimestamp(firstEntry.timestampMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Spacer(Modifier.height(4.dp))
        Column {
            group.forEach { entry ->
                Text(
                    entry.text,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
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

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatTimestamp(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

private fun formatCoordinates(latitude: Double, longitude: Double): String {
    return String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
}
