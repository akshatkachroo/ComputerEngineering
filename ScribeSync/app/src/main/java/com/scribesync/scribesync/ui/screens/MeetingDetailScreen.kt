package com.scribesync.scribesync.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribesync.scribesync.data.ActionItem
import com.scribesync.scribesync.data.Meeting
import com.scribesync.scribesync.data.TranscriptEntry
import com.scribesync.scribesync.ui.viewmodel.MeetingViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MeetingDetailScreen(
    viewModel: MeetingViewModel,
    meetingId: String,
    onBack: () -> Unit
) {
    val meetings by viewModel.repository.allMeetings.collectAsState(initial = emptyList())
    val meeting = meetings.find { it.id == meetingId }
    val transcript by viewModel.repository.getTranscript(meetingId).collectAsState(initial = emptyList())
    val actionItems by viewModel.repository.getActionItems(meetingId).collectAsState(initial = emptyList())

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var showAddActionDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var editingItemForDate by remember { mutableStateOf<ActionItem?>(null) }
    
    var newTitle by remember { mutableStateOf(meeting?.title ?: "") }
    var newTag by remember { mutableStateOf("") }
    var newActionText by remember { mutableStateOf("") }
    var selectedDueDate by remember { mutableStateOf<Long?>(null) }

    var showConfetti by remember { mutableStateOf(false) }
    
    val confirmedItems = actionItems.filter { it.isConfirmed }
    val completedCount = confirmedItems.count { it.isCompleted }
    val totalCount = confirmedItems.size
    val allDone = totalCount > 0 && completedCount == totalCount
    
    LaunchedEffect(allDone) {
        if (allDone) {
            showConfetti = true
        }
    }

    if (meeting == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Meeting not found")
        }
        return
    }

    // Dialogs...
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

    if (showDatePicker || editingItemForDate != null) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = editingItemForDate?.dueDate?.time ?: selectedDueDate
        )
        
        DatePickerDialog(
            onDismissRequest = { 
                showDatePicker = false
                editingItemForDate = null
            },
            confirmButton = {
                TextButton(onClick = {
                    val date = datePickerState.selectedDateMillis?.let { Date(it) }
                    if (editingItemForDate != null) {
                        viewModel.updateActionItem(editingItemForDate!!.copy(dueDate = date))
                    } else {
                        selectedDueDate = datePickerState.selectedDateMillis
                    }
                    showDatePicker = false
                    editingItemForDate = null
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDatePicker = false
                    editingItemForDate = null
                }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showAddActionDialog) {
        AlertDialog(
            onDismissRequest = { 
                showAddActionDialog = false
                selectedDueDate = null
            },
            title = { Text("New Action Item") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newActionText,
                        onValueChange = { newActionText = it },
                        label = { Text("What needs to be done?") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedCard(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Event, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = if (selectedDueDate != null) {
                                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(selectedDueDate!!))
                                } else {
                                    "Set Due Date (Optional)"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newActionText.isNotBlank()) {
                        val dueDate = selectedDueDate?.let { Date(it) }
                        viewModel.addManualActionItem(meetingId, newActionText.trim(), dueDate)
                    }
                    newActionText = ""
                    selectedDueDate = null
                    showAddActionDialog = false
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showAddActionDialog = false 
                    selectedDueDate = null
                }) {
                    Text("Cancel")
                }
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

    Box(modifier = Modifier.fillMaxSize()) {
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
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Title")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    MeetingHeader(meeting = meeting)
                }

                if (totalCount > 0) {
                    item {
                        ActionProgress(completed = completedCount, total = totalCount)
                    }
                }

                item {
                    Text(
                        "ACTION ITEMS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        letterSpacing = 1.sp
                    )
                }

                items(actionItems, key = { it.id }) { item ->
                    ActionItemRow(
                        item = item,
                        onToggle = { viewModel.toggleActionItemComplete(item) },
                        onConfirm = { viewModel.confirmActionItem(item) },
                        onDismiss = { viewModel.deleteActionItem(item.id) },
                        onEditDate = { editingItemForDate = item }
                    )
                }

                if (allDone) {
                    item {
                        SuccessCard()
                    }
                }

                item {
                    TextButton(
                        onClick = { showAddActionDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add Item")
                        }
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

                if (!meeting.summary.isNullOrEmpty()) {
                    item {
                        SummarySection(summary = meeting.summary)
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }

                item {
                    Text("Transcript", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                    items(transcript) { entry ->
                        TranscriptDetailItem(entry = entry)
                    }
                }
            }
        }

        if (showConfetti) {
            ConfettiOverlay(onFinished = { showConfetti = false })
        }
    }
}

@Composable
fun MeetingHeader(meeting: Meeting) {
    val dateFormat = SimpleDateFormat("EEEE 'at' h:mm a", Locale.getDefault())
    Column {
        Text(
            meeting.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Today at ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(meeting.date)} · ${formatDuration(meeting.durationSeconds)} recording",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun ActionProgress(completed: Int, total: Int) {
    val progress by animateFloatAsState(
        targetValue = if (total > 0) completed.toFloat() / total.toFloat() else 0f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "progress"
    )
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(CircleShape),
            color = Color(0xFF4CAF50),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Text(
            "$completed / $total",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionItemRow(
    item: ActionItem,
    onToggle: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onEditDate: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState()
    var isExpanded by remember { mutableStateOf(false) }
    
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDismiss()
        }
    }

    if (!item.isConfirmed) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "AI Suggestion",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    item.text, 
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { isExpanded = !isExpanded }
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) {
                        Text("Dismiss", color = MaterialTheme.colorScheme.outline)
                    }
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onConfirm()
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    } else {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                val color = when (dismissState.dismissDirection) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    else -> Color.Transparent
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.medium)
                        .background(color)
                        .padding(horizontal = 16.dp)
                )
            },
            enableDismissFromStartToEnd = false
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedCheckbox(
                        checked = item.isCompleted,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggle()
                        }
                    )
                    
                    Spacer(Modifier.width(12.dp))
                    
                    val textColor by animateColorAsState(
                        targetValue = if (item.isCompleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                        label = "textColor"
                    )
                    
                    val alpha by animateFloatAsState(
                        targetValue = if (item.isCompleted) 0.6f else 1f,
                        label = "alpha"
                    )

                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor,
                            modifier = Modifier
                                .weight(1f)
                                .alpha(alpha)
                                .clickable { isExpanded = !isExpanded },
                            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                            maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        // Connecting line and label
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onEditDate() }
                        ) {
                            if (item.dueDate != null) {
                                Text(
                                    text = SimpleDateFormat("MMM d", Locale.getDefault()).format(item.dueDate),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (item.isCompleted) MaterialTheme.colorScheme.outline else Color.Red,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            } else if (!item.isCompleted) {
                                Icon(
                                    Icons.Default.Event,
                                    contentDescription = "Set due date",
                                    modifier = Modifier.size(16.dp).padding(end = 8.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(32.dp)
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                                    .alpha(if (item.isCompleted) 0.3f else 1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "You",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuccessCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1B5E20).copy(alpha = 0.1f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "All done!",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun AnimatedCheckbox(
    checked: Boolean,
    onCheckedChange: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (checked) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "checkboxScale"
    )

    Box(
        modifier = Modifier
            .size(28.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (checked) Color(0xFF4CAF50) else Color.Transparent)
            .clickable { onCheckedChange() }
            .then(
                if (!checked) Modifier.background(
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    CircleShape
                ) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Complete",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        } else {
            // Empty circle or subtle ring
        }
    }
}

@Composable
fun ConfettiOverlay(onFinished: () -> Unit) {
    val particles = remember { List(50) { ConfettiParticle() } }
    val progress = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2500, easing = LinearOutSlowInEasing)
        )
        onFinished()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEach { particle ->
            val p = progress.value
            if (p < 1f) {
                val angle = particle.angle * (Math.PI / 180f)
                val distance = particle.speed * p * 800f
                
                val x = size.width / 2 + Math.cos(angle).toFloat() * distance
                val y = size.height / 3 + Math.sin(angle).toFloat() * distance + (p * p * 500f)
                
                drawRect(
                    color = particle.color,
                    topLeft = Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(8.dp.toPx(), 4.dp.toPx()),
                    alpha = 1f - p
                )
            }
        }
    }
}

class ConfettiParticle {
    val angle = Random.nextFloat() * 360f
    val speed = Random.nextFloat() * 1.2f + 0.3f
    val color = listOf(Color.Cyan, Color.Magenta, Color.Yellow, Color.Green, Color.Red, Color.Blue).random()
}

@Composable
private fun SummarySection(summary: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "AI Summary",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun TranscriptDetailItem(entry: TranscriptEntry) {
    val speakerColor = when (entry.speakerLabel) {
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
                    entry.speakerLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = speakerColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                formatTimestamp(entry.timestampMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            entry.text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
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
