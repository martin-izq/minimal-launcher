package com.martin.foco.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.martin.foco.R
import com.martin.foco.util.formatDuration
import kotlinx.coroutines.delay

/**
 * Friction screen: an intentional full-screen pause before opening a distracting app, on the
 * [BlockScreen] scaffold. Shows today's usage in the app (and its share of today's screen time,
 * when known) and forces a short wait — a giant warm countdown — before "open anyway" enables.
 * The healthy exit ("not now") is the prominent action.
 *
 * The prompt itself rotates (see [FrictionMessages]) and its tone follows today's usage, so the
 * screen doesn't turn into wallpaper after a few days of seeing the same sentence.
 */
@Composable
fun FrictionDialog(
    appLabel: String,
    usedTodayMs: Long,
    seconds: Int,
    onProceed: () -> Unit,
    onDismiss: () -> Unit,
    totalTodayMs: Long = 0L,
) {
    val tone = remember(usedTodayMs) { frictionToneFor(usedTodayMs) }
    val lines = stringArrayResource(tone.linesRes)
    // Saveable so a rotation mid-countdown doesn't swap the sentence under the user.
    val lineIndex = rememberSaveable(tone) { FrictionMessages.next(tone, lines.size) }

    var remaining by remember { mutableIntStateOf(seconds) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining -= 1
        }
    }
    BlockScreen(
        kicker = stringResource(R.string.friction_kicker),
        appLabel = appLabel,
        onBack = onDismiss,
    ) {
        BlockBody(stringResource(R.string.friction_used_today, formatDuration(usedTodayMs)))
        val pct = if (totalTodayMs > 0) (usedTodayMs * 100 / totalTodayMs).toInt() else 0
        if (pct >= 1) {
            BlockBody(stringResource(R.string.friction_share_today, pct), topPadding = 4)
        }
        BlockBody(lines.getOrElse(lineIndex) { lines.first() }, topPadding = 18)
        Spacer(Modifier.weight(1f))
        BlockCountdown(remaining)
        Spacer(Modifier.weight(1f))
        BlockPrimaryAction(stringResource(R.string.block_not_now), onClick = onDismiss)
        BlockQuietAction(
            text = stringResource(R.string.friction_open_anyway),
            enabled = remaining <= 0,
            onClick = onProceed,
        )
    }
}
