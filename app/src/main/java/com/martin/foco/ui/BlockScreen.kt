package com.martin.foco.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.martin.foco.ui.theme.FocusWarm

/**
 * Full-screen scaffold shared by the three block moments (friction, daily limit, focus session).
 * This is the screen the user meets at every pause, so it carries the brand instead of a generic
 * dialog: the warm focus light breathing slowly from the top ("take a breath"), centered thin
 * display typography, an uppercase tracked kicker — no cards, no icons.
 *
 * The [content] slot runs inside the main column (below the kicker + app name), so screens can use
 * `Spacer(Modifier.weight(1f))` to compose their own vertical rhythm down to their actions.
 */
@Composable
fun BlockScreen(
    kicker: String,
    appLabel: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)

    // Slow breathing of the warm glow — the visual metronome for the pause (~one breath cycle).
    val breath by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathAlpha",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Swallow taps on empty areas so they don't fall through to the live UI stacked below
            // this overlay in LauncherRoot (unlike the Dialog it replaced, nothing blocks for us).
            // A no-op clickable blocks pass-through while nested child clickables (the buttons)
            // still win their own taps — a plain event-consuming node would instead cancel them.
            .clickableText {}
            // Same radial "light on" as the home's focus glow, but latent: it breathes.
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to FocusWarm.copy(alpha = 0.30f * breath),
                            0.45f to FocusWarm.copy(alpha = 0.12f * breath),
                            1f to Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.height * 0.62f,
                    )
                )
            }
            .systemBarsPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            kicker.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
            color = FocusWarm,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            appLabel,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 40.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-1).sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Spacer(Modifier.height(20.dp))
        content()
        Spacer(Modifier.height(28.dp))
    }
}

/** Centered supporting line on a block screen. */
@Composable
fun BlockBody(text: String, topPadding: Int = 10) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = topPadding.dp),
    )
}

/**
 * The countdown as the screen's centerpiece: a huge thin number in the warm light. Fixed height so
 * the layout doesn't jump when it fades out at zero.
 */
@Composable
fun BlockCountdown(remaining: Int) {
    Box(Modifier.height(120.dp), contentAlignment = Alignment.Center) {
        Crossfade(targetState = remaining > 0, label = "countdown") { counting ->
            if (counting) {
                Text(
                    remaining.toString(),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 92.sp,
                        fontWeight = FontWeight.Light,
                    ),
                    color = FocusWarm,
                )
            }
        }
    }
}

/**
 * The healthy way out — deliberately the easy, prominent action: an outlined pill (same language
 * as the home focus chip at rest). [destructive] tints it with the error color for the "exit focus"
 * action.
 */
@Composable
fun BlockPrimaryAction(text: String, onClick: () -> Unit, destructive: Boolean = false) {
    val shape = RoundedCornerShape(50)
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        modifier = Modifier
            .clip(shape)
            .border(1.dp, if (destructive) color.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline, shape)
            .clickableText(onClick)
            .padding(horizontal = 36.dp, vertical = 14.dp),
    )
}

/** The escape hatch — quiet on purpose; disabled (dimmed) until the countdown ends. */
@Composable
fun BlockQuietAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary.copy(alpha = if (enabled) 1f else 0.35f),
        modifier = Modifier
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.clickableText(onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
