package com.martin.foco.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.martin.foco.R
import com.martin.foco.ui.theme.FocusWarm
import com.martin.foco.util.formatDuration
import java.util.Locale

/** Upper bound of the daily-limit slider, in minutes (1-minute steps from 0). */
private const val LIMIT_MAX_MIN = 120

/**
 * Picker for an app's daily time limit: a giant warm number driven by a slider, the same way a
 * manual focus duration is chosen in [FocusControlScreen]. Sliding all the way down to 0 means
 * "no limit". The value is only committed on "done", so dragging doesn't write to disk per frame.
 */
@Composable
fun TimeLimitDialog(
    appLabel: String,
    currentMinutes: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by remember { mutableIntStateOf(currentMinutes ?: 0) }
    val off = minutes == 0
    MinimalDialog(onDismiss) {
        DialogTitle(stringResource(R.string.limit_dialog_title, appLabel))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(18.dp))
            Text(
                if (off) "—" else minutes.toString(),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Light,
                ),
                color = if (off) MaterialTheme.colorScheme.onSurfaceVariant else FocusWarm,
            )
            Text(
                stringResource(if (off) R.string.limit_off else R.string.focus_min_label)
                    .uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            MinimalSlider(minutes, 0, LIMIT_MAX_MIN) { minutes = it }
        }
        DialogActions {
            DialogButton(stringResource(R.string.common_cancel), emphasized = false, onClick = onDismiss)
            DialogButton(stringResource(R.string.common_done)) { onSelect(minutes.takeIf { it > 0 }) }
        }
    }
}

/**
 * Heads-up shortly before a daily limit runs out, so the cut isn't a surprise. Same scaffold as the
 * block it precedes, with the remaining minutes as the giant warm number — deliberately *not* a
 * live countdown: the app is paused while this screen is up, so its usage isn't running down and a
 * ticking number would be lying. Leaving early is the prominent action; going back is the quiet one.
 */
@Composable
fun LimitWarningDialog(
    appLabel: String,
    remainingMinutes: Int,
    onContinue: () -> Unit,
    onLeave: () -> Unit,
) {
    BlockScreen(
        kicker = stringResource(R.string.limit_kicker),
        appLabel = appLabel,
        onBack = onContinue,   // back dismisses the notice, it isn't a block
    ) {
        BlockBody(stringResource(R.string.limit_warn_body))
        Spacer(Modifier.weight(1f))
        BlockCountdown(remainingMinutes)
        Text(
            stringResource(R.string.focus_min_label).uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        BlockPrimaryAction(stringResource(R.string.limit_warn_leave), onClick = onLeave)
        BlockQuietAction(stringResource(R.string.limit_warn_continue), onClick = onContinue)
    }
}

/**
 * Shown full-screen (on the [BlockScreen] scaffold) when opening an app that already hit its daily
 * limit. A firm block: once the limit is reached the app can't be opened again today — no countdown
 * and no escape hatch, only going back.
 */
@Composable
fun LimitReachedDialog(
    appLabel: String,
    usedTodayMs: Long,
    limitMinutes: Int,
    onDismiss: () -> Unit,
) {
    BlockScreen(
        kicker = stringResource(R.string.limit_kicker),
        appLabel = appLabel,
        onBack = onDismiss,
    ) {
        BlockBody(stringResource(R.string.limit_reached_body, formatDuration(usedTodayMs), limitMinutes))
        BlockBody(stringResource(R.string.limit_reached_hint), topPadding = 18)
        Spacer(Modifier.weight(1f))
        BlockPrimaryAction(stringResource(R.string.limit_reached_back), onClick = onDismiss)
    }
}
