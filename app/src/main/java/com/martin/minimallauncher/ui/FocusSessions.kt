package com.martin.minimallauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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

/** Firm block shown when trying to open a distracting app during an active focus session. */
@Composable
fun FocusBlockDialog(
    appLabel: String,
    onDismiss: () -> Unit,
) {
    MinimalDialog(onDismiss) {
        DialogTitle(stringResource(R.string.focus_block_title, appLabel))
        DialogBody(stringResource(R.string.focus_block_body))
        DialogActions {
            DialogButton(stringResource(R.string.common_done), onClick = onDismiss)
        }
    }
}

/**
 * Quick focus control as a minimal bottom sheet (matching the app-options sheet): start manual
 * focus for a duration, resume a skipped scheduled session, or exit the current focus. Dismiss by
 * swiping down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusControlSheet(
    active: Boolean,
    locked: Boolean,
    allowIndefinite: Boolean,
    canResume: Boolean,
    untilLabel: String?,
    onStart: (minutes: Int?) -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(if (active) R.string.focus_control_active else R.string.focus_control_start),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 8.dp),
            )
            when {
                // Strict mode: locked until it ends — no exit.
                active && locked -> Text(
                    stringResource(R.string.focus_locked_msg, untilLabel ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 12.dp),
                )
                active -> FocusSheetItem(stringResource(R.string.focus_control_exit), destructive = true) { onStop() }
                else -> {
                    if (canResume) FocusSheetItem(stringResource(R.string.focus_resume_scheduled)) { onResume() }
                    var mins by remember { mutableIntStateOf(30) }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.focus_duration),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                stringResource(R.string.focus_minutes_value, mins),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        MinimalSlider(mins, 5, 120) { mins = it }
                    }
                    FocusSheetItem(stringResource(R.string.focus_start_dur, mins)) { onStart(mins) }
                    if (allowIndefinite) FocusSheetItem(stringResource(R.string.focus_dur_until_off)) { onStart(null) }
                }
            }
        }
    }
}

@Composable
private fun FocusSheetItem(text: String, destructive: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickableText(onClick)
            .padding(horizontal = 28.dp, vertical = 14.dp),
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

    MinimalDialog(onDismiss) {
        DialogTitle(stringResource(R.string.focus_session_edit))
        Spacer(Modifier.height(18.dp))
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
        DialogActions {
            if (!isNew) DialogButton(stringResource(R.string.common_remove), destructive = true, onClick = onDelete)
            DialogButton(stringResource(R.string.common_cancel), emphasized = false, onClick = onDismiss)
            DialogButton(
                stringResource(R.string.common_save),
                enabled = valid,
                onClick = { onSave(session.copy(start = start, end = end, days = days)) },
            )
        }
    }
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
