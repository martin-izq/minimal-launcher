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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import android.app.Activity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.martin.minimallauncher.LauncherViewModel
import com.martin.minimallauncher.data.AppInfo
import com.martin.minimallauncher.data.minuteOfDayLabel
import com.martin.minimallauncher.data.LauncherSettings.Companion.DIR_LEFT
import com.martin.minimallauncher.service.NotificationService
import com.martin.minimallauncher.data.LauncherSettings.Companion.DIR_RIGHT
import com.martin.minimallauncher.data.LauncherSettings.Companion.DIR_UP
import com.martin.minimallauncher.ui.widgets.LocalWidgetController
import com.martin.minimallauncher.ui.widgets.WidgetScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Overlay { None, ScreenTime, Settings }

/** The two full-screen surfaces that can live on a side gesture. Quick-launch is an action, not a screen. */
private enum class SideScreen { Widgets, Drawer }

@Composable
fun LauncherRoot(vm: LauncherViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    val settingsLoaded by vm.settingsLoaded.collectAsState()
    val scope = rememberCoroutineScope()

    // Gesture layout: widgets, quick-launch and the drawer each own one of {left, right, up}
    // (a permutation). "Down" is always the notification shade.
    val widgetsDir = state.settings.widgetsDir
    val quickDir = state.settings.quickLaunchDir
    val drawerDir = state.settings.drawerDir
    val quickLaunchPackage = state.settings.quickLaunchPackage

    // Which side screen (if any) sits at each direction.
    fun screenAt(dir: Int): SideScreen? = when (dir) {
        widgetsDir -> SideScreen.Widgets
        drawerDir -> SideScreen.Drawer
        else -> null // quick-launch lives here; it's an action, not a page
    }

    val leftScreen = screenAt(DIR_LEFT)
    val rightScreen = screenAt(DIR_RIGHT)
    val upScreen = screenAt(DIR_UP)

    val hasLeft = leftScreen != null
    val hasRight = rightScreen != null
    val homeIndex = if (hasLeft) 1 else 0
    val leftPageIndex = 0
    val rightPageIndex = homeIndex + 1
    val pageCount = 1 + (if (hasLeft) 1 else 0) + (if (hasRight) 1 else 0)

    // Horizontal axis: side screens ↔ Home. Vertical axis (only if a screen is assigned "up").
    // Recreate both pagers whenever the gesture layout changes so they start fresh at Home with the
    // right indices/pageCount. Persisting a single state across layout changes left the horizontal
    // pager with a stale currentPage/pageCount and broke swiping toward one side.
    val horizontalPager = key(widgetsDir, quickDir, drawerDir) {
        rememberPagerState(initialPage = homeIndex, pageCount = { pageCount })
    }
    val verticalPager = key(widgetsDir, quickDir, drawerDir) {
        rememberPagerState(pageCount = { 2 })
    }
    val homeIndexState = rememberUpdatedState(homeIndex)
    // Effects that outlive a layout change (goHome) must read the current pager instances.
    val horizontalPagerRef = rememberUpdatedState(horizontalPager)
    val verticalPagerRef = rememberUpdatedState(verticalPager)
    val hasUpScreen = upScreen != null

    val widgetController = LocalWidgetController.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // Is the drawer the currently visible page? (for keyboard dismissal)
    val inDrawer = when (drawerDir) {
        DIR_UP -> horizontalPager.currentPage == homeIndex && verticalPager.currentPage == 1
        DIR_LEFT -> horizontalPager.currentPage == leftPageIndex
        DIR_RIGHT -> horizontalPager.currentPage == rightPageIndex
        else -> false
    }
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
    var limitApp by remember { mutableStateOf<AppInfo?>(null) }
    var overLimitApp by remember { mutableStateOf<AppInfo?>(null) }
    var focusBlockApp by remember { mutableStateOf<AppInfo?>(null) }

    // Show/hide the system status bar per the setting.
    val view = LocalView.current
    val hideStatusBar = state.settings.hideStatusBar
    LaunchedEffect(hideStatusBar) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hideStatusBar) controller.hide(WindowInsetsCompat.Type.statusBars())
        else controller.show(WindowInsetsCompat.Type.statusBars())
    }

    // Tick every minute so focus sessions activate/deactivate on schedule. During an active
    // session, distracting apps are "blocked": greyed out and non-launchable across the UI.
    var nowTick by remember { mutableStateOf(java.time.LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowTick = java.time.LocalDateTime.now()
            delay(60_000)
        }
    }
    val nowMs = System.currentTimeMillis()
    val sessionNow = state.settings.focusSessions.firstOrNull {
        it.isActiveAt(nowTick.dayOfWeek.value, nowTick.hour * 60 + nowTick.minute)
    }
    // Scheduled focus counts unless the user "skipped" the current window; manual focus forces it on.
    val scheduledActive = sessionNow != null && nowMs >= state.settings.focusSkipUntil
    val manualActive = nowMs < state.settings.manualFocusUntil
    val focusActiveNow = scheduledActive || manualActive
    val blockedPackages = if (focusActiveNow) state.settings.distracting else emptySet()
    val focusUntil: String? = when {
        manualActive && state.settings.manualFocusUntil != Long.MAX_VALUE ->
            java.time.Instant.ofEpochMilli(state.settings.manualFocusUntil)
                .atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                .let { "%02d:%02d".format(it.hour, it.minute) }
        manualActive -> null // indefinite
        scheduledActive && sessionNow != null -> minuteOfDayLabel(sessionNow.end)
        else -> null
    }
    var showFocusControl by remember { mutableStateOf(false) }

    // Unread notification badges (needs notification access granted).
    val notifCounts by NotificationService.counts.collectAsState()
    val badgeCounts = if (state.settings.showNotificationBadges) notifCounts else emptyMap()

    // Back to home when HOME is pressed
    LaunchedEffect(Unit) {
        vm.goHome.collect {
            overlay = Overlay.None
            optionsApp = null
            renameApp = null
            frictionApp = null
            limitApp = null
            overLimitApp = null
            focusBlockApp = null
            showFocusControl = false
            scope.launch { horizontalPagerRef.value.scrollToPage(homeIndexState.value) }
            scope.launch { verticalPagerRef.value.scrollToPage(0) }
        }
    }

    val onAppClick: (AppInfo) -> Unit = { app ->
        val limitMin = state.settings.appLimits[app.packageName]
        val usedMs = state.usage.perAppToday[app.packageName] ?: 0L
        val distracting = app.packageName in state.settings.distracting
        val inFocus = distracting && focusActiveNow
        when {
            // Focus session → firm block (takes priority; it's the hard block).
            inFocus -> focusBlockApp = app
            // Over the daily limit → soft block. Requires usage permission for perAppToday.
            limitMin != null && usedMs >= limitMin * 60_000L -> overLimitApp = app
            state.settings.frictionEnabled && distracting -> frictionApp = app
            else -> vm.launch(app)
        }
    }
    val onAppLongClick: (AppInfo) -> Unit = { app -> optionsApp = app }

    // Quick-launch: route through onAppClick so it respects limits and focus blocks.
    val onQuickLaunch: (() -> Unit)? = quickLaunchPackage?.let { pkg ->
        {
            val app = state.allApps.firstOrNull { it.packageName == pkg }
            if (app != null) onAppClick(app) else vm.launchByPackage(pkg)
        }
    }

    // Reusable renderers for the two side screens.
    val renderWidgets: @Composable () -> Unit = {
        WidgetScreen(
            placements = state.widgets,
            onRemoveWidget = { id ->
                widgetController?.removeWidget(id)
                vm.removeWidget(id)
            },
            onResizeWidget = { id, heightDp -> vm.setWidgetHeight(id, heightDp) },
            onMoveWidget = { id, up -> vm.moveWidget(id, up) },
        )
    }
    val renderDrawer: @Composable () -> Unit = {
        AppDrawer(
            state = state,
            onAppClick = onAppClick,
            onAppLongClick = onAppLongClick,
            onOpenScreenTime = { overlay = Overlay.ScreenTime },
            // Swipe-down-to-home only makes sense when the drawer opens upward; otherwise the
            // horizontal pager handles going back.
            enableSwipeDownToHome = drawerDir == DIR_UP,
            onSwipeDownToHome = { scope.launch { verticalPager.animateScrollToPage(0) } },
            blockedPackages = blockedPackages,
            badgeCounts = badgeCounts,
        )
    }
    val renderSide: @Composable (SideScreen) -> Unit = { kind ->
        when (kind) {
            SideScreen.Widgets -> renderWidgets()
            SideScreen.Drawer -> renderDrawer()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (settingsLoaded && !state.settings.onboarded) {
            OnboardingScreen(vm = vm, onFinish = { vm.setOnboarded(true) })
        } else {
        when (overlay) {
            Overlay.ScreenTime -> ScreenTimeScreen(state = state, onBack = { overlay = Overlay.None })
            Overlay.Settings -> SettingsScreen(state = state, vm = vm, onBack = { overlay = Overlay.None })
            // Keep adjacent pages composed so hosted AppWidgetHostViews are not detached/re-attached
            // every time we return (re-attaching left them collapsed and invisible until a resize).
            Overlay.None -> HorizontalPager(
                state = horizontalPager,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { hPage ->
                when {
                    hasLeft && hPage == leftPageIndex -> renderSide(leftScreen!!)
                    hasRight && hPage == rightPageIndex -> renderSide(rightScreen!!)
                    else -> {
                        // Home container. If a screen is assigned "up", nest it in a vertical pager.
                        val isHomeVisible = horizontalPager.currentPage == homeIndex &&
                            (!hasUpScreen || verticalPager.currentPage == 0) &&
                            overlay == Overlay.None
                        val home: @Composable () -> Unit = {
                            HomeScreen(
                                state = state,
                                isHomeVisible = isHomeVisible,
                                onAppClick = onAppClick,
                                onAppLongClick = onAppLongClick,
                                onOpenScreenTime = { overlay = Overlay.ScreenTime },
                                onOpenSettings = { overlay = Overlay.Settings },
                                onOpenClock = { vm.openAlarms() },
                                onQuickLaunch = onQuickLaunch,
                                quickLaunchDir = quickDir,
                                drawerDir = drawerDir,
                                blockedPackages = blockedPackages,
                                badgeCounts = badgeCounts,
                                focusActive = focusActiveNow,
                                focusUntil = focusUntil,
                                showFocusChip = state.settings.showFocusOnHome,
                                onFocusTap = { showFocusControl = true },
                            )
                        }
                        if (upScreen != null) {
                            VerticalPager(
                                state = verticalPager,
                                beyondViewportPageCount = 1,
                                modifier = Modifier.fillMaxSize(),
                            ) { vPage ->
                                if (vPage == 0) home() else renderSide(upScreen)
                            }
                        } else {
                            home()
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
                onSetLimit = { limitApp = app; optionsApp = null },
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

        // Time-limit picker (from the app options menu)
        limitApp?.let { app ->
            TimeLimitDialog(
                appLabel = app.displayLabel(state.settings.renames),
                currentMinutes = state.settings.appLimits[app.packageName],
                onSelect = { minutes -> vm.setAppLimit(app, minutes); limitApp = null },
                onDismiss = { limitApp = null },
            )
        }

        // Daily limit reached → soft block
        overLimitApp?.let { app ->
            LimitReachedDialog(
                appLabel = app.displayLabel(state.settings.renames),
                usedTodayMs = state.usage.perAppToday[app.packageName] ?: 0L,
                limitMinutes = state.settings.appLimits[app.packageName] ?: 0,
                seconds = state.settings.frictionSeconds,
                onProceed = { vm.launch(app); overLimitApp = null },
                onDismiss = { overLimitApp = null },
            )
        }

        // Distracting app tapped during a focus session → firm block message
        focusBlockApp?.let { app ->
            FocusBlockDialog(
                appLabel = app.displayLabel(state.settings.renames),
                onDismiss = { focusBlockApp = null },
            )
        }

        // Quick focus control (start manual focus / exit current focus)
        if (showFocusControl) {
            FocusControlDialog(
                active = focusActiveNow,
                onStart = { mins ->
                    val until = if (mins == null) Long.MAX_VALUE
                    else System.currentTimeMillis() + mins * 60_000L
                    vm.setManualFocusUntil(until)
                    showFocusControl = false
                },
                onStop = {
                    vm.setManualFocusUntil(0L)
                    if (scheduledActive && sessionNow != null) {
                        val end = sessionNow.end
                        val endEpoch = java.time.LocalDate.now().atTime(end / 60, end % 60)
                            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        vm.setFocusSkipUntil(endEpoch)
                    }
                    showFocusControl = false
                },
                onDismiss = { showFocusControl = false },
            )
        }
        }
    }
}
