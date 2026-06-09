package com.martin.minimallauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.martin.minimallauncher.R
import com.martin.minimallauncher.data.AppInfo
import com.martin.minimallauncher.data.LauncherSettings

/** Bottom sheet with per-app actions (favorite, rename, hide, distracting, etc.). */
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
    val isFavorite = app.packageName in settings.favorites
    val isDistracting = app.packageName in settings.distracting

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                text = app.displayLabel(settings.renames),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            )
            SheetItem(
                stringResource(
                    if (isFavorite) R.string.app_options_remove_favorite else R.string.app_options_add_favorite
                )
            ) { onToggleFavorite(); onDismiss() }
            if (isFavorite) {
                SheetItem(stringResource(R.string.app_options_move_up)) { onMoveUp() }
                SheetItem(stringResource(R.string.app_options_move_down)) { onMoveDown() }
            }
            SheetItem(stringResource(R.string.app_options_rename)) { onRename() }
            SheetItem(
                stringResource(
                    if (isDistracting) R.string.app_options_unmark_distracting else R.string.app_options_mark_distracting
                )
            ) { onToggleDistracting(); onDismiss() }
            SheetItem(stringResource(R.string.app_options_hide)) { onHide(); onDismiss() }
            SheetItem(stringResource(R.string.app_options_info)) { onInfo(); onDismiss() }
            SheetItem(stringResource(R.string.app_options_uninstall)) { onUninstall(); onDismiss() }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetItem(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 14.dp),
    )
}
