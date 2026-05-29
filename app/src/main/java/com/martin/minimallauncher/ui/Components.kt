package com.martin.minimallauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import com.martin.minimallauncher.data.AppInfo
import com.martin.minimallauncher.data.LauncherSettings
import com.martin.minimallauncher.util.formatDuration
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    label: String,
    isFavorite: Boolean = false,
    fontSizeSp: Int? = null,
    textAlign: TextAlign? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.let {
                if (fontSizeSp != null) it.copy(fontSize = fontSizeSp.sp) else it
            },
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.weight(1f),
        )
        if (isFavorite) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.width(16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppOptionsSheet(
    app: AppInfo,
    settings: LauncherSettings,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onToggleDistracting: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val isFav = app.packageName in settings.favorites
    val isDistracting = app.packageName in settings.distracting

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                text = app.displayLabel(settings.renames),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            )
            SheetItem(if (isFav) "Quitar de favoritos" else "Agregar a favoritos") { onToggleFavorite(); onDismiss() }
            if (isFav) {
                SheetItem("Mover arriba ↑") { onMoveUp() }
                SheetItem("Mover abajo ↓") { onMoveDown() }
            }
            SheetItem("Renombrar") { onRename() }
            SheetItem(if (isDistracting) "Quitar marca de distractora" else "Marcar como distractora") { onToggleDistracting(); onDismiss() }
            SheetItem("Ocultar app") { onHide(); onDismiss() }
            SheetItem("Info de la app") { onInfo(); onDismiss() }
            SheetItem("Desinstalar") { onUninstall(); onDismiss() }
        }
    }
}

@Composable
private fun SheetItem(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickableSimple(onClick)
            .padding(horizontal = 28.dp, vertical = 14.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableSimple(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)

@Composable
fun RenameDialog(
    app: AppInfo,
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Nombre visible") },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text); onDismiss() }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/**
 * Pantalla de fricción: pausa intencional antes de abrir una app distractora.
 * Muestra el tiempo de uso de hoy y obliga a esperar unos segundos ("respirá").
 */
@Composable
fun FrictionDialog(
    appLabel: String,
    usedTodayMs: Long,
    seconds: Int,
    onProceed: () -> Unit,
    onDismiss: () -> Unit,
) {
    var remaining by remember { mutableStateOf(seconds) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining -= 1
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Seguro que querés abrir $appLabel?") },
        text = {
            Column {
                Text(
                    "Llevás ${formatDuration(usedTodayMs)} en esta app hoy.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Respirá un segundo. ¿Realmente lo necesitás ahora?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onProceed, enabled = remaining <= 0) {
                    Text(if (remaining > 0) "Abrir (${remaining})" else "Abrir igual")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
