package com.martin.minimallauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.martin.minimallauncher.R
import com.martin.minimallauncher.data.FocusSession
import com.martin.minimallauncher.data.minuteOfDayLabel
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

/** Soft block shown when opening a distracting app during an active focus session. */
@Composable
fun FocusBlockDialog(
    appLabel: String,
    untilLabel: String,
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
        title = { Text(stringResource(R.string.focus_block_title, appLabel)) },
        text = {
            Text(
                stringResource(R.string.focus_block_body, untilLabel),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onProceed, enabled = remaining <= 0) {
                Text(
                    if (remaining > 0) stringResource(R.string.friction_open_countdown, remaining)
                    else stringResource(R.string.friction_open_anyway)
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.limit_reached_back)) } },
    )
}

/** Editor for a focus session: start/end times (15-min steppers) and active weekdays. */
@Composable
fun FocusSessionEditorDialog(
    session: FocusSession,
    isNew: Boolean,
    onSave: (FocusSession) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var start by remember { mutableIntStateOf(session.start) }
    var end by remember { mutableIntStateOf(session.end) }
    var days by remember { mutableStateOf(session.days) }
    val valid = end > start && days.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.focus_session_edit)) },
        text = {
            Column {
                TimeStepper(stringResource(R.string.focus_start), start) { start = it }
                Spacer(Modifier.height(8.dp))
                TimeStepper(stringResource(R.string.focus_end), end) { end = it }
                Spacer(Modifier.height(16.dp))
                DayChips(days) { d -> days = if (d in days) days - d else days + d }
                if (!valid) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.focus_invalid),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(session.copy(start = start, end = end, days = days)) },
                enabled = valid,
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            Row {
                if (!isNew) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.common_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
}

/** Formats a session for a settings row, e.g. "09:00–18:00 · M T W". */
fun focusSessionLabel(session: FocusSession): String {
    val locale = Locale.getDefault()
    val days = (1..7)
        .filter { it in session.days }
        .joinToString(" ") { DayOfWeek.of(it).getDisplayName(TextStyle.NARROW, locale) }
    return "${minuteOfDayLabel(session.start)}–${minuteOfDayLabel(session.end)}  ·  $days"
}

private fun step(minute: Int, delta: Int): Int = (((minute + delta) % 1440) + 1440) % 1440

@Composable
private fun TimeStepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickableText { onChange(step(value, -15)) }.padding(horizontal = 10.dp),
            )
            Text(
                minuteOfDayLabel(value),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickableText { onChange(step(value, 15)) }.padding(horizontal = 10.dp),
            )
        }
    }
}

@Composable
private fun DayChips(days: Set<Int>, onToggle: (Int) -> Unit) {
    val locale = Locale.getDefault()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..7).forEach { d ->
            val selected = d in days
            Text(
                DayOfWeek.of(d).getDisplayName(TextStyle.NARROW, locale),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickableText { onToggle(d) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}
