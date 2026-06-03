package com.martin.minimallauncher.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.martin.minimallauncher.LauncherUiState
import com.martin.minimallauncher.data.AppInfo
import kotlinx.coroutines.launch

@Composable
fun AppDrawer(
    state: LauncherUiState,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val s = state.settings
    val renames = s.renames
    val favorites = s.favorites.toSet()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val textAlign = when (s.appDrawerAlign) {
        1 -> TextAlign.Center
        2 -> TextAlign.End
        else -> TextAlign.Start
    }

    val filtered = remember(query, state.visibleApps, renames) {
        if (query.isBlank()) state.visibleApps
        else state.visibleApps.filter {
            it.displayLabel(renames).contains(query.trim(), ignoreCase = true)
        }
    }

    // Inicial → índice de la primera app, para la guía alfabética.
    val letters = remember(filtered, renames) {
        val map = LinkedHashMap<Char, Int>()
        filtered.forEachIndexed { i, app ->
            val first = app.displayLabel(renames).trim().firstOrNull()?.uppercaseChar()
            val key = if (first != null && first.isLetter()) first else '#'
            if (key !in map) map[key] = i
        }
        map.entries.map { it.key to it.value }
    }
    var lastScrubIndex by remember { mutableStateOf(-1) }

    // Al abrir una app limpiamos el buscador y cerramos el teclado.
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

        Box(Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.packageName }) { app ->
                    AppRow(
                        label = app.displayLabel(renames),
                        isFavorite = app.packageName in favorites,
                        fontSizeSp = s.appDrawerSize,
                        textAlign = textAlign,
                        onClick = { launchApp(app) },
                        onLongClick = { onAppLongClick(app) },
                    )
                }
            }

            if (s.alphabetIndex && letters.size > 1) {
                // Si las apps están a la derecha, la guía va a la izquierda para no solaparse.
                val scrubberAlignment =
                    if (s.appDrawerAlign == 2) Alignment.CenterStart else Alignment.CenterEnd
                AlphabetScrubber(
                    entries = letters,
                    modifier = Modifier.align(scrubberAlignment),
                    onPick = { index ->
                        if (index != lastScrubIndex) {
                            lastScrubIndex = index
                            scope.launch { listState.scrollToItem(index) }
                        }
                    },
                )
            }
        }
    }
}

/** Guía A-Z a la derecha: tocar o arrastrar salta a la sección correspondiente. */
@Composable
private fun AlphabetScrubber(
    entries: List<Pair<Char, Int>>,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var heightPx by remember { mutableStateOf(0) }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(end = 6.dp)
            .onSizeChanged { heightPx = it.height }
            .pointerInput(entries, heightPx) {
                if (entries.isEmpty()) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun pick(y: Float) {
                        val h = heightPx
                        if (h <= 0) return
                        val idx = ((y / h) * entries.size).toInt().coerceIn(0, entries.lastIndex)
                        onPick(entries[idx].second)
                    }
                    pick(down.position.y)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        pick(change.position.y)
                        change.consume()
                    }
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        entries.forEach { (c, _) ->
            Text(
                c.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}
