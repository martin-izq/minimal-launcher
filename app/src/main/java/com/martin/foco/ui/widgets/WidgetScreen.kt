package com.martin.foco.ui.widgets

import android.appwidget.AppWidgetManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.martin.foco.R
import com.martin.foco.data.WidgetPlacement
import com.martin.foco.ui.MenuItem
import com.martin.foco.ui.MinimalMenu
import com.martin.foco.ui.SCREEN_TOP_OFFSET_DP
import com.martin.foco.ui.clickableText
import com.martin.foco.ui.stableStatusBarsPadding
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Widgets screen: nothing but the widgets themselves. Long-pressing anywhere opens a menu to add
 * a widget or edit the ones already placed; in edit mode each widget gets a grab bar to resize it
 * plus controls to reorder and remove it. "Done" (or back) leaves edit mode.
 */
@Composable
fun WidgetScreen(
    placements: List<WidgetPlacement>,
    onRemoveWidget: (Int) -> Unit,
    onResizeWidget: (Int, Int) -> Unit,
    onMoveWidget: (Int, Boolean) -> Unit,
    onAddWidget: () -> Unit,
) {
    val controller = LocalWidgetController.current
    var editing by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var optionsTarget by remember { mutableStateOf<Int?>(null) }
    var removeTarget by remember { mutableStateOf<Int?>(null) }

    // Removing the last widget leaves nothing to edit, so the mode shouldn't survive it.
    val inEditMode = editing && controller != null && placements.isNotEmpty()

    // Reset the underlying flag too, not just the derived one: otherwise adding a widget later would
    // silently re-enter edit mode (inEditMode would flip back to true on its own).
    LaunchedEffect(placements.isEmpty()) {
        if (placements.isEmpty()) editing = false
    }

    // Leaving edit mode is what back should do first, before leaving the screen.
    BackHandler(enabled = inEditMode) { editing = false }

    Column(
        Modifier
            .fillMaxSize()
            .stableStatusBarsPadding()
            // Not while editing: there the grab bar owns the drag and the menu would only get in
            // the way of adjusting a widget.
            .onLongPress(enabled = controller != null && !inEditMode) { showMenu = true }
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 28.dp, top = SCREEN_TOP_OFFSET_DP.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.widgets_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (inEditMode) {
                Text(
                    stringResource(R.string.common_done),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickableText { editing = false }
                        .padding(vertical = 8.dp),
                )
            }
        }

        if (inEditMode) {
            Text(
                stringResource(R.string.widgets_resize_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 12.dp),
            )
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
                placements.forEach { placement ->
                    key(placement.appWidgetId) {
                        EditableWidgetItem(
                            placement = placement,
                            controller = controller,
                            editing = inEditMode,
                            // A menu on top must not leave the widget still taking the touch that
                            // opened it, or it fires its own tap behind the sheet.
                            interactive = !inEditMode && !showMenu,
                            onResize = { h -> onResizeWidget(placement.appWidgetId, h) },
                            onOptions = { optionsTarget = placement.appWidgetId },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showMenu) {
        MinimalMenu(onDismiss = { showMenu = false }) {
            MenuItem(stringResource(R.string.widgets_add)) {
                showMenu = false
                onAddWidget()
            }
            if (placements.isNotEmpty()) {
                MenuItem(stringResource(R.string.widgets_edit)) {
                    showMenu = false
                    editing = true
                }
            }
        }
    }

    // Per-widget actions while editing. Moving keeps the menu open so you can nudge a widget
    // several positions without reopening it each time.
    optionsTarget?.let { id ->
        val index = placements.indexOfFirst { it.appWidgetId == id }
        if (index < 0) {
            optionsTarget = null
        } else {
            MinimalMenu(onDismiss = { optionsTarget = null }) {
                if (index > 0) {
                    MenuItem(stringResource(R.string.app_options_move_up)) { onMoveWidget(id, true) }
                }
                if (index < placements.lastIndex) {
                    MenuItem(stringResource(R.string.app_options_move_down)) { onMoveWidget(id, false) }
                }
                MenuItem(stringResource(R.string.common_remove), destructive = true) {
                    optionsTarget = null
                    removeTarget = id
                }
            }
        }
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

/**
 * Fires [onLongPress] without ever consuming the gesture, so taps still reach the widgets below.
 *
 * Watching the **Initial** pass is what makes this safe on a scrolling page: as soon as the scroll
 * (or a resize drag) claims the pointer the change comes back consumed and we bail, instead of the
 * timer firing blindly. A detector living in the hosted View can't see that — once Compose takes
 * the gesture the View stops receiving events at all, which is why scrolling kept entering edit mode.
 */
private fun Modifier.onLongPress(enabled: Boolean, onLongPress: () -> Unit): Modifier =
    this.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis + EXTRA_HOLD_MS) {
                var stillDown = true
                while (stillDown) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed || change.isConsumed ||
                        (change.position - down.position).getDistance() > slop
                    ) {
                        stillDown = false
                    }
                }
                Unit
            }
            // Only a timeout means the finger stayed put and unclaimed for the whole press.
            if (held == null) onLongPress()
        }
    }

// The system timeout alone felt trigger-happy on a page you mostly scroll.
private const val EXTRA_HOLD_MS = 150L

@Composable
private fun EditableWidgetItem(
    placement: WidgetPlacement,
    controller: WidgetController,
    editing: Boolean,
    interactive: Boolean,
    onResize: (Int) -> Unit,
    onOptions: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val screenHeightDp = LocalConfiguration.current.screenHeightDp

    val info = remember(placement.appWidgetId) {
        AppWidgetManager.getInstance(context).getAppWidgetInfo(placement.appWidgetId)
    }
    val sizing = remember(info, density, screenHeightDp) {
        widgetSizing(info, density, screenHeightDp)
    }

    // The height the provider actually supports — a stored value outside that range is what
    // leaves a widget rendering its "can't display content" state.
    val resolvedHeight = sizing.resolve(placement.heightDp)
    var heightDp by remember(placement.appWidgetId, resolvedHeight) {
        mutableStateOf(resolvedHeight.toFloat())
    }

    // Heal widgets stored at an unsupported height (from before these bounds were honoured),
    // so the fix survives instead of being re-clamped on every composition.
    LaunchedEffect(placement.appWidgetId, placement.heightDp, resolvedHeight) {
        if (placement.heightDp != resolvedHeight) onResize(resolvedHeight)
    }

    // Every edit affordance is laid *over* the widget, never around it: anything that adds height
    // shifts the page, so you end up guessing where the widgets will really sit once you're done.
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        WidgetHostViewItem(
            appWidgetId = placement.appWidgetId,
            controller = controller,
            heightDp = heightDp.roundToInt(),
            scrollable = !editing,
            interactive = interactive,
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
            // Reordering and removing live behind a long press instead of a row of buttons, which
            // added far too much height. Safe to lay a tap detector over the widget here — in edit
            // mode it's already inert.
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(placement.appWidgetId) {
                        detectTapGestures(onLongPress = { onOptions() })
                    },
            )
            // Added after the tap layer so the drag wins over it.
            if (sizing.resizable) {
                ResizeGrabBar(
                    key = placement.appWidgetId,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onDrag = { deltaPx ->
                        heightDp = (heightDp + deltaPx / density)
                            .coerceIn(sizing.minHeightDp.toFloat(), sizing.maxHeightDp.toFloat())
                    },
                    onDragEnd = { onResize(heightDp.roundToInt()) },
                )
            }
        }
    }
}

/**
 * Resize handle pinned to the widget's bottom edge, *inside* its box so edit mode doesn't change
 * the page layout. The touch target spans the full width; the visible chip is small and sits on a
 * scrim so it stays legible over whatever the widget is drawing underneath.
 */
@Composable
private fun ResizeGrabBar(
    key: Int,
    onDrag: (deltaPx: Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(key) {
                detectVerticalDragGestures(onDragEnd = onDragEnd) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    RoundedCornerShape(50),
                )
                .padding(horizontal = 18.dp, vertical = 7.dp),
        ) {
            Box(
                Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
            )
        }
    }
}

