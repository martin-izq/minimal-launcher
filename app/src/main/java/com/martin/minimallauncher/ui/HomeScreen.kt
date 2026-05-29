package com.martin.minimallauncher.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.martin.minimallauncher.LauncherUiState
import com.martin.minimallauncher.data.AppInfo
import com.martin.minimallauncher.util.formatDuration
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val esAR = Locale("es", "AR")

@Composable
fun HomeScreen(
    state: LauncherUiState,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    onOpenScreenTime: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenClock: () -> Unit,
) {
    val s = state.settings
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(10_000)
        }
    }

    val context = LocalContext.current
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
            .statusBarsPadding(),
        horizontalAlignment = horizontalAlign,
    ) {
        // Espaciador superior: empuja el contenido si la posición es centro o abajo
        if (s.verticalPos != 0) Spacer(Modifier.weight(1f))

        // Encabezado: reloj / fecha / batería
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
                val dow = now.dayOfWeek.getDisplayName(TextStyle.FULL, esAR)
                    .replaceFirstChar { it.titlecase(esAR) }
                val month = now.month.getDisplayName(TextStyle.FULL, esAR)
                Text(
                    text = "$dow ${now.dayOfMonth} de $month",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = s.dateSize.sp),
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = textAlign,
                )
            }
            if (s.showBattery) {
                battery.value?.let { lvl ->
                    Text(
                        text = "Batería $lvl%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = textAlign,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        // Resumen de screen time
        if (s.showScreenTimeHome && state.usage.hasPermission) {
            Text(
                text = "Hoy: ${formatDuration(state.usage.totalTodayMs)}  ·  ${state.usage.unlocksToday} desbloqueos",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .padding(horizontal = 28.dp)
                    .padding(bottom = 8.dp)
                    .clickableText(onOpenScreenTime),
            )
        } else if (s.showScreenTimeHome) {
            Text(
                text = "Activá Screen Time →",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .padding(horizontal = 28.dp)
                    .padding(bottom = 8.dp)
                    .clickableText(onOpenScreenTime),
            )
        }

        Spacer(Modifier.height(16.dp))

        // Favoritos
        if (state.favoriteApps.isEmpty()) {
            Text(
                text = "Sin favoritos todavía.\nDeslizá hacia arriba y mantené presionada una app para agregarla.",
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

        // Espaciador inferior: centra el bloque cuando la posición es "centro"
        if (s.verticalPos == 1) Spacer(Modifier.weight(1f))

        Spacer(Modifier.height(24.dp))

        // Acciones inferiores
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                "Apps",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickableText(onOpenDrawer),
            )
            Text(
                "Screen Time",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickableText(onOpenScreenTime),
            )
            Text(
                "Ajustes",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickableText(onOpenSettings),
            )
        }

        Box(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "⌃ deslizá para ver todas",
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
