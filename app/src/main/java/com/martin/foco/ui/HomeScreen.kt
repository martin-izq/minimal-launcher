package com.martin.foco.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.martin.foco.LauncherUiState
import com.martin.foco.R
import com.martin.foco.data.AppInfo
import com.martin.foco.data.LauncherSettings.Companion.DIR_LEFT
import com.martin.foco.data.LauncherSettings.Companion.DIR_RIGHT
import com.martin.foco.data.LauncherSettings.Companion.DIR_UP
import com.martin.foco.ui.theme.FocusWarm
import com.martin.foco.ui.theme.OnFocusWarm
import com.martin.foco.service.NotificationAccessibilityService
import com.martin.foco.util.formatDuration
import com.martin.foco.util.openNotificationShade
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun HomeScreen(
    state: LauncherUiState,
    isHomeVisible: Boolean,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    onOpenScreenTime: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenClock: () -> Unit,
    onQuickLaunch: (() -> Unit)? = null,
    quickLaunchDir: Int = DIR_RIGHT,
    drawerDir: Int = DIR_UP,
    blockedPackages: Set<String> = emptySet(),
    badgeCounts: Map<String, Int> = emptyMap(),
    focusActive: Boolean = false,
    focusUntil: String? = null,
    showFocusChip: Boolean = false,
    onFocusTap: () -> Unit = {},
) {
    val s = state.settings
    var hintVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isHomeVisible) {
        if (isHomeVisible) {
            hintVisible = true
            delay(3_000)
            hintVisible = false
        } else {
            hintVisible = false
        }
    }
    val hintAlpha by animateFloatAsState(
        targetValue = if (hintVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "hintAlpha",
    )
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(10_000)
        }
    }

    val context = LocalContext.current
    val quickLaunch = rememberUpdatedState(onQuickLaunch)
    val quickDir = rememberUpdatedState(quickLaunchDir)
    // Home content follows the finger while doing the quick-launch swipe, then springs back.
    var quickOffset by remember { mutableFloatStateOf(0f) }
    val animScope = rememberCoroutineScope()
    val battery = remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(s.showBattery) {
        if (s.showBattery) battery.value = readBatteryLevel(context)
    }

    val horizontalAlign = when (s.homeAlign) {
        1 -> Alignment.CenterHorizontally
        2 -> Alignment.End
        else -> Alignment.Start
    }
    val textAlign = when (s.homeAlign) {
        1 -> TextAlign.Center
        2 -> TextAlign.End
        else -> TextAlign.Start
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Warm "light on" glow from the top while in focus.
            .drawBehind {
                if (focusActive) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to FocusWarm.copy(alpha = 0.28f),
                                0.45f to FocusWarm.copy(alpha = 0.12f),
                                1f to Color.Transparent,
                            ),
                            center = Offset(size.width / 2f, 0f),
                            radius = size.height * 0.62f,
                        )
                    )
                }
            }
            .graphicsLayer {
                if (quickLaunchDir == DIR_UP) translationY = quickOffset
                else translationX = quickOffset
            }
            .statusBarsPadding()
            .pointerInput(Unit) {
                val thresholdPx = 60.dp.toPx()
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalDx = 0f
                    var totalDy = 0f
                    var decided = false
                    // 0 = don't capture (pagers handle it); 1 = swipe down (notifications);
                    // 2 = swipe toward the quick-launch direction (left/right/up).
                    var mode = 0
                    var fired = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val pc = change.positionChange()
                        totalDx += pc.x
                        totalDy += pc.y
                        if (!decided) {
                            if (abs(totalDy) > slop || abs(totalDx) > slop) {
                                decided = true
                                val vertical = abs(totalDy) >= abs(totalDx)
                                val hasQuick = quickLaunch.value != null
                                mode = when {
                                    // Swipe down always opens the notification shade.
                                    vertical && totalDy > 0 -> 1
                                    // Swipe up quick-launch (only when nothing else owns "up").
                                    vertical && hasQuick && quickDir.value == DIR_UP -> 2
                                    // Horizontal quick-launch. The quick side has no page, so the
                                    // pager stays at its edge and we capture the swipe here. The
                                    // finger direction is the OPPOSITE of the quick side: the pager
                                    // reveals a page-on-the-left with a finger-right (and vice
                                    // versa), so quick-on-the-right fires on finger-left, and
                                    // quick-on-the-left fires on finger-right.
                                    !vertical && hasQuick && quickDir.value == DIR_RIGHT && totalDx < 0 -> 2
                                    !vertical && hasQuick && quickDir.value == DIR_LEFT && totalDx > 0 -> 2
                                    else -> 0
                                }
                                if (mode == 0) break
                            }
                        }
                        if (mode != 0) {
                            change.consume()
                            if (mode == 2) {
                                // Move Home along the swipe axis (damped) as feedback.
                                val raw = if (quickDir.value == DIR_UP) totalDy else totalDx
                                quickOffset = raw * 0.5f
                            }
                            if (!fired) {
                                when (mode) {
                                    1 -> if (totalDy >= thresholdPx) {
                                        fired = true
                                        if (!openNotificationShade(context)) {
                                            android.widget.Toast.makeText(
                                                context,
                                                context.getString(R.string.home_notif_gesture_disabled),
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                    2 -> {
                                        val progress = if (quickDir.value == DIR_UP) abs(totalDy) else abs(totalDx)
                                        if (progress >= thresholdPx) {
                                            fired = true
                                            quickLaunch.value?.invoke()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Spring Home back to its place once the finger lifts.
                    if (mode == 2 && quickOffset != 0f) {
                        animScope.launch {
                            animate(quickOffset, 0f, animationSpec = tween(220)) { v, _ -> quickOffset = v }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    // Double tap on empty space locks the screen.
                    onDoubleTap = {
                        if (!NotificationAccessibilityService.lockScreen()) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.home_lock_disabled),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    // Long press opens Settings.
                    onLongPress = { onOpenSettings() },
                )
            },
        horizontalAlignment = horizontalAlign,
    ) {
        // Top spacer: pushes content down when the position is center or bottom
        if (s.verticalPos != 0) Spacer(Modifier.weight(1f))

        // Header: clock / date / battery
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 28.dp, top = 48.dp, bottom = 8.dp),
            horizontalAlignment = horizontalAlign,
        ) {
            // Focus chip: tap to start manual focus, or (when active) to exit. Shows the state.
            if (showFocusChip || focusActive) {
                val chipText = when {
                    focusActive && focusUntil != null -> stringResource(R.string.home_focus_indicator, focusUntil)
                    focusActive -> stringResource(R.string.home_focus_active)
                    else -> stringResource(R.string.home_focus_off)
                }
                val chipShape = RoundedCornerShape(50)
                // Warm "light on" when in focus; a quiet outline at rest.
                val chipBg = if (focusActive) {
                    Modifier.clip(chipShape).background(FocusWarm)
                } else {
                    Modifier.clip(chipShape).border(1.dp, MaterialTheme.colorScheme.outline, chipShape)
                }
                Text(
                    text = chipText,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (focusActive) OnFocusWarm else MaterialTheme.colorScheme.secondary,
                    modifier = chipBg
                        .clickableText(onFocusTap)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(10.dp))
            }
            if (s.showClock) {
                Text(
                    text = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = s.clockSize.sp),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = textAlign,
                    modifier = if (s.clockOpensAlarms) Modifier.clickableText(onOpenClock) else Modifier,
                )
            }
            if (s.showDate) {
                val locale = Locale.getDefault()
                val dateText = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
                    .replaceFirstChar { it.titlecase(locale) }
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = s.dateSize.sp),
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = textAlign,
                )
            }
            if (s.showBattery) {
                battery.value?.let { lvl ->
                    Text(
                        text = stringResource(R.string.home_battery, lvl),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = textAlign,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        // Screen time summary
        if (s.showScreenTimeHome && state.usage.hasPermission) {
            Text(
                text = stringResource(
                    R.string.home_screentime_summary,
                    formatDuration(state.usage.totalTodayMs),
                    state.usage.unlocksToday,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .padding(horizontal = 28.dp)
                    .padding(bottom = 8.dp)
                    .clickableText(onOpenScreenTime),
            )
        } else if (s.showScreenTimeHome) {
            Text(
                text = stringResource(R.string.home_screentime_enable),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .padding(horizontal = 28.dp)
                    .padding(bottom = 8.dp)
                    .clickableText(onOpenScreenTime),
            )
        }

        Spacer(Modifier.height(16.dp))

        // Favorites
        if (state.favoriteApps.isEmpty()) {
            Text(
                text = stringResource(R.string.home_no_favorites),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            )
        } else {
            state.favoriteApps.forEach { app ->
                AppRow(
                    label = app.displayLabel(s.renames),
                    fontSizeSp = s.favoritesSize,
                    textAlign = textAlign,
                    onClick = { onAppClick(app) },
                    onLongClick = { onAppLongClick(app) },
                    blocked = app.packageName in blockedPackages,
                    badge = badgeCounts[app.packageName] ?: 0,
                )
            }
        }

        // Bottom spacer: centers the block when the position is "center"
        if (s.verticalPos == 1) Spacer(Modifier.weight(1f))

        Spacer(Modifier.height(24.dp))

        Box(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).alpha(hintAlpha),
            contentAlignment = Alignment.Center,
        ) {
            // Arrow points in the finger-swipe direction that opens the drawer: up if the drawer
            // is "up"; a screen on the left is revealed by swiping right (and vice versa).
            val swipeArrow = when (drawerDir) {
                DIR_LEFT -> "›"
                DIR_RIGHT -> "‹"
                else -> "⌃"
            }
            Text(
                "$swipeArrow  ${stringResource(R.string.home_swipe_hint)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun readBatteryLevel(context: Context): Int? {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    return (level * 100) / scale
}
