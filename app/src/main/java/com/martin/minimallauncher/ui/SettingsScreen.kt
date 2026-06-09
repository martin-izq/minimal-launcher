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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.martin.minimallauncher.LauncherUiState
import com.martin.minimallauncher.LauncherViewModel
import com.martin.minimallauncher.R
import com.martin.minimallauncher.service.NotificationAccessibilityService
import com.martin.minimallauncher.util.openAccessibilitySettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: LauncherUiState,
    vm: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val s = state.settings
    val showAppPicker = remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))
        ScreenHeader(stringResource(R.string.settings_title), onBack)

        Section(stringResource(R.string.settings_section_home)) {
            ToggleRow(stringResource(R.string.settings_clock), s.showClock, vm::setShowClock)
            ToggleRow(stringResource(R.string.settings_date), s.showDate, vm::setShowDate)
            ToggleRow(stringResource(R.string.settings_battery), s.showBattery, vm::setShowBattery)
            ToggleRow(stringResource(R.string.settings_screentime_summary), s.showScreenTimeHome, vm::setShowScreenTimeHome)
            val a11yActive = NotificationAccessibilityService.isActive()
            ActionRow(
                title = stringResource(R.string.settings_notif_gesture_title),
                subtitle = stringResource(
                    if (a11yActive) R.string.settings_notif_enabled else R.string.settings_notif_tap_enable
                ),
                onClick = { openAccessibilitySettings(context) },
            )
        }

        val alignOptions = listOf(
            stringResource(R.string.settings_align_left),
            stringResource(R.string.settings_align_center),
            stringResource(R.string.settings_align_right),
        )
        Section(stringResource(R.string.settings_section_home_custom)) {
            SizeSlider(stringResource(R.string.settings_clock_size), s.clockSize, 32, 120, vm::setClockSize)
            SizeSlider(stringResource(R.string.settings_date_size), s.dateSize, 10, 32, vm::setDateSize)
            SizeSlider(stringResource(R.string.settings_favorites_size), s.favoritesSize, 12, 36, vm::setFavoritesSize)
            SegmentedSelector(stringResource(R.string.settings_alignment), alignOptions, s.homeAlign, vm::setHomeAlign)
            SegmentedSelector(
                stringResource(R.string.settings_vertical_position),
                listOf(
                    stringResource(R.string.settings_pos_top),
                    stringResource(R.string.settings_align_center),
                    stringResource(R.string.settings_pos_bottom),
                ),
                s.verticalPos,
                vm::setVerticalPos,
            )
            SegmentedSelector(
                stringResource(R.string.settings_widgets_screen),
                listOf(stringResource(R.string.settings_align_left), stringResource(R.string.settings_align_right)),
                if (s.widgetsOnLeft) 0 else 1,
            ) { vm.setWidgetsOnLeft(it == 0) }
            ToggleRow(stringResource(R.string.settings_clock_opens_alarms), s.clockOpensAlarms, vm::setClockOpensAlarms)
        }

        Section(stringResource(R.string.settings_section_quick)) {
            val quickApp = s.quickLaunchPackage?.let { pkg ->
                state.allApps.firstOrNull { it.packageName == pkg }
            }
            ActionRow(
                title = stringResource(
                    if (s.widgetsOnLeft) R.string.settings_quick_app_right else R.string.settings_quick_app_left
                ),
                subtitle = quickApp?.originalLabel ?: stringResource(R.string.settings_quick_unset),
                onClick = { showAppPicker.value = true },
            )
            if (s.quickLaunchPackage != null) {
                Text(
                    stringResource(R.string.settings_quick_remove),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clickableText { vm.setQuickLaunchPackage(null) }
                        .padding(vertical = 8.dp),
                )
            }
        }

        Section(stringResource(R.string.settings_section_drawer)) {
            SizeSlider(stringResource(R.string.settings_letter_size), s.appDrawerSize, 12, 36, vm::setAppDrawerSize)
            SegmentedSelector(stringResource(R.string.settings_alignment), alignOptions, s.appDrawerAlign, vm::setAppDrawerAlign)
            SegmentedSelector(
                stringResource(R.string.settings_search_position),
                listOf(stringResource(R.string.settings_pos_top), stringResource(R.string.settings_pos_bottom)),
                if (s.searchBarBottom) 1 else 0,
            ) { vm.setSearchBarBottom(it == 1) }
            ToggleRow(stringResource(R.string.settings_alphabet_index), s.alphabetIndex, vm::setAlphabetIndex)
        }

        Section(stringResource(R.string.settings_section_distractions)) {
            ToggleRow(stringResource(R.string.settings_friction_screen), s.frictionEnabled, vm::setFrictionEnabled)
            val secs = listOf(3, 5, 10)
            SegmentedSelector(
                stringResource(R.string.settings_friction_pause),
                secs.map { stringResource(R.string.settings_seconds_value, it) },
                secs.indexOf(s.frictionSeconds).coerceAtLeast(0),
            ) { vm.setFrictionSeconds(secs[it]) }
            val distractingApps = state.allApps.filter { it.packageName in s.distracting }
            if (distractingApps.isNotEmpty()) {
                Text(
                    stringResource(R.string.settings_distracting_apps, distractingApps.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                distractingApps.forEach { app ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            app.originalLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            stringResource(R.string.common_remove),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickableText { vm.setDistracting(app, false) },
                        )
                    }
                }
            } else {
                Text(
                    stringResource(R.string.settings_no_distracting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }

        Section(stringResource(R.string.settings_section_appearance)) {
            ToggleRow(stringResource(R.string.settings_amoled), s.amoledDark, vm::setAmoled)
        }

        val hidden = state.allApps.filter { it.packageName in s.hidden }
        Section(stringResource(R.string.settings_hidden_apps, hidden.size)) {
            if (hidden.isEmpty()) {
                Text(
                    stringResource(R.string.settings_no_hidden),
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
                            app.originalLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            stringResource(R.string.common_show),
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
        ) { Text(stringResource(R.string.settings_set_default_launcher)) }

        Spacer(Modifier.height(40.dp))
    }

    if (showAppPicker.value) {
        ModalBottomSheet(
            onDismissRequest = { showAppPicker.value = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Text(
                stringResource(R.string.settings_quick_choose),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            LazyColumn(Modifier.fillMaxWidth()) {
                items(state.allApps.sortedBy { it.originalLabel.lowercase() }) { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickableText {
                                vm.setQuickLaunchPackage(app.packageName)
                                showAppPicker.value = false
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            app.originalLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        if (app.packageName == s.quickLaunchPackage) {
                            Text(
                                "✓",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

/** Section title (gray) + rounded card grouping the controls. */
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
                stringResource(R.string.settings_size_value, value),
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

/** Pill-style option selector: the chosen option is highlighted with a filled background. */
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
