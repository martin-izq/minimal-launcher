package com.martin.minimallauncher.ui.widgets

import android.appwidget.AppWidgetManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.martin.minimallauncher.R
import com.martin.minimallauncher.data.WidgetPlacement
import com.martin.minimallauncher.ui.clickableText

/**
 * Widgets screen: widgets are stacked vertically with scroll. An "Edit" button toggles
 * edit mode, where each widget shows controls to move, remove and resize its height.
 */
@Composable
fun WidgetScreen(
    placements: List<WidgetPlacement>,
    onRemoveWidget: (Int) -> Unit,
    onResizeWidget: (Int, Int) -> Unit,
    onMoveWidget: (Int, Boolean) -> Unit,
) {
    val controller = LocalWidgetController.current
    var showPicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<Int?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // Header with title and Edit/Done button
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 28.dp, top = 48.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.widgets_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (controller != null && placements.isNotEmpty()) {
                Text(
                    stringResource(if (editing) R.string.common_done else R.string.common_edit),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickableText { editing = !editing },
                )
            }
        }

        if (controller != null) {
            if (placements.isEmpty()) {
                Text(
                    stringResource(R.string.widgets_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                )
            } else {
                placements.forEachIndexed { index, placement ->
                    key(placement.appWidgetId) {
                        EditableWidgetItem(
                            placement = placement,
                            controller = controller,
                            editing = editing,
                            canMoveUp = index > 0,
                            canMoveDown = index < placements.lastIndex,
                            onResize = { h -> onResizeWidget(placement.appWidgetId, h) },
                            onMoveUp = { onMoveWidget(placement.appWidgetId, true) },
                            onMoveDown = { onMoveWidget(placement.appWidgetId, false) },
                            onRemove = { removeTarget = placement.appWidgetId },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.widgets_add),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickableText { showPicker = true }
                .padding(vertical = 16.dp),
        )
        Spacer(Modifier.height(24.dp))
    }

    if (showPicker && controller != null) {
        WidgetPicker(controller = controller, onDismiss = { showPicker = false })
    }

    removeTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text(stringResource(R.string.widgets_remove_title)) },
            confirmButton = {
                TextButton(onClick = { onRemoveWidget(id); removeTarget = null }) {
                    Text(stringResource(R.string.common_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun EditableWidgetItem(
    placement: WidgetPlacement,
    controller: WidgetController,
    editing: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onResize: (Int) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val config = LocalConfiguration.current
    val info = remember(placement.appWidgetId) {
        AppWidgetManager.getInstance(context).getAppWidgetInfo(placement.appWidgetId)
    }
    val defaultH = remember(info) { ((info?.minHeight ?: 0) / density).coerceAtLeast(100f) }
    val minH = remember(info) {
        (((info?.minResizeHeight ?: 0).takeIf { it > 0 } ?: (info?.minHeight ?: 0)) / density)
            .coerceAtLeast(60f)
    }
    val maxH = config.screenHeightDp * 0.85f

    var heightDp by remember(placement.appWidgetId, placement.heightDp) {
        mutableStateOf(if (placement.heightDp > 0) placement.heightDp.toFloat() else defaultH)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        WidgetHostViewItem(
            appWidgetId = placement.appWidgetId,
            controller = controller,
            heightDp = heightDp.toInt(),
            scrollable = !editing,
            modifier = if (editing) {
                Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(4.dp),
                )
            } else {
                Modifier
            },
        )

        if (editing) {
            // Layer that captures the vertical drag (resize) and blocks the widget's
            // interaction while editing.
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(placement.appWidgetId) {
                        detectVerticalDragGestures(
                            onDragEnd = { onResize(heightDp.toInt()) },
                        ) { change, dragAmount ->
                            change.consume()
                            heightDp = (heightDp + dragAmount / density).coerceIn(minH, maxH)
                        }
                    },
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                EditControl("↑", enabled = canMoveUp, onClick = onMoveUp)
                EditControl("↓", enabled = canMoveDown, onClick = onMoveDown)
                EditControl("✕", enabled = true, onClick = onRemove)
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 3.dp),
            ) {
                Text(
                    stringResource(R.string.widgets_resize_hint),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun EditControl(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
            .then(if (enabled) Modifier.clickableText(onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            symbol,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
    }
}
