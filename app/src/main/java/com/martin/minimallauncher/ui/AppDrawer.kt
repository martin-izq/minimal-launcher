package com.martin.minimallauncher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.martin.minimallauncher.LauncherUiState
import com.martin.minimallauncher.data.AppInfo

@Composable
fun AppDrawer(
    state: LauncherUiState,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val renames = state.settings.renames
    val favorites = state.settings.favorites.toSet()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val filtered = remember(query, state.visibleApps, renames) {
        if (query.isBlank()) state.visibleApps
        else state.visibleApps.filter {
            it.displayLabel(renames).contains(query.trim(), ignoreCase = true)
        }
    }

    // Al abrir una app limpiamos el buscador y cerramos el teclado, así al volver
    // al launcher la pantalla queda en blanco y sin foco.
    val launchApp: (AppInfo) -> Unit = { app ->
        query = ""
        keyboard?.hide()
        focusManager.clearFocus()
        onAppClick(app)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("Buscar app") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                filtered.firstOrNull()?.let(launchApp)
            }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        )

        if (filtered.isEmpty()) {
            Text(
                "Sin resultados",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
            )
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { it.packageName }) { app ->
                AppRow(
                    label = app.displayLabel(renames),
                    isFavorite = app.packageName in favorites,
                    onClick = { launchApp(app) },
                    onLongClick = { onAppLongClick(app) },
                )
            }
        }
    }
}
