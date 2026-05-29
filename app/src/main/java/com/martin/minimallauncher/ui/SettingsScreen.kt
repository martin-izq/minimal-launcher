package com.martin.minimallauncher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.martin.minimallauncher.LauncherUiState
import com.martin.minimallauncher.LauncherViewModel

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
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader("Ajustes", onBack)
        Spacer(Modifier.height(16.dp))

        SectionTitle("Pantalla de inicio")
        ToggleRow("Reloj", s.showClock, vm::setShowClock)
        ToggleRow("Fecha", s.showDate, vm::setShowDate)
        ToggleRow("Batería", s.showBattery, vm::setShowBattery)
        ToggleRow("Resumen de Screen Time", s.showScreenTimeHome, vm::setShowScreenTimeHome)

        Spacer(Modifier.height(8.dp))
        SectionTitle("Personalización de inicio")
        SizeSlider("Tamaño del reloj", s.clockSize, 32, 120, vm::setClockSize)
        SizeSlider("Tamaño de la fecha", s.dateSize, 10, 32, vm::setDateSize)
        SizeSlider("Tamaño de favoritos", s.favoritesSize, 12, 36, vm::setFavoritesSize)
        SegmentedSelector(
            label = "Alineación",
            options = listOf("Izquierda", "Centro", "Derecha"),
            selected = s.homeAlign,
            onSelect = vm::setHomeAlign,
        )
        SegmentedSelector(
            label = "Posición vertical",
            options = listOf("Arriba", "Centro", "Abajo"),
            selected = s.verticalPos,
            onSelect = vm::setVerticalPos,
        )
        ToggleRow("Tocar la hora abre el reloj", s.clockOpensAlarms, vm::setClockOpensAlarms)

        Spacer(Modifier.height(8.dp))
        SectionTitle("Reducir distracciones")
        ToggleRow("Pantalla de fricción al abrir apps distractoras", s.frictionEnabled, vm::setFrictionEnabled)
        Text(
            "Pausa de espera antes de abrir:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            listOf(3, 5, 10).forEach { sec ->
                val selected = s.frictionSeconds == sec
                Text(
                    "${sec}s",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.clickableText { vm.setFrictionSeconds(sec) }.padding(8.dp),
                )
            }
        }
        Text(
            "Apps marcadas como distractoras: ${state.settings.distracting.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )

        Spacer(Modifier.height(8.dp))
        SectionTitle("Apariencia")
        ToggleRow("Tema oscuro AMOLED (negro puro)", s.amoledDark, vm::setAmoled)

        Spacer(Modifier.height(8.dp))
        SectionTitle("Apps ocultas (${state.allApps.count { it.packageName in s.hidden }})")
        val hidden = state.allApps.filter { it.packageName in s.hidden }
        if (hidden.isEmpty()) {
            Text(
                "Ninguna app oculta.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        } else {
            hidden.forEach { app ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(app.displayLabel(s.renames), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Mostrar",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickableText { vm.setHidden(app, false) },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))

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

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
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

@Composable
private fun SegmentedSelector(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 6.dp)) {
            options.forEachIndexed { index, opt ->
                val isSelected = selected == index
                Text(
                    opt,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.clickableText { onSelect(index) }.padding(vertical = 4.dp),
                )
            }
        }
    }
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
