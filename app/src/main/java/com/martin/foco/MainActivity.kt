package com.martin.foco

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import com.martin.foco.ui.LauncherRoot
import com.martin.foco.ui.theme.MinimalLauncherTheme
import com.martin.foco.ui.widgets.LocalWidgetController
import com.martin.foco.ui.widgets.WidgetController

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()
    private lateinit var widgetController: WidgetController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        widgetController = WidgetController(
            activity = this,
            appWidgetManager = AppWidgetManager.getInstance(this),
            onBound = { placement -> vm.addWidget(placement) },
        )

        // The back button never leaves the launcher: it returns home.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                vm.emitGoHome()
            }
        })

        setContent {
            val state by vm.uiState.collectAsState()
            // Status/nav bar icon color must follow the app's own theme (not the system's), since
            // the launcher has its own light/dark toggle. Light theme → dark icons, and vice versa.
            val darkTheme = state.settings.amoledDark
            LaunchedEffect(darkTheme) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            MinimalLauncherTheme(amoledDark = state.settings.amoledDark, accent = state.settings.accentColor) {
                CompositionLocalProvider(LocalWidgetController provides widgetController) {
                    LauncherRoot(vm = vm)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        widgetController.startListening()
    }

    override fun onStop() {
        super.onStop()
        widgetController.stopListening()
    }

    /**
     * Receives the widget configuration result. This legacy callback is required because
     * [android.appwidget.AppWidgetHost.startAppWidgetConfigureActivityForResult] dispatches
     * through it; launching the configure intent manually via the ActivityResult API breaks
     * configuration for many providers (missing host-granted flags).
     */
    @Deprecated("Required by AppWidgetHost.startAppWidgetConfigureActivityForResult")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        widgetController.handleConfigureResult(requestCode, resultCode)
    }

    /** Pressing HOME while in another app returns to the home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.emitGoHome()
    }

    override fun onResume() {
        super.onResume()
        // Reload apps (installed/uninstalled) and usage stats.
        vm.refresh()
    }
}
