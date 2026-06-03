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
import androidx.compose.ui.unit.dp
import com.martin.minimallauncher.data.WidgetPlacement
import com.martin.minimallauncher.ui.clickableText

/**
 * Pantalla de widgets: los widgets se apilan verticalmente con scroll, sin barra
 * de título para aprovechar el espacio. Se entra en modo edición manteniendo
 * pulsado un widget; ahí aparece una barra inferior para agregar y salir, y cada
 * widget muestra controles para mover, quitar y redimensionar el alto.
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

    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))

            if (controller != null) {
                if (placements.isEmpty()) {
                    Text(
                        "Todavía no agregaste widgets.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                    )
                    Text(
                        "+ Agregar widget",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickableText { showPicker = true }
                            .padding(horizontal = 28.dp, vertical = 12.dp),
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
                                onEnterEdit = { editing = true },
                                onResize = { h -> onResizeWidget(placement.appWidgetId, h) },
                                onMoveUp = { onMoveWidget(placement.appWidgetId, true) },
                                onMoveDown = { onMoveWidget(placement.appWidgetId, false) },
                                onRemove = { removeTarget = placement.appWidgetId },
                            )
                        }
                    }
                }
            }

            // Espacio extra al final para que la barra de edición no tape el último widget.
            Spacer(Modifier.height(if (editing) 72.dp else 24.dp))
        }

        // Barra de edición flotante (solo en modo edición).
        if (editing) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "+ Agregar widget",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickableText { showPicker = true },
                )
                Text(
                    "Listo",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickableText { editing = false },
                )
            }
        }
    }

    if (showPicker && controller != null) {
        WidgetPicker(controller = controller, onDismiss = { showPicker = false })
    }

    removeTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("¿Quitar widget?") },
            confirmButton = {
                TextButton(onClick = { onRemoveWidget(id); removeTarget = null }) { Text("Quitar") }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("Cancelar") }
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
    onEnterEdit: () -> Unit,
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
            onLongPress = onEnterEdit,
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
            // Capa que captura el arrastre vertical (redimensionar) y bloquea la
            // interacción del widget mientras se edita.
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
                    "⬍ arrastrá para el alto",
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
