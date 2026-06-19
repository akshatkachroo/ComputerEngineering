package com.scribesync.scribesync.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.scribesync.scribesync.data.TranscriptEntry
import com.scribesync.scribesync.ui.viewmodel.MeetingViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    viewModel: MeetingViewModel,
    meetingTitle: String,
    onStopRecording: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val transcriptEntries by viewModel.transcript.collectAsState()
    
    val context = LocalContext.current
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudioPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var isRecording by remember { mutableStateOf(false) }

    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission) {
            isRecording = true
            viewModel.startMeeting(meetingTitle)
        }
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(1000L)
            elapsedSeconds++
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(transcriptEntries.size) {
        if (transcriptEntries.isNotEmpty()) {
            listState.animateScrollToItem(transcriptEntries.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(meetingTitle, fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = {
                        isRecording = false
                        viewModel.stopMeeting()
                        onStopRecording()
                    }) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(4.dp))
                        Text("Stop", color = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            RecordingStatusHeader(elapsedSeconds = elapsedSeconds, isRecording = isRecording)
            HorizontalDivider()
            
            // Show current state overlay if needed
            if (uiState != MeetingViewModel.MeetingUiState.ActiveRecording && isRecording) {
                Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    Text(uiState.toString(), style = MaterialTheme.typography.labelSmall)
                }
            }

            if (!hasAudioPermission) {
                PermissionDeniedMessage(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(transcriptEntries) { entry ->
                        TranscriptEntryItem(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingStatusHeader(elapsedSeconds: Int, isRecording: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.5f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .scale(pulseScale)
                .background(
                    color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                )
        )
        Spacer(Modifier.width(12.dp))
        Text(
            formatDuration(elapsedSeconds),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(12.dp))
        Text(
            if (isRecording) "LIVE" else "WAITING",
            style = MaterialTheme.typography.labelMedium,
            color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TranscriptEntryItem(entry: TranscriptEntry) {
    val speakerColor = when (entry.speakerLabel) {
        "Speaker 1" -> MaterialTheme.colorScheme.primary
        "Speaker 2" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = speakerColor.copy(alpha = 0.15f),
            modifier = Modifier.padding(top = 2.dp)
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
            entry.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PermissionDeniedMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Microphone access required", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(8.dp))
        Text(
            "Please grant microphone permission in Settings to record meetings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
