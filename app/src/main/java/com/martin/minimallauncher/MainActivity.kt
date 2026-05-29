package com.martin.minimallauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.martin.minimallauncher.ui.LauncherRoot
import com.martin.minimallauncher.ui.theme.MinimalLauncherTheme

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // El botón atrás nunca sale del launcher: vuelve al inicio.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                vm.emitGoHome()
            }
        })

        setContent {
            val state by vm.uiState.collectAsState()
            MinimalLauncherTheme(amoledDark = state.settings.amoledDark) {
                LauncherRoot(vm = vm)
            }
        }
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
