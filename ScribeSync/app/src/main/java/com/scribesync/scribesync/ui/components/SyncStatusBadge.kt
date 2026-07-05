package com.scribesync.scribesync.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Single reused glyph for cloud sync state, so the offline-first story reads
 * the same way in the list, the calendar, and the detail screen.
 */
@Composable
fun SyncStatusBadge(isSynced: Boolean, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isSynced) Icons.Default.Cloud else Icons.Default.CloudOff,
            contentDescription = if (isSynced) "Synced to cloud" else "Local only",
            modifier = Modifier.size(14.dp),
            tint = if (isSynced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
        if (showLabel) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (isSynced) "Synced" else "Local only",
                style = MaterialTheme.typography.labelSmall,
                color = if (isSynced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
    }
}
