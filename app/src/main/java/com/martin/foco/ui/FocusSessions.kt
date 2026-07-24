package com.martin.foco.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.martin.foco.R
import com.martin.foco.data.FocusSession
import com.martin.foco.data.minuteOfDayLabel
import com.martin.foco.ui.theme.FocusWarm
import com.martin.foco.ui.theme.OnFocusWarm
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Firm full-screen block (on the [BlockScreen] scaffold) when trying to open a distracting app
 * during an active focus session. No countdown and no escape hatch — the pause is on purpose.
 */
@Composable
fun FocusBlockDialog(
    appLabel: String,
    onDismiss: () -> Unit,
) {
    BlockScreen(
        kicker = stringResource(R.string.focus_kicker),
        appLabel = appLabel,
        onBack = onDismiss,
    ) {
        BlockBody(stringResource(R.string.focus_block_body))
        Spacer(Modifier.weight(1f))
        BlockPrimaryAction(stringResource(R.string.common_done), onClick = onDismiss)
    }
}

/**
 * Quick focus control as a full-screen surface on the [BlockScreen] scaffold — same brand language
 * as the friction/limit/focus block screens (breathing warm glow, thin display type, tracked
 * kicker). Depending on state it starts manual focus for a duration, resumes a skipped scheduled
 * session, or exits the current focus. Back dismisses.
 */
@Composable
fun FocusControlScreen(
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
    BlockScreen(
        kicker = stringResource(R.string.focus_control_kicker),
        appLabel = stringResource(if (active) R.string.focus_control_active else R.string.focus_control_start),
        onBack = onDismiss,
    ) {
        when {
            // Strict mode: locked until it ends — no exit, just acknowledge and close.
            active && locked -> {
                BlockBody(stringResource(R.string.focus_locked_msg, untilLabel ?: ""))
                Spacer(Modifier.weight(1f))
                BlockPrimaryAction(stringResource(R.string.common_done), onClick = onDismiss)
            }
            // Active focus: exit is the (destructive) prominent action; staying is the quiet one.
            active -> {
                if (untilLabel != null) BlockBody(stringResource(R.string.home_focus_indicator, untilLabel))
                Spacer(Modifier.weight(1f))
                BlockPrimaryAction(stringResource(R.string.focus_control_exit), destructive = true, onClick = onStop)
                BlockQuietAction(stringResource(R.string.focus_keep), onClick = onDismiss)
            }
            // Inactive: pick a duration (giant warm number + slider) and start.
            else -> {
                var mins by remember { mutableIntStateOf(30) }
                Spacer(Modifier.height(8.dp))
                Text(
                    mins.toString(),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 88.sp,
                        fontWeight = FontWeight.Light,
                    ),
                    color = FocusWarm,
                )
                Text(
                    stringResource(R.string.focus_min_label).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(28.dp))
                Box(Modifier.width(240.dp)) {
                    MinimalSlider(mins, 5, 120) { mins = it }
                }
                Spacer(Modifier.weight(1f))
                BlockPrimaryAction(stringResource(R.string.focus_start_dur, mins), onClick = { onStart(mins) })
                if (allowIndefinite) BlockQuietAction(stringResource(R.string.focus_dur_until_off)) { onStart(null) }
                if (canResume) BlockQuietAction(stringResource(R.string.focus_resume_scheduled), onClick = onResume)
            }
        }
    }
}

/** Editor for a focus session: start/end times (spin wheels) and active weekdays. */
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TimeWheel(stringResource(R.string.focus_start), start) { start = it }
            TimeWheel(stringResource(R.string.focus_end), end) { end = it }
        }
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

/** Start/end time as two spin wheels (hour + 5-min steps) under a small label. */
@Composable
private fun TimeWheel(label: String, value: Int, onChange: (Int) -> Unit) {
    val hour = value / 60
    val minute = value % 60
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WheelPicker(24, hour, { h -> onChange(h * 60 + minute) }, { "%02d".format(it) }, Modifier.width(46.dp))
            Text(
                ":",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            WheelPicker(12, minute / 5, { m -> onChange(hour * 60 + m * 5) }, { "%02d".format(it * 5) }, Modifier.width(46.dp))
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
                color = if (selected) OnFocusWarm else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) FocusWarm else Color.Transparent)
                    .clickableText { onToggle(d) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}
