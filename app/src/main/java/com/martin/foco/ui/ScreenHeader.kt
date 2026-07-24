package com.martin.foco.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Base distance (dp) between the status bar inset and the first content of titled screens
 * (settings, screen time, app drawer) so they don't start cramped against the top edge.
 * The stable status-bar padding on each screen already reserves the bar's height, so this
 * is only the breathing room below it — keep it small or titles sit too low.
 */
const val SCREEN_TOP_OFFSET_DP = 12

/** Back chevron + title row used by the modal overlay screens. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = SCREEN_TOP_OFFSET_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "‹",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .clickableText(onBack)
                .padding(end = 16.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Start,
        )
    }
}
