package com.martin.minimallauncher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

private val LIMIT_PRESETS = listOf(5, 10, 15, 30, 60, 120)

/** Picker for an app's daily time limit (minutes). Passing null clears it. */
@Composable
fun TimeLimitDialog(
    appLabel: String,
    currentMinutes: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    MinimalDialog(onDismiss) {
        DialogTitle(stringResource(R.string.limit_dialog_title, appLabel))
        Spacer(Modifier.height(6.dp))
        LimitOption(stringResource(R.string.limit_off), currentMinutes == null) { onSelect(null) }
        LIMIT_PRESETS.forEach { m ->
            LimitOption(stringResource(R.string.limit_minutes, m), currentMinutes == m) { onSelect(m) }
        }
        DialogActions {
            DialogButton(stringResource(R.string.common_done), onClick = onDismiss)
        }
    }
}

@Composable
private fun LimitOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickableText(onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (selected) {
            Text("✓", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Shown when opening an app that already hit its daily limit. A soft block: it reflects the
 * overuse and only enables "open anyway" after a short countdown.
 */
@Composable
fun LimitReachedDialog(
    appLabel: String,
    usedTodayMs: Long,
    limitMinutes: Int,
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
    MinimalDialog(onDismiss) {
        DialogTitle(stringResource(R.string.limit_reached_title, appLabel))
        DialogBody(stringResource(R.string.limit_reached_body, formatDuration(usedTodayMs), limitMinutes))
        DialogBody(stringResource(R.string.limit_reached_hint))
        DialogActions {
            DialogButton(stringResource(R.string.limit_reached_back), emphasized = false, onClick = onDismiss)
            DialogButton(
                text = if (remaining > 0) stringResource(R.string.friction_open_countdown, remaining)
                else stringResource(R.string.friction_open_anyway),
                enabled = remaining <= 0,
                onClick = onProceed,
            )
        }
    }
}
