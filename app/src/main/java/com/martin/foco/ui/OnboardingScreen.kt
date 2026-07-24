package com.martin.foco.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.martin.foco.LauncherViewModel
import com.martin.foco.R
import com.martin.foco.service.NotificationAccessibilityService
import com.martin.foco.ui.theme.FocusWarm
import com.martin.foco.util.openAccessibilitySettings

private const val LAST_STEP = 9

/**
 * How warm each panel is (0 = cold monochrome, 1 = full amber). The story mirrors the app itself —
 * "cold at rest, warm in focus": the tour opens in quiet black and the warm focus light rises a
 * little with every panel, so swiping forward literally turns the focus up, full by the last step.
 * A linear ramp across all steps (interpolated with the swipe in [OnboardingScreen]).
 */
private val PanelWarmth = FloatArray(LAST_STEP + 1) { it.toFloat() / LAST_STEP }

/**
 * First-run wizard as a small narrated, illustrated tour, swiped through horizontally: welcome →
 * set as default → gestures → widgets → focus mode → friction → daily limits → strict mode →
 * do not disturb → optional permissions. Instead of listing features it demonstrates the brand — each panel carries
 * an abstract animated hero ([OnbHero]) and the warm focus light rises with the swipe across the
 * focus panels — teaching the differentiators (free-assigned gestures, resizable widgets,
 * phone-wide focus blocking, screen-time insight) implicitly through art and copy.
 */
@Composable
fun OnboardingScreen(vm: LauncherViewModel, onFinish: () -> Unit) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { LAST_STEP + 1 })
    val step = pagerState.currentPage

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

    // Warm light tracking the swipe: interpolated across the panel offset so the glow rises smoothly
    // as the user drags toward the focus panel — the same latent glow the block screens use, here as
    // the emotional arc of the tour.
    val warmth = run {
        val target = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
            .coerceIn(0f, LAST_STEP.toFloat())
        val i = target.toInt().coerceIn(0, LAST_STEP)
        val next = (i + 1).coerceAtMost(LAST_STEP)
        PanelWarmth[i] + (PanelWarmth[next] - PanelWarmth[i]) * (target - i)
    }
    val breath by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathAlpha",
    )
    val kickerColor = lerp(MaterialTheme.colorScheme.secondary, FocusWarm, warmth.coerceIn(0f, 1f))

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                if (warmth <= 0f) return@drawBehind
                val a = warmth * breath
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to FocusWarm.copy(alpha = 0.30f * a),
                            0.45f to FocusWarm.copy(alpha = 0.12f * a),
                            1f to Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.height * 0.62f,
                    )
                )
            }
            .stableStatusBarsPadding()
            .padding(horizontal = 28.dp),
    ) {
        // Skip — top right, while there are panels ahead.
        Box(
            Modifier.fillMaxWidth().padding(top = 20.dp).height(28.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (step < LAST_STEP) {
                Text(
                    stringResource(R.string.onboarding_skip),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.clickableText { onFinish() },
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.Top,
        ) { s ->
            // Each panel scrolls on its own; panels differ in height, so top-anchoring keeps the
            // hero + title steady instead of jumping vertically between steps.
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp),
            ) {
                OnbHero(
                    step = s,
                    warmth = warmth,
                    modifier = Modifier.fillMaxWidth().height(168.dp),
                )
                Spacer(Modifier.height(20.dp))
                when (s) {
                    0 -> {
                        PanelHeader(
                            kicker = stringResource(R.string.onboarding_p1_kicker),
                            title = stringResource(R.string.onboarding_p1_title),
                            kickerColor = kickerColor,
                        )
                        PanelBody(stringResource(R.string.onboarding_p1_body))
                    }

                    1 -> {
                        PanelHeader(
                            kicker = stringResource(R.string.onboarding_p2_kicker),
                            title = stringResource(R.string.onboarding_p2_title),
                            kickerColor = kickerColor,
                        )
                        PanelBody(stringResource(R.string.onboarding_p2_body))
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
                        PanelHeader(
                            kicker = stringResource(R.string.onboarding_p3_kicker),
                            title = stringResource(R.string.onboarding_p3_title),
                            kickerColor = kickerColor,
                        )
                        PanelBody(stringResource(R.string.onboarding_p3_body))
                        Spacer(Modifier.height(28.dp))
                        MicroTip(stringResource(R.string.onboarding_tip_longpress_app))
                        MicroTip(stringResource(R.string.onboarding_tip_settings))
                    }

                    3 -> {
                        PanelHeader(
                            kicker = stringResource(R.string.onboarding_p4_kicker),
                            title = stringResource(R.string.onboarding_p4_title),
                            kickerColor = kickerColor,
                        )
                        PanelBody(stringResource(R.string.onboarding_p4_body))
                    }

                    4 -> {
                        PanelHeader(
                            kicker = stringResource(R.string.onboarding_p5_kicker),
                            title = stringResource(R.string.onboarding_p5_title),
                            kickerColor = kickerColor,
                        )
                        PanelBody(stringResource(R.string.onboarding_p5_body))
                    }

                    5 -> {
                        PanelHeader(
                            kicker = stringResource(R.string.onboarding_friction_kicker),
                            title = stringResource(R.string.onboarding_friction_title),
                            kickerColor = kickerColor,
                        )
                        PanelBody(stringResource(R.string.onboarding_friction_body))
                    }

                    6 -> {
                        PanelHeader(
                            kicker = stringResource(R.string.onboarding_limits_kicker),
                            title = stringResource(R.string.onboarding_limits_title),
                            kickerColor = kickerColor,
                        )
                        PanelBody(stringResource(R.string.onboarding_limits_body))
                    }

                    7 -> {
                        PanelHeader(
                            kicker = stringResource(R.string.onboarding_strict_kicker),
                            title = stringResource(R.string.onboarding_strict_title),
                            kickerColor = kickerColor,
                        )
                        PanelBody(stringResource(R.string.onboarding_strict_body))
                    }

                    8 -> {
                        PanelHeader(
                            kicker = stringResource(R.string.onboarding_dnd_kicker),
                            title = stringResource(R.string.onboarding_dnd_title),
                            kickerColor = kickerColor,
                        )
                        PanelBody(stringResource(R.string.onboarding_dnd_body))
                    }

                    else -> {
                        PanelHeader(
                            kicker = stringResource(R.string.onboarding_p6_kicker),
                            title = stringResource(R.string.onboarding_p6_title),
                            kickerColor = kickerColor,
                        )
                        PanelBody(stringResource(R.string.onboarding_p6_body))
                        Spacer(Modifier.height(24.dp))
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
                }
            }
        }

        // Footer: progress dots, and the finish button on the last panel.
        StepDots(current = step, total = LAST_STEP + 1)
        Box(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp).height(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (step >= LAST_STEP) {
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.common_done))
                }
            }
        }
    }
}

/** Minimal progress: a row of dots, the current one filled and slightly larger. */
@Composable
private fun StepDots(current: Int, total: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { i ->
            val active = i == current
            Box(
                Modifier
                    .size(if (active) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.outline
                    )
            )
        }
    }
}

@Composable
private fun PanelHeader(kicker: String, title: String, kickerColor: Color) {
    Text(
        kicker.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
        color = kickerColor,
    )
    Spacer(Modifier.height(14.dp))
    Text(
        title,
        style = MaterialTheme.typography.displaySmall.copy(
            fontWeight = FontWeight.Light,
            letterSpacing = (-0.8).sp,
        ),
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun PanelBody(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.secondary,
    )
}

/** A quiet practical hint at the foot of a panel — smaller than the body, never the headline. */
@Composable
private fun MicroTip(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            "—  ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
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
