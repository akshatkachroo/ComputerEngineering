package com.scribesync.scribesync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scribesync.scribesync.data.ActionItem
import com.scribesync.scribesync.ui.components.ActionItemRow
import com.scribesync.scribesync.ui.components.ActionProgress
import com.scribesync.scribesync.ui.viewmodel.MeetingViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    viewModel: MeetingViewModel,
    meetingId: String? = null,
    onBack: () -> Unit
) {
    val allActionItems by viewModel.repository.allConfirmedActionItems.collectAsState(initial = emptyList())
    
    val actionItems = if (meetingId != null) {
        viewModel.repository.getActionItems(meetingId).collectAsState(initial = emptyList()).value
    } else {
        allActionItems
    }
    
    val completedCount = actionItems.count { it.isCompleted }
    val totalCount = actionItems.size
    val allDone = totalCount > 0 && completedCount == totalCount
    
    var showConfetti by remember { mutableStateOf(false) }
    LaunchedEffect(allDone) {
        if (allDone) showConfetti = true
    }
    
    var editingItemForDate by remember { mutableStateOf<ActionItem?>(null) }
    var showAddActionDialog by remember { mutableStateOf(false) }
    var newActionText by remember { mutableStateOf("") }
    var selectedDueDate by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

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
        ) { DatePicker(state = datePickerState) }
    }

    if (showAddActionDialog && meetingId != null) {
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
                    OutlinedCard(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Event, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = if (selectedDueDate != null) {
                                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(selectedDueDate!!))
                                } else "Set Due Date (Optional)",
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
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showAddActionDialog = false 
                    selectedDueDate = null
                }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (meetingId != null) "Meeting Tasks" else "Task List") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (meetingId != null) {
                        IconButton(onClick = { showAddActionDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Task")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (totalCount > 0) {
                Spacer(Modifier.height(16.dp))
                ActionProgress(completed = completedCount, total = totalCount)
                Spacer(Modifier.height(16.dp))
            }

            if (actionItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tasks yet.", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
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
                            com.scribesync.scribesync.ui.components.SuccessCard()
                        }
                    }
                }
            }
        }

        if (showConfetti) {
            com.scribesync.scribesync.ui.components.ConfettiOverlay(onFinished = { showConfetti = false })
        }
    }
}
