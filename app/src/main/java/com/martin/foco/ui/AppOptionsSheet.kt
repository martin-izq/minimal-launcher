package com.martin.foco.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.martin.foco.R
import com.martin.foco.data.AppInfo
import com.martin.foco.data.LauncherSettings

/** Bottom sheet with per-app actions (favorite, rename, hide, distracting, etc.). */
@Composable
fun AppOptionsSheet(
    app: AppInfo,
    settings: LauncherSettings,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onToggleDistracting: () -> Unit,
    onSetLimit: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    // Options that only affect the app while it lives on the home screen (rename, reorder).
    // Hidden when the menu is opened from the app drawer, where they have no visible effect.
    showHomeOptions: Boolean = true,
) {
    val isFavorite = app.packageName in settings.favorites
    val isDistracting = app.packageName in settings.distracting
    val limit = settings.appLimits[app.packageName]

    MinimalMenu(onDismiss) {
        MenuHeader(
            kicker = stringResource(R.string.app_options_kicker),
            title = app.displayLabel(settings.renames),
        )
        MenuItem(
            stringResource(
                if (isFavorite) R.string.app_options_remove_favorite else R.string.app_options_add_favorite
            )
        ) { onToggleFavorite(); onDismiss() }
        if (showHomeOptions && isFavorite) {
            MenuItem(stringResource(R.string.app_options_move_up)) { onMoveUp() }
            MenuItem(stringResource(R.string.app_options_move_down)) { onMoveDown() }
        }
        if (showHomeOptions) {
            MenuItem(stringResource(R.string.app_options_rename)) { onRename() }
        }
        MenuItem(
            stringResource(
                if (isDistracting) R.string.app_options_unmark_distracting else R.string.app_options_mark_distracting
            ),
            accent = isDistracting,
        ) { onToggleDistracting(); onDismiss() }
        MenuItem(
            stringResource(R.string.app_options_limit),
            trailing = limit?.let { stringResource(R.string.focus_minutes_value, it) },
            accent = limit != null,
        ) { onSetLimit() }
        MenuItem(stringResource(R.string.app_options_hide)) { onHide(); onDismiss() }
        MenuItem(stringResource(R.string.app_options_info)) { onInfo(); onDismiss() }
        MenuItem(stringResource(R.string.app_options_uninstall), destructive = true) { onUninstall(); onDismiss() }
    }
}
