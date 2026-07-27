package com.scribesync.scribesync.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import com.scribesync.scribesync.data.ActionItem
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.random.Random

@Composable
fun TasksButton(taskCount: Int = 0, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Tasks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            if (taskCount > 0) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = taskCount.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
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
fun SuccessCard(text: String = "All done!") {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.1f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF4CAF50), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ConfettiOverlay(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val particles = remember { List(50) { ConfettiParticle() } }
    
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(2500, easing = LinearOutSlowInEasing))
        onFinished()
    }
    
    Canvas(Modifier.fillMaxSize()) {
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
        }
    }
}
