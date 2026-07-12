package com.martin.foco.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.martin.foco.R
import com.martin.foco.util.formatDuration
import kotlinx.coroutines.delay

/**
 * Friction screen: an intentional pause before opening a distracting app. Shows today's usage and
 * forces a short wait ("take a breath") before the open button is enabled.
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
    MinimalDialog(onDismiss) {
        DialogTitle(stringResource(R.string.friction_title, appLabel))
        DialogBody(stringResource(R.string.friction_used_today, formatDuration(usedTodayMs)))
        DialogBody(stringResource(R.string.friction_breathe))
        DialogActions {
            DialogButton(stringResource(R.string.common_cancel), emphasized = false, onClick = onDismiss)
            DialogButton(
                text = if (remaining > 0) stringResource(R.string.friction_open_countdown, remaining)
                else stringResource(R.string.friction_open_anyway),
                enabled = remaining <= 0,
                onClick = onProceed,
            )
        }
    }
}
