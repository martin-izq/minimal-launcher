package com.martin.minimallauncher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.martin.minimallauncher.LauncherUiState
import com.martin.minimallauncher.LauncherViewModel
import com.martin.minimallauncher.service.NotificationAccessibilityService
import com.martin.minimallauncher.util.openAccessibilitySettings

@Composable
fun SettingsScreen(
    state: LauncherUiState,
    vm: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val s = state.settings

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))
        ScreenHeader("Ajustes", onBack)

        Section("Pantalla de inicio") {
            ToggleRow("Reloj", s.showClock, vm::setShowClock)
            ToggleRow("Fecha", s.showDate, vm::setShowDate)
            ToggleRow("Batería", s.showBattery, vm::setShowBattery)
            ToggleRow("Resumen de Screen Time", s.showScreenTimeHome, vm::setShowScreenTimeHome)
            val a11yActive = NotificationAccessibilityService.isActive()
            ActionRow(
                title = "Notificaciones al deslizar hacia abajo",
                subtitle = if (a11yActive) "Activado" else "Tocá para activar (accesibilidad)",
                onClick = { openAccessibilitySettings(context) },
            )
        }

        Section("Personalización de inicio") {
            SizeSlider("Tamaño del reloj", s.clockSize, 32, 120, vm::setClockSize)
            SizeSlider("Tamaño de la fecha", s.dateSize, 10, 32, vm::setDateSize)
            SizeSlider("Tamaño de favoritos", s.favoritesSize, 12, 36, vm::setFavoritesSize)
            SegmentedSelector("Alineación", listOf("Izquierda", "Centro", "Derecha"), s.homeAlign, vm::setHomeAlign)
            SegmentedSelector("Posición vertical", listOf("Arriba", "Centro", "Abajo"), s.verticalPos, vm::setVerticalPos)
            SegmentedSelector(
                "Pantalla de widgets",
                listOf("Izquierda", "Derecha"),
                if (s.widgetsOnLeft) 0 else 1,
            ) { vm.setWidgetsOnLeft(it == 0) }
            ToggleRow("Tocar la hora abre el reloj", s.clockOpensAlarms, vm::setClockOpensAlarms)
        }

        Section("Cajón de apps") {
            SizeSlider("Tamaño de la letra", s.appDrawerSize, 12, 36, vm::setAppDrawerSize)
            SegmentedSelector("Alineación", listOf("Izquierda", "Centro", "Derecha"), s.appDrawerAlign, vm::setAppDrawerAlign)
            ToggleRow("Guía alfabética", s.alphabetIndex, vm::setAlphabetIndex)
        }

        Section("Reducir distracciones") {
            ToggleRow("Pantalla de fricción", s.frictionEnabled, vm::setFrictionEnabled)
            val secs = listOf(3, 5, 10)
            SegmentedSelector(
                "Pausa antes de abrir",
                secs.map { "${it}s" },
                secs.indexOf(s.frictionSeconds).coerceAtLeast(0),
            ) { vm.setFrictionSeconds(secs[it]) }
            Text(
                "Apps marcadas como distractoras: ${s.distracting.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }

        Section("Apariencia") {
            ToggleRow("Tema oscuro AMOLED (negro puro)", s.amoledDark, vm::setAmoled)
        }

        val hidden = state.allApps.filter { it.packageName in s.hidden }
        Section("Apps ocultas (${hidden.size})") {
            if (hidden.isEmpty()) {
                Text(
                    "Ninguna app oculta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            } else {
                hidden.forEach { app ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            app.displayLabel(s.renames),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "Mostrar",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickableText { vm.setHidden(app, false) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Establecer como launcher por defecto") }

        Spacer(Modifier.height(40.dp))
    }
}

/** Título de sección (gris) + tarjeta redondeada que agrupa los controles. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(start = 6.dp, top = 24.dp, bottom = 8.dp),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        content = content,
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickableText(onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun SizeSlider(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "${value}sp",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
        )
    }
}

/** Selector de opciones tipo "pill": la opción elegida queda resaltada con fondo. */
@Composable
private fun SegmentedSelector(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            options.forEachIndexed { index, opt ->
                val isSelected = selected == index
                Text(
                    opt,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickableText { onSelect(index) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}
