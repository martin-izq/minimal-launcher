package com.martin.minimallauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.abs
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
        val thumb = 16.dp
        val thumbX = (maxWidth - thumb) * fraction
        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline))
        Box(Modifier.fillMaxWidth(fraction).height(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Box(Modifier.offset(x = thumbX).size(thumb).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
    }
}

/**
 * A minimal scroll wheel: a short vertical list that snaps, with the centered row highlighted
 * inside a soft window and neighbors fading out. Drag to spin, it settles on the nearest value.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    count: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    label: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    val itemHeight = 38.dp
    val visible = 3
    val density = LocalDensity.current
    val itemPx = with(density) { itemHeight.toPx() }
    val state = rememberLazyListState(selected.coerceIn(0, (count - 1).coerceAtLeast(0)))
    val fling = rememberSnapFlingBehavior(lazyListState = state)

    // When scrolling settles, report the row closest to the viewport center.
    LaunchedEffect(state, count) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                val info = state.layoutInfo
                if (info.visibleItemsInfo.isNotEmpty()) {
                    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                    val idx = info.visibleItemsInfo.minByOrNull {
                        abs((it.offset + it.size / 2f) - center)
                    }!!.index
                    if (idx != selected) onSelect(idx)
                }
            }
        }
    }

    Box(modifier.height(itemHeight * visible), contentAlignment = Alignment.Center) {
        // Selection window.
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = itemHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(count) { i ->
                val info = state.layoutInfo
                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                val dist = info.visibleItemsInfo.firstOrNull { it.index == i }
                    ?.let { abs((it.offset + it.size / 2f) - center) } ?: itemPx
                val norm = (dist / itemPx).coerceIn(0f, 1f)
                Box(Modifier.height(itemHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        label(i),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 1f - 0.72f * norm),
                    )
                }
            }
        }
    }
}
