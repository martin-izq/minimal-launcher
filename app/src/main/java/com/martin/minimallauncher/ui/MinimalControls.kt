package com.martin.minimallauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

/** Minimal dialog container: a rounded near-black card, no Material chrome. */
@Composable
fun MinimalDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 24.dp, vertical = 22.dp),
            content = content,
        )
    }
}

@Composable
fun DialogTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
fun DialogBody(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp),
    )
}

/** Right-aligned action row for dialog buttons. */
@Composable
fun DialogActions(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Text button in the minimal style. */
@Composable
fun DialogButton(
    text: String,
    enabled: Boolean = true,
    emphasized: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            destructive -> MaterialTheme.colorScheme.error
            emphasized -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.secondary
        },
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.clickableText(onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** Thin, monochrome slider: a hairline track with a small knob. Tap or horizontal-drag to set. */
@Composable
fun MinimalSlider(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    val range = (max - min).coerceAtLeast(1)
    val fraction = ((value - min).toFloat() / range).coerceIn(0f, 1f)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            // Tap to set; horizontal drag to scrub — vertical scrolls pass through to the list.
            .pointerInput(min, max) {
                detectTapGestures { pos ->
                    onChange((min + (pos.x / size.width.toFloat()).coerceIn(0f, 1f) * range).roundToInt())
                }
            }
            .pointerInput(min, max) {
                detectHorizontalDragGestures { change, _ ->
                    onChange((min + (change.position.x / size.width.toFloat()).coerceIn(0f, 1f) * range).roundToInt())
                    change.consume()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumb = 14.dp
        val thumbX = (maxWidth - thumb) * fraction
        Box(Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline))
        Box(Modifier.fillMaxWidth(fraction).height(2.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground))
        Box(Modifier.offset(x = thumbX).size(thumb).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground))
    }
}
