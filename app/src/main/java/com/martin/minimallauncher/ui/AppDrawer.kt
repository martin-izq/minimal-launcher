package com.martin.minimallauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.martin.minimallauncher.LauncherUiState
import com.martin.minimallauncher.R
import com.martin.minimallauncher.data.AppInfo
import com.martin.minimallauncher.util.formatDuration
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun AppDrawer(
    state: LauncherUiState,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    onOpenScreenTime: () -> Unit = {},
    enableSwipeDownToHome: Boolean = true,
    onSwipeDownToHome: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    val s = state.settings
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
    val headerAlign = when (s.appDrawerAlign) {
        1 -> Alignment.CenterHorizontally
        2 -> Alignment.End
        else -> Alignment.Start
    }

    // The drawer shows each app's original name (renames only apply to favorites).
    val filtered = remember(query, state.visibleApps) {
        if (query.isBlank()) state.visibleApps
        else state.visibleApps.filter {
            it.originalLabel.contains(query.trim(), ignoreCase = true)
        }
    }

    // First letter → index of the first matching app, for the alphabet scrubber.
    val letters = remember(filtered) {
        val map = LinkedHashMap<Char, Int>()
        filtered.forEachIndexed { i, app ->
            val first = app.originalLabel.trim().firstOrNull()?.uppercaseChar()
            val key = if (first != null && first.isLetter()) first else '#'
            if (key !in map) map[key] = i
        }
        map.entries.map { it.key to it.value }
    }
    var lastScrubIndex by remember { mutableIntStateOf(-1) }
    // Letter under the finger while actively scrubbing (drives the floating bubble).
    var scrubLetter by remember { mutableStateOf<Char?>(null) }
    // Letter of the section currently at the top of the list (drives the scrubber highlight).
    val currentLetter by remember(letters) {
        derivedStateOf {
            val idx = listState.firstVisibleItemIndex
            letters.lastOrNull { it.second <= idx }?.first
        }
    }

    val scrubberShown = s.alphabetIndex && letters.size > 1
    // So we don't steal the scrubber's gesture: the swipe-down-to-home is ignored if the
    // drag starts over the side band where the scrubber lives.
    val scrubberActive = rememberUpdatedState(scrubberShown)
    val scrubberOnLeft = rememberUpdatedState(s.appDrawerAlign == 2)
    val scrubberBandDp = s.scrubberWidth.dp

    // When opening an app, clear the search box and hide the keyboard.
    val launchApp: (AppInfo) -> Unit = { app ->
        query = ""
        keyboard?.hide()
        focusManager.clearFocus()
        onAppClick(app)
    }

    // While typing, once the search narrows down to a single app, open it automatically.
    LaunchedEffect(filtered) {
        if (query.isNotBlank() && filtered.size == 1) {
            launchApp(filtered.first())
        }
    }

    val swipeDownModifier = if (enableSwipeDownToHome) {
        // Back to home with little resistance: if the list starts at the top and the user drags
        // down, we return home past a small threshold. We process in the Initial pass (before the
        // LazyColumn and its overscroll, which would otherwise consume the gesture so it was never
        // detected) and measure with positionChangeIgnoreConsumed.
        Modifier.pointerInput(Unit) {
            val thresholdPx = 56.dp.toPx()
            val slop = viewConfiguration.touchSlop
            val scrubberBandPx = scrubberBandDp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val atTop = !listState.canScrollBackward
                // Did the gesture start over the scrubber's side band?
                val onScrubber = scrubberActive.value && run {
                    if (scrubberOnLeft.value) down.position.x < scrubberBandPx
                    else down.position.x > size.width - scrubberBandPx
                }
                var totalDx = 0f
                var totalDy = 0f
                var decided = false
                var capture = false
                var fired = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val pc = change.positionChangeIgnoreConsumed()
                    totalDx += pc.x
                    totalDy += pc.y
                    if (!decided) {
                        if (abs(totalDy) > slop || abs(totalDx) > slop) {
                            decided = true
                            capture = atTop && totalDy > 0 && abs(totalDy) > abs(totalDx) && !onScrubber
                            if (!capture) break
                        }
                    }
                    if (capture) {
                        change.consume()
                        if (!fired && totalDy >= thresholdPx) {
                            fired = true
                            onSwipeDownToHome()
                        }
                    }
                }
            }
        }
    } else Modifier

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .then(swipeDownModifier),
    ) {
        // Header (title / usage) so the list starts lower and the top area is useful. Hidden while
        // searching to give the results more room.
        if (query.isBlank()) {
            DrawerHeader(
                state = state,
                headerAlign = headerAlign,
                textAlign = textAlign,
                onOpenScreenTime = onOpenScreenTime,
            )
        } else if (s.drawerTopSpace > 0) {
            Spacer(Modifier.height(s.drawerTopSpace.dp))
        }

        if (!s.searchBarBottom) {
            SearchField(query, { query = it }, { filtered.firstOrNull()?.let(launchApp) })
        }

        if (filtered.isEmpty()) {
            Text(
                stringResource(R.string.drawer_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
            )
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.packageName }) { app ->
                    AppRow(
                        label = app.originalLabel,
                        isFavorite = app.packageName in favorites,
                        fontSizeSp = s.appDrawerSize,
                        textAlign = textAlign,
                        onClick = { launchApp(app) },
                        onLongClick = { onAppLongClick(app) },
                    )
                }
            }

            if (scrubberShown) {
                // If apps are right-aligned, the scrubber goes left to avoid overlap.
                val scrubberAlignment =
                    if (s.appDrawerAlign == 2) Alignment.CenterStart else Alignment.CenterEnd
                AlphabetScrubber(
                    entries = letters,
                    highlight = scrubLetter ?: currentLetter,
                    width = scrubberBandDp,
                    modifier = Modifier.align(scrubberAlignment),
                    onPick = { index, letter ->
                        scrubLetter = letter
                        if (index != lastScrubIndex) {
                            lastScrubIndex = index
                            scope.launch { listState.scrollToItem(index) }
                        }
                    },
                    onScrubEnd = { scrubLetter = null },
                )
            }

            // Floating bubble showing the letter under the finger while scrubbing.
            scrubLetter?.let { letter ->
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        letter.toString(),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        if (s.searchBarBottom) {
            SearchField(query, { query = it }, { filtered.firstOrNull()?.let(launchApp) })
        }
    }
}

/** Top area of the drawer: optional configurable title and today's usage summary. */
@Composable
private fun DrawerHeader(
    state: LauncherUiState,
    headerAlign: Alignment.Horizontal,
    textAlign: TextAlign,
    onOpenScreenTime: () -> Unit,
) {
    val s = state.settings
    if (s.drawerTopSpace > 0) Spacer(Modifier.height(s.drawerTopSpace.dp))
    if (!s.drawerShowTitle && !s.drawerShowUsage) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 8.dp),
        horizontalAlignment = headerAlign,
    ) {
        if (s.drawerShowTitle) {
            val title = s.drawerTitle.ifBlank { stringResource(R.string.drawer_default_title) }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = textAlign,
            )
        }
        if (s.drawerShowUsage) {
            val text = if (state.usage.hasPermission) {
                stringResource(
                    R.string.home_screentime_summary,
                    formatDuration(state.usage.totalTodayMs),
                    state.usage.unlocksToday,
                )
            } else {
                stringResource(R.string.home_screentime_enable)
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = textAlign,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clickableText(onOpenScreenTime),
            )
        }
    }
}

/** App search field. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onGo: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        label = { Text(stringResource(R.string.drawer_search_hint)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

/** A-Z scrubber on the side: tap or drag jumps to the matching section. */
@Composable
private fun AlphabetScrubber(
    entries: List<Pair<Char, Int>>,
    highlight: Char?,
    width: Dp,
    onPick: (index: Int, letter: Char) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var heightPx by remember { mutableIntStateOf(0) }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .onSizeChanged { heightPx = it.height }
            .pointerInput(entries, heightPx) {
                if (entries.isEmpty()) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun pick(y: Float) {
                        val h = heightPx
                        if (h <= 0) return
                        val idx = ((y / h) * entries.size).toInt().coerceIn(0, entries.lastIndex)
                        onPick(entries[idx].second, entries[idx].first)
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
                    onScrubEnd()
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        entries.forEach { (c, _) ->
            val active = c == highlight
            Text(
                c.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondary,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}
