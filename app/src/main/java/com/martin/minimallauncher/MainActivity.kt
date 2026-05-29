package com.martin.minimallauncher

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.martin.minimallauncher.ui.LauncherRoot
import com.martin.minimallauncher.ui.theme.MinimalLauncherTheme
import com.martin.minimallauncher.ui.widgets.LocalWidgetController
import com.martin.minimallauncher.ui.widgets.WidgetController

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

        // El botón atrás nunca sale del launcher: vuelve al inicio.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                vm.emitGoHome()
            }
        })

        setContent {
            val state by vm.uiState.collectAsState()
            MinimalLauncherTheme(amoledDark = state.settings.amoledDark) {
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

    @Deprecated("Necesario para el resultado de la pantalla de configuración del widget")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        widgetController.handleConfigureResult(requestCode, resultCode)
    }

    /** Presionar HOME estando en una app vuelve a la pantalla de inicio. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.emitGoHome()
    }

    override fun onResume() {
        super.onResume()
        // Recargar apps (instaladas/desinstaladas) y estadísticas de uso.
        vm.refresh()
    }
}
