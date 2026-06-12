package com.scribsync.scribsync.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Button
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.scribsync.scribsync.data.MockData
import com.scribsync.scribsync.data.TranscriptEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(onStopRecording: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudioPermission = granted }

    var isRecording by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var showSaveSheet by remember { mutableStateOf(false) }
    var meetingTitle by remember {
        mutableStateOf("Meeting — ${SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date())}")
    }

    val transcriptEntries = remember { mutableStateListOf<TranscriptEntry>() }
    val listState = rememberLazyListState()
    var simulatedIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(1000L)
            elapsedSeconds++
        }
    }

    LaunchedEffect(isRecording) {
        while (isRecording && simulatedIndex < MockData.simulatedRecordingLines.size) {
            delay(3000L)
            if (isRecording) {
                val (speaker, text) = MockData.simulatedRecordingLines[simulatedIndex++]
                transcriptEntries.add(TranscriptEntry(speaker, text, elapsedSeconds * 1000L))
            }
        }
    }

    LaunchedEffect(transcriptEntries.size) {
        if (transcriptEntries.isNotEmpty()) {
            listState.animateScrollToItem(transcriptEntries.size - 1)
        }
    }

    fun stopAndSave() {
        isRecording = false
        showSaveSheet = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Recording", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = {
                        if (isRecording || transcriptEntries.isNotEmpty()) stopAndSave()
                        else onStopRecording()
                    }) {
                        Text("Cancel")
                    }
                },
                actions = {
                    if (isRecording || transcriptEntries.isNotEmpty()) {
                        IconButton(onClick = ::stopAndSave) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Save recording",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (!isRecording && transcriptEntries.isEmpty()) {
            IdleContent(
                modifier = Modifier.padding(padding).fillMaxSize(),
                hasPermission = hasAudioPermission,
                onTap = {
                    if (hasAudioPermission) isRecording = true
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            )
        } else {
            ActiveContent(
                modifier = Modifier.padding(padding).fillMaxSize(),
                transcriptEntries = transcriptEntries,
                listState = listState,
                elapsedSeconds = elapsedSeconds,
                isRecording = isRecording,
                onMicTap = {
                    if (isRecording) stopAndSave() else isRecording = true
                }
            )
        }
    }

    if (showSaveSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSaveSheet = false },
            sheetState = sheetState
        ) {
            SaveSheet(
                title = meetingTitle,
                onTitleChange = { meetingTitle = it },
                onSave = {
                    scope.launch {
                        sheetState.hide()
                        showSaveSheet = false
                        onStopRecording()
                    }
                },
                onDiscard = {
                    scope.launch {
                        sheetState.hide()
                        showSaveSheet = false
                        onStopRecording()
                    }
                }
            )
        }
    }
}

@Composable
private fun IdleContent(
    modifier: Modifier = Modifier,
    hasPermission: Boolean,
    onTap: () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "ScribeSync",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "On-device · Private · Offline",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(64.dp))

        MicButton(isRecording = false, onClick = onTap)

        Spacer(Modifier.height(28.dp))
        Text(
            if (hasPermission) "Tap to start recording" else "Tap to grant microphone access",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ActiveContent(
    modifier: Modifier = Modifier,
    transcriptEntries: List<TranscriptEntry>,
    listState: LazyListState,
    elapsedSeconds: Int,
    isRecording: Boolean,
    onMicTap: () -> Unit
) {
    Column(modifier) {
        RecordingStatusBar(elapsedSeconds = elapsedSeconds, isRecording = isRecording)
        HorizontalDivider()

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(transcriptEntries) { entry ->
                TranscriptEntryItem(entry = entry)
            }
        }

        HorizontalDivider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MicButton(isRecording = isRecording, onClick = onMicTap)
            Spacer(Modifier.height(12.dp))
            Text(
                if (isRecording) "Tap to stop" else "Tap to resume",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun RecordingStatusBar(elapsedSeconds: Int, isRecording: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot_blink")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "dot_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .alpha(if (isRecording) dotAlpha else 0.2f)
                .background(MaterialTheme.colorScheme.error, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (isRecording) "LIVE" else "PAUSED",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.weight(1f))
        Text(
            formatDuration(elapsedSeconds),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MicButton(isRecording: Boolean, onClick: () -> Unit) {
    val ring1 = remember { Animatable(0f) }
    val ring2 = remember { Animatable(0f) }
    val ring3 = remember { Animatable(0f) }

    LaunchedEffect(isRecording) {
        if (!isRecording) {
            ring1.snapTo(0f); ring2.snapTo(0f); ring3.snapTo(0f)
            return@LaunchedEffect
        }
        launch {
            while (isActive) {
                ring1.animateTo(1f, tween(1400, easing = LinearEasing))
                ring1.snapTo(0f)
            }
        }
        launch {
            delay(467)
            while (isActive) {
                ring2.animateTo(1f, tween(1400, easing = LinearEasing))
                ring2.snapTo(0f)
            }
        }
        launch {
            delay(934)
            while (isActive) {
                ring3.animateTo(1f, tween(1400, easing = LinearEasing))
                ring3.snapTo(0f)
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val buttonColor = if (isRecording) errorColor else primaryColor

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(160.dp)
    ) {
        if (isRecording) {
            listOf(ring1.value, ring2.value, ring3.value).forEach { v ->
                Box(
                    Modifier
                        .size(120.dp)
                        .scale(1f + v * 0.8f)
                        .alpha((1f - v) * 0.45f)
                        .background(errorColor, CircleShape)
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = buttonColor,
            onClick = onClick,
            modifier = Modifier.size(96.dp),
            shadowElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) "Stop recording" else "Start recording",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
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
            color = speakerColor.copy(alpha = 0.12f),
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
private fun SaveSheet(
    title: String,
    onTitleChange: (String) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Save Recording",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Give this meeting a name before saving.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Meeting title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Recording")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onDiscard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Discard", color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
