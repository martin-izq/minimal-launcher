package com.martin.minimallauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.martin.minimallauncher.LauncherViewModel
import com.martin.minimallauncher.data.AppInfo
import com.martin.minimallauncher.ui.widgets.LocalWidgetController
import com.martin.minimallauncher.ui.widgets.WidgetScreen
import kotlinx.coroutines.launch

private enum class Overlay { None, ScreenTime, Settings }

@Composable
fun LauncherRoot(vm: LauncherViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    // Lado de la pantalla de widgets respecto al Inicio.
    val widgetsOnLeft = state.settings.widgetsOnLeft
    val homePage = if (widgetsOnLeft) 1 else 0
    val widgetsPage = if (widgetsOnLeft) 0 else 1
    // Eje vertical: Inicio (0) ↕ Cajón de apps (1)
    val verticalPager = rememberPagerState(pageCount = { 2 })
    // Eje horizontal: Widgets ↔ Inicio/Cajón. Arranca en Inicio.
    val horizontalPager = rememberPagerState(initialPage = homePage, pageCount = { 2 })
    val currentHomePage by rememberUpdatedState(homePage)
    val widgetController = LocalWidgetController.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // Cerrar el teclado / soltar el foco cuando dejamos de estar en el cajón de apps.
    val inDrawer = horizontalPager.currentPage == homePage && verticalPager.currentPage == 1
    LaunchedEffect(inDrawer) {
        if (!inDrawer) {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }

    var overlay by remember { mutableStateOf(Overlay.None) }
    var optionsApp by remember { mutableStateOf<AppInfo?>(null) }
    var renameApp by remember { mutableStateOf<AppInfo?>(null) }
    var frictionApp by remember { mutableStateOf<AppInfo?>(null) }

    // Al cambiar el lado de los widgets, reubicar el pager en el Inicio.
    LaunchedEffect(widgetsOnLeft) { horizontalPager.scrollToPage(homePage) }

    // Volver al inicio al presionar HOME
    LaunchedEffect(Unit) {
        vm.goHome.collect {
            overlay = Overlay.None
            optionsApp = null
            renameApp = null
            frictionApp = null
            scope.launch { horizontalPager.scrollToPage(currentHomePage) }
            scope.launch { verticalPager.scrollToPage(0) }
        }
    }

    val onAppClick: (AppInfo) -> Unit = { app ->
        val distracting = app.packageName in state.settings.distracting
        if (state.settings.frictionEnabled && distracting) {
            frictionApp = app
        } else {
            vm.launch(app)
        }
    }
    val onAppLongClick: (AppInfo) -> Unit = { app -> optionsApp = app }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (overlay) {
            Overlay.ScreenTime -> ScreenTimeScreen(state = state, onBack = { overlay = Overlay.None })
            Overlay.Settings -> SettingsScreen(state = state, vm = vm, onBack = { overlay = Overlay.None })
            Overlay.None -> HorizontalPager(state = horizontalPager, modifier = Modifier.fillMaxSize()) { hPage ->
                when (hPage) {
                    widgetsPage -> WidgetScreen(
                        placements = state.widgets,
                        onRemoveWidget = { id ->
                            widgetController?.removeWidget(id)
                            vm.removeWidget(id)
                        },
                        onResizeWidget = { id, heightDp -> vm.setWidgetHeight(id, heightDp) },
                        onMoveWidget = { id, up -> vm.moveWidget(id, up) },
                    )
                    else -> VerticalPager(state = verticalPager, modifier = Modifier.fillMaxSize()) { page ->
                        when (page) {
                            0 -> HomeScreen(
                                state = state,
                                onAppClick = onAppClick,
                                onAppLongClick = onAppLongClick,
                                onOpenScreenTime = { overlay = Overlay.ScreenTime },
                                onOpenSettings = { overlay = Overlay.Settings },
                                onOpenClock = { vm.openAlarms() },
                            )
                            else -> AppDrawer(
                                state = state,
                                onAppClick = onAppClick,
                                onAppLongClick = onAppLongClick,
                            )
                        }
                    }
                }
            }
        }

        // Menú al mantener presionada una app
        optionsApp?.let { app ->
            AppOptionsSheet(
                app = app,
                settings = state.settings,
                onDismiss = { optionsApp = null },
                onToggleFavorite = { vm.toggleFavorite(app) },
                onRename = { renameApp = app; optionsApp = null },
                onHide = { vm.setHidden(app, true) },
                onToggleDistracting = { vm.setDistracting(app, app.packageName !in state.settings.distracting) },
                onInfo = { vm.openAppInfo(app) },
                onUninstall = { vm.uninstall(app) },
                onMoveUp = { vm.moveFavorite(app, up = true) },
                onMoveDown = { vm.moveFavorite(app, up = false) },
            )
        }

        // Diálogo de renombrar
        renameApp?.let { app ->
            RenameDialog(
                app = app,
                currentName = state.settings.renames[app.packageName] ?: app.originalLabel,
                onConfirm = { vm.rename(app, it) },
                onDismiss = { renameApp = null },
            )
        }

        // Pantalla de fricción
        frictionApp?.let { app ->
            FrictionDialog(
                appLabel = app.displayLabel(state.settings.renames),
                usedTodayMs = state.usage.perAppToday[app.packageName] ?: 0L,
                seconds = state.settings.frictionSeconds,
                onProceed = { vm.launch(app); frictionApp = null },
                onDismiss = { frictionApp = null },
            )
        }
    }
}
