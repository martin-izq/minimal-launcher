package com.martin.minimallauncher.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.martin.minimallauncher.LauncherUiState
import com.martin.minimallauncher.R
import com.martin.minimallauncher.data.AppInfo
import com.martin.minimallauncher.service.NotificationAccessibilityService
import com.martin.minimallauncher.util.formatDuration
import com.martin.minimallauncher.util.openNotificationShade
import kotlinx.coroutines.delay
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
    quickLaunchSwipeRight: Boolean = false,
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
    val quickSwipeRight = rememberUpdatedState(quickLaunchSwipeRight)
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
                    // 2 = horizontal swipe toward the side opposite the widgets (quick-launch).
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
                                if (abs(totalDy) >= abs(totalDx)) {
                                    // Vertical: only capture downward (up = drawer).
                                    mode = if (totalDy > 0) 1 else 0
                                } else {
                                    // Horizontal: capture only if there's a quick-launch app and
                                    // the gesture goes the right way (the side opposite the widgets).
                                    // The other direction is handled by the pager (goes to widgets).
                                    val goingRight = totalDx > 0
                                    mode = if (quickLaunch.value != null &&
                                        goingRight == quickSwipeRight.value
                                    ) 2 else 0
                                }
                                if (mode == 0) break
                            }
                        }
                        if (mode != 0) {
                            change.consume()
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
                                    2 -> if (abs(totalDx) >= thresholdPx) {
                                        fired = true
                                        quickLaunch.value?.invoke()
                                    }
                                }
                            }
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
            Text(
                stringResource(R.string.home_swipe_hint),
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
