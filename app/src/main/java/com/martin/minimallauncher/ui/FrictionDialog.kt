package com.martin.minimallauncher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.martin.minimallauncher.R
import com.martin.minimallauncher.util.formatDuration
import kotlinx.coroutines.delay

/**
 * Friction screen: an intentional pause before opening a distracting app. Shows today's
 * usage and forces a short wait ("take a breath") before the open button is enabled.
 */
@Composable
fun FrictionDialog(
    appLabel: String,
    usedTodayMs: Long,
    seconds: Int,
    onProceed: () -> Unit,
    onDismiss: () -> Unit,
) {
    var remaining by remember { mutableIntStateOf(seconds) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining -= 1
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.friction_title, appLabel)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.friction_used_today, formatDuration(usedTodayMs)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.friction_breathe),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onProceed, enabled = remaining <= 0) {
                    Text(
                        if (remaining > 0) stringResource(R.string.friction_open_countdown, remaining)
                        else stringResource(R.string.friction_open_anyway)
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
