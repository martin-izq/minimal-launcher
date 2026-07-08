package com.martin.minimallauncher.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.martin.minimallauncher.LauncherUiState
import com.martin.minimallauncher.LauncherViewModel
import com.martin.minimallauncher.R
import com.martin.minimallauncher.data.FocusSession
import com.martin.minimallauncher.service.NotificationAccessibilityService
import com.martin.minimallauncher.ui.theme.AccentColors
import com.martin.minimallauncher.util.openAccessibilitySettings
import java.util.UUID

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
    var editorSession by remember { mutableStateOf<FocusSession?>(null) }
    var editorIsNew by remember { mutableStateOf(false) }

    // Re-check special-access permissions on resume so status updates after returning from settings.
    fun notifAccessGranted() =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    var a11yActive by remember { mutableStateOf(NotificationAccessibilityService.isActive()) }
    var notifAccess by remember { mutableStateOf(notifAccessGranted()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yActive = NotificationAccessibilityService.isActive()
                notifAccess = notifAccessGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { u ->
            runCatching {
                context.contentResolver.openOutputStream(u)?.use { it.write(vm.exportSettingsJson().toByteArray()) }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { u ->
            runCatching {
                context.contentResolver.openInputStream(u)?.use { vm.importSettings(it.readBytes().decodeToString()) }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))
        ScreenHeader(stringResource(R.string.settings_title), onBack)

        Section(stringResource(R.string.settings_section_home), initiallyExpanded = true) {
            ToggleRow(stringResource(R.string.settings_clock), s.showClock, vm::setShowClock)
            ToggleRow(stringResource(R.string.settings_date), s.showDate, vm::setShowDate)
            ToggleRow(stringResource(R.string.settings_battery), s.showBattery, vm::setShowBattery)
            ToggleRow(stringResource(R.string.settings_screentime_summary), s.showScreenTimeHome, vm::setShowScreenTimeHome)
            ToggleRow(stringResource(R.string.settings_hide_status_bar), s.hideStatusBar, vm::setHideStatusBar)
            ActionRow(
                title = stringResource(R.string.settings_notif_gesture_title),
                subtitle = stringResource(
                    if (a11yActive) R.string.settings_notif_enabled else R.string.settings_notif_tap_enable
                ),
                onClick = { openAccessibilitySettings(context) },
            )
            ToggleRow(stringResource(R.string.settings_notif_badges), s.showNotificationBadges, vm::setShowNotificationBadges)
            if (s.showNotificationBadges) {
                ActionRow(
                    title = stringResource(R.string.settings_notif_access),
                    subtitle = stringResource(
                        if (notifAccess) R.string.settings_notif_enabled else R.string.settings_notif_access_grant
                    ),
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                )
            }
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
            ToggleRow(stringResource(R.string.settings_clock_opens_alarms), s.clockOpensAlarms, vm::setClockOpensAlarms)
        }

        val dirOptions = listOf(
            stringResource(R.string.settings_align_left),
            stringResource(R.string.settings_align_right),
            stringResource(R.string.settings_dir_up),
        )
        Section(stringResource(R.string.settings_section_gestures)) {
            Text(
                stringResource(R.string.settings_gestures_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            // Each gesture owns a distinct direction; picking a taken one swaps them.
            SegmentedSelector(stringResource(R.string.settings_gesture_widgets), dirOptions, s.widgetsDir) {
                vm.setGestureDir(0, it)
            }
            SegmentedSelector(stringResource(R.string.settings_gesture_quick), dirOptions, s.quickLaunchDir) {
                vm.setGestureDir(1, it)
            }
            SegmentedSelector(stringResource(R.string.settings_gesture_drawer), dirOptions, s.drawerDir) {
                vm.setGestureDir(2, it)
            }

            // Quick-launch app lives on the direction chosen above.
            val quickApp = s.quickLaunchPackage?.let { pkg ->
                state.allApps.firstOrNull { it.packageName == pkg }
            }
            val quickDirName = dirOptions[s.quickLaunchDir.coerceIn(0, 2)].lowercase()
            ActionRow(
                title = stringResource(R.string.settings_quick_app_dir, quickDirName),
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
            DpSlider(stringResource(R.string.settings_drawer_top_space), s.drawerTopSpace, 0, 200, vm::setDrawerTopSpace)
            ToggleRow(stringResource(R.string.settings_drawer_show_title), s.drawerShowTitle, vm::setDrawerShowTitle)
            if (s.drawerShowTitle) {
                val titleField = remember(s.drawerTitle) { mutableStateOf(s.drawerTitle) }
                OutlinedTextField(
                    value = titleField.value,
                    onValueChange = { titleField.value = it; vm.setDrawerTitle(it) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_drawer_title)) },
                    placeholder = { Text(stringResource(R.string.drawer_default_title)) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
            }
            ToggleRow(stringResource(R.string.settings_drawer_show_usage), s.drawerShowUsage, vm::setDrawerShowUsage)
            ToggleRow(stringResource(R.string.settings_alphabet_index), s.alphabetIndex, vm::setAlphabetIndex)
            if (s.alphabetIndex) {
                DpSlider(stringResource(R.string.settings_scrubber_width), s.scrubberWidth, 24, 72, vm::setScrubberWidth)
            }
        }

        Section(stringResource(R.string.settings_section_focus)) {
            Text(
                stringResource(R.string.settings_focus_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            ToggleRow(stringResource(R.string.settings_focus_show_home), s.showFocusOnHome, vm::setShowFocusOnHome)

            // Scheduled sessions — hard block distracting apps during these windows.
            SubHead(stringResource(R.string.settings_focus_sessions))
            s.focusSessions.forEach { session ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        focusSessionLabel(session),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .weight(1f)
                            .clickableText { editorSession = session; editorIsNew = false },
                    )
                    Switch(
                        checked = session.enabled,
                        onCheckedChange = { vm.upsertFocusSession(session.copy(enabled = it)) },
                        colors = minimalSwitchColors(),
                    )
                }
            }
            Text(
                stringResource(R.string.settings_focus_add),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickableText {
                        editorSession = FocusSession(
                            id = UUID.randomUUID().toString(),
                            start = 9 * 60,
                            end = 18 * 60,
                            days = setOf(1, 2, 3, 4, 5),
                        )
                        editorIsNew = true
                    }
                    .padding(vertical = 10.dp),
            )

            // Friction — soft pause when there's no active session.
            SubHead(stringResource(R.string.settings_focus_friction))
            ToggleRow(stringResource(R.string.settings_friction_screen), s.frictionEnabled, vm::setFrictionEnabled)
            val secs = listOf(3, 5, 10)
            SegmentedSelector(
                stringResource(R.string.settings_friction_pause),
                secs.map { stringResource(R.string.settings_seconds_value, it) },
                secs.indexOf(s.frictionSeconds).coerceAtLeast(0),
            ) { vm.setFrictionSeconds(secs[it]) }

            // Distracting apps that the two mechanisms above act on.
            val distractingApps = state.allApps.filter { it.packageName in s.distracting }
            SubHead(stringResource(R.string.settings_distracting_apps, distractingApps.size))
            if (distractingApps.isEmpty()) {
                Text(
                    stringResource(R.string.settings_no_distracting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            } else {
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
            }

        }

        Section(stringResource(R.string.settings_section_appearance)) {
            ToggleRow(stringResource(R.string.settings_amoled), s.amoledDark, vm::setAmoled)
            Text(
                stringResource(R.string.settings_accent),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AccentColors.forEachIndexed { i, c ->
                    val swatch = c ?: MaterialTheme.colorScheme.onBackground
                    val selected = s.accentColor == i
                    Box(
                        Modifier
                            .size(34.dp)
                            .then(
                                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, CircleShape)
                                else Modifier
                            )
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .clickableText { vm.setAccentColor(i) },
                    )
                }
            }
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

        Section(stringResource(R.string.settings_section_backup)) {
            ActionRow(
                title = stringResource(R.string.settings_backup_export),
                subtitle = stringResource(R.string.settings_backup_export_desc),
                onClick = { exportLauncher.launch("foco-settings.json") },
            )
            ActionRow(
                title = stringResource(R.string.settings_backup_import),
                subtitle = stringResource(R.string.settings_backup_import_desc),
                onClick = { importLauncher.launch(arrayOf("application/json")) },
            )
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

    editorSession?.let { session ->
        FocusSessionEditorDialog(
            session = session,
            isNew = editorIsNew,
            onSave = { vm.upsertFocusSession(it); editorSession = null },
            onDelete = { vm.removeFocusSession(session.id); editorSession = null },
            onDismiss = { editorSession = null },
        )
    }
}

/** Collapsible settings group: a tappable title that reveals a rounded card with the controls. */
@Composable
private fun Section(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickableText { expanded = !expanded }
                .padding(horizontal = 6.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                "⌄",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.rotate(rotation),
            )
        }
        AnimatedVisibility(expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                content = content,
            )
        }
    }
}

/** Small gray subheading inside a section. */
@Composable
private fun SubHead(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
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
        Switch(checked = checked, onCheckedChange = onChange, colors = minimalSwitchColors())
    }
}

/**
 * Switch colors with a clearly visible OFF thumb. The default Material3 off state renders the
 * thumb and track in near-identical dark grays on the AMOLED theme, which reads as "disabled".
 */
@Composable
private fun minimalSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedBorderColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.secondary,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
)

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

/** Slider whose value is shown in dp (used for spacing / touch-band widths). */
@Composable
private fun DpSlider(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(
                stringResource(R.string.settings_dp_value, value),
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
