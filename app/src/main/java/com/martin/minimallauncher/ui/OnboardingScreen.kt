package com.martin.minimallauncher.ui

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.martin.minimallauncher.LauncherViewModel
import com.martin.minimallauncher.R
import com.martin.minimallauncher.service.NotificationAccessibilityService
import com.martin.minimallauncher.util.openAccessibilitySettings

private const val LAST_STEP = 3

/** First-run wizard: welcome → set as default → optional permissions → tips. */
@Composable
fun OnboardingScreen(vm: LauncherViewModel, onFinish: () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }

    // Re-check permissions whenever we come back (e.g. from the system settings screen), so a
    // just-granted permission shows as enabled without leaving onboarding.
    var usageGranted by remember { mutableStateOf(vm.hasUsagePermission()) }
    var a11yActive by remember { mutableStateOf(NotificationAccessibilityService.isActive()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                usageGranted = vm.hasUsagePermission()
                a11yActive = NotificationAccessibilityService.isActive()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 28.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column(Modifier.fillMaxWidth()) {
                when (step) {
                    0 -> {
                        Text(
                            stringResource(R.string.onboarding_welcome_title),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.onboarding_welcome_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }

                    1 -> {
                        StepTitle(stringResource(R.string.onboarding_default_title))
                        StepBody(stringResource(R.string.onboarding_default_body))
                        Spacer(Modifier.height(24.dp))
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_HOME_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.settings_set_default_launcher)) }
                    }

                    2 -> {
                        StepTitle(stringResource(R.string.onboarding_perms_title))
                        StepBody(stringResource(R.string.onboarding_perms_body))
                        Spacer(Modifier.height(20.dp))
                        PermissionRow(
                            title = stringResource(R.string.onboarding_perm_usage),
                            body = stringResource(R.string.onboarding_perm_usage_body),
                            enabled = usageGranted,
                            onEnable = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                        )
                        Spacer(Modifier.height(16.dp))
                        PermissionRow(
                            title = stringResource(R.string.onboarding_perm_a11y),
                            body = stringResource(R.string.onboarding_perm_a11y_body),
                            enabled = a11yActive,
                            onEnable = { openAccessibilitySettings(context) },
                        )
                    }

                    else -> {
                        StepTitle(stringResource(R.string.onboarding_tips_title))
                        Spacer(Modifier.height(12.dp))
                        Tip(stringResource(R.string.onboarding_tip_drawer))
                        Tip(stringResource(R.string.onboarding_tip_longpress_app))
                        Tip(stringResource(R.string.onboarding_tip_settings))
                    }
                }
            }
        }

        // Navigation
        Row(
            Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(if (step == 0) R.string.onboarding_skip else R.string.onboarding_back),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .clickableText { if (step == 0) onFinish() else step-- }
                    .padding(8.dp),
            )
            Button(onClick = { if (step >= LAST_STEP) onFinish() else step++ }) {
                Text(
                    stringResource(if (step >= LAST_STEP) R.string.common_done else R.string.onboarding_next)
                )
            }
        }
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun StepBody(text: String) {
    Spacer(Modifier.height(12.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.secondary,
    )
}

@Composable
private fun Tip(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            "•  ",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun PermissionRow(title: String, body: String, enabled: Boolean, onEnable: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(body, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        }
        if (enabled) {
            Text(
                stringResource(R.string.settings_notif_enabled),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp),
            )
        } else {
            OutlinedButton(onClick = onEnable, modifier = Modifier.padding(start = 12.dp)) {
                Text(stringResource(R.string.onboarding_enable))
            }
        }
    }
}
