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
    // Side of the widgets screen relative to Home.
    val widgetsOnLeft = state.settings.widgetsOnLeft
    val quickLaunchPackage = state.settings.quickLaunchPackage
    // Quick-launch is NOT a pager page: it's a gesture on Home that launches the app without
    // moving anything (like the swipe-down for notifications). Since it lives on the side
    // opposite the widgets, the triggering gesture is swiping the finger toward that side.
    // widgets on the left  → quick on the right → swipe finger left.
    // widgets on the right → quick on the left  → swipe finger right.
    val quickLaunchSwipeRight = !widgetsOnLeft
    val homePage = if (widgetsOnLeft) 1 else 0
    val widgetsPage = if (widgetsOnLeft) 0 else 1
    // Vertical axis: Home (0) ↕ App drawer (1)
    val verticalPager = rememberPagerState(pageCount = { 2 })
    // Horizontal axis: Widgets ↔ Home/Drawer. Starts on Home.
    val horizontalPager = rememberPagerState(initialPage = homePage, pageCount = { 2 })
    val currentHomePage by rememberUpdatedState(homePage)
    val widgetController = LocalWidgetController.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // Hide the keyboard / drop focus when we leave the app drawer.
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

    // When the widgets side changes, move the pager back to Home.
    LaunchedEffect(widgetsOnLeft) { horizontalPager.scrollToPage(homePage) }

    // Quick-launch action: if an app is configured, swiping toward the side opposite the
    // widgets launches it directly, without moving Home.
    val onQuickLaunch: (() -> Unit)? = quickLaunchPackage?.let { pkg -> { vm.launchByPackage(pkg) } }

    // Back to home when HOME is pressed
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
                        val isHomeVisible = horizontalPager.currentPage == homePage &&
                            verticalPager.currentPage == 0 &&
                            overlay == Overlay.None
                        when (page) {
                            0 -> HomeScreen(
                                state = state,
                                isHomeVisible = isHomeVisible,
                                onAppClick = onAppClick,
                                onAppLongClick = onAppLongClick,
                                onOpenScreenTime = { overlay = Overlay.ScreenTime },
                                onOpenSettings = { overlay = Overlay.Settings },
                                onOpenClock = { vm.openAlarms() },
                                onQuickLaunch = onQuickLaunch,
                                quickLaunchSwipeRight = quickLaunchSwipeRight,
                            )
                            else -> AppDrawer(
                                state = state,
                                onAppClick = onAppClick,
                                onAppLongClick = onAppLongClick,
                                onSwipeDownToHome = { scope.launch { verticalPager.animateScrollToPage(0) } },
                            )
                        }
                    }
                }
            }
        }

        // Long-press app menu
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

        // Rename dialog
        renameApp?.let { app ->
            RenameDialog(
                app = app,
                currentName = state.settings.renames[app.packageName] ?: app.originalLabel,
                onConfirm = { vm.rename(app, it) },
                onDismiss = { renameApp = null },
            )
        }

        // Friction screen
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
