package com.scribesync.scribesync.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scribesync.scribesync.data.ActionItem
import com.scribesync.scribesync.data.Meeting
import com.scribesync.scribesync.ui.viewmodel.MeetingViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: MeetingViewModel,
    onBack: () -> Unit,
    onMeetingClick: (String) -> Unit
) {
    val meetings by viewModel.repository.allMeetings.collectAsState(initial = emptyList())
    val actionItems by viewModel.repository.allConfirmedActionItems.collectAsState(initial = emptyList())
    
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    val meetingsByDate = remember(meetings) {
        meetings.groupBy { meeting ->
            meeting.date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }
    
    val dueItemsByDate = remember(actionItems) {
        actionItems.filter { it.dueDate != null && !it.isCompleted }.groupBy { item ->
            item.dueDate!!.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meeting Calendar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            CalendarHeader(
                currentMonth = currentMonth,
                onMonthChange = { currentMonth = it }
            )
            
            CalendarGrid(
                currentMonth = currentMonth,
                selectedDate = selectedDate,
                meetingsByDate = meetingsByDate,
                dueItemsByDate = dueItemsByDate,
                onDateSelected = { selectedDate = it }
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "${selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy"))}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold
            )

            val notesForSelectedDate = meetingsByDate[selectedDate] ?: emptyList()
            val dueForSelectedDate = dueItemsByDate[selectedDate] ?: emptyList()
            
            if (notesForSelectedDate.isEmpty() && dueForSelectedDate.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No activity for this day", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (dueForSelectedDate.isNotEmpty()) {
                        item {
                            Text(
                                "Due Action Items",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(dueForSelectedDate) { item ->
                            DueActionItemCard(item = item)
                        }
                    }

                    if (notesForSelectedDate.isNotEmpty()) {
                        item {
                            Text(
                                "Recordings",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(notesForSelectedDate.sortedBy { it.date }) { meeting ->
                            MeetingItem(meeting = meeting, onClick = { onMeetingClick(meeting.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DueActionItemCard(item: ActionItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AssignmentTurnedIn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun CalendarHeader(
    currentMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Row {
            IconButton(onClick = { onMonthChange(currentMonth.minusMonths(1)) }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
            }
            IconButton(onClick = { onMonthChange(currentMonth.plusMonths(1)) }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
            }
        }
    }
}

@Composable
fun CalendarGrid(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    meetingsByDate: Map<LocalDate, List<Meeting>>,
    dueItemsByDate: Map<LocalDate, List<ActionItem>>,
    onDateSelected: (LocalDate) -> Unit
) {
    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfMonth = currentMonth.atDay(1).dayOfWeek.value % 7 // 0 for Sunday
    val days = (1..daysInMonth).toList()
    
    val weekDays = listOf("S", "M", "T", "W", "T", "F", "S")

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            weekDays.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth(),
            userScrollEnabled = false
        ) {
            items(firstDayOfMonth) {
                Box(modifier = Modifier.aspectRatio(1f))
            }
            
            items(days) { day ->
                val date = currentMonth.atDay(day)
                val isSelected = date == selectedDate
                val meetings = meetingsByDate[date] ?: emptyList()
                val dueItems = dueItemsByDate[date] ?: emptyList()
                
                CalendarDay(
                    day = day,
                    isSelected = isSelected,
                    meetingCount = meetings.size,
                    hasDueItems = dueItems.isNotEmpty(),
                    onClick = { onDateSelected(date) }
                )
            }
        }
    }
}

@Composable
fun CalendarDay(
    day: Int,
    isSelected: Boolean,
    meetingCount: Int,
    hasDueItems: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.toString(),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp
            )
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                // Meeting dots
                repeat(minOf(meetingCount, 2)) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 1.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                    )
                }
                // Due item indicator (square or different color)
                if (hasDueItems) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 1.dp)
                            .size(4.dp)
                            .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.Red)
                    )
                }
            }
        }
    }
}

@Composable
fun MeetingItem(meeting: Meeting, onClick: () -> Unit) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meeting.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (meeting.transcriptPreview.isNotEmpty()) {
                    Text(
                        text = meeting.transcriptPreview,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = timeFormat.format(meeting.date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
