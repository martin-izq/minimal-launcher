package com.martin.foco.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.martin.foco.ui.theme.FocusWarm

/**
 * Abstract, geometric hero illustrations for the onboarding tour. On brand — monochrome line-art
 * drawn on Canvas (never clip-art icons), amber only where focus is), each one quietly *animating*
 * the idea of the panel it sits above. Dispatched by [OnbHero].
 */
@Composable
fun OnbHero(step: Int, warmth: Float, modifier: Modifier = Modifier) {
    when (step) {
        0 -> FocusOrbArt(modifier)
        1 -> GridToListArt(modifier)
        2 -> GesturesArt(modifier)
        3 -> WidgetsArt(modifier)
        4 -> FocusTileArt(modifier)
        5 -> FrictionArt(modifier)
        6 -> LimitArt(modifier)
        7 -> StrictArt(modifier)
        8 -> DndArt(modifier)
        else -> ScreenTimeArt(modifier, warmth)
    }
}

/** Welcome: a single point of attention, with rings breathing outward from it. */
@Composable
private fun FocusOrbArt(modifier: Modifier) {
    val onBg = MaterialTheme.colorScheme.onBackground
    val t = rememberInfiniteTransition(label = "orb")
    val ring by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "ring",
    )
    val core by t.animateFloat(
        0.72f, 1f,
        infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "core",
    )
    Canvas(modifier) {
        val c = center
        val maxR = size.minDimension * 0.44f
        for (i in 0..2) {
            val p = (ring + i / 3f) % 1f
            drawCircle(
                color = onBg.copy(alpha = (1f - p) * 0.4f),
                radius = maxR * p,
                center = c,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
        drawCircle(onBg, radius = 5.dp.toPx() * core, center = c)
    }
}

/** Text-only home: an icon grid dissolving into a clean text list, inside a phone frame. */
@Composable
private fun GridToListArt(modifier: Modifier) {
    val onBg = MaterialTheme.colorScheme.onBackground
    val sec = MaterialTheme.colorScheme.secondary
    val outline = MaterialTheme.colorScheme.outline
    val t by rememberInfiniteTransition(label = "morph").animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "t",
    )
    Canvas(modifier) {
        val fw = 96.dp.toPx()
        val fh = 148.dp.toPx()
        val left = (size.width - fw) / 2f
        val top = (size.height - fh) / 2f
        drawRoundRect(
            color = outline,
            topLeft = Offset(left, top),
            size = Size(fw, fh),
            cornerRadius = CornerRadius(16.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx()),
        )
        // Icon grid — fades/shrinks out (alpha 1-t).
        val cell = 16.dp.toPx()
        val gap = 12.dp.toPx()
        val gridW = 3 * cell + 2 * gap
        val gx = left + (fw - gridW) / 2f
        val gy = top + 22.dp.toPx()
        val gA = (1f - t)
        for (r in 0..2) for (col in 0..2) {
            val s = cell * (0.6f + 0.4f * gA)
            val off = (cell - s) / 2f
            drawRoundRect(
                color = sec.copy(alpha = gA * 0.9f),
                topLeft = Offset(gx + col * (cell + gap) + off, gy + r * (cell + gap) + off),
                size = Size(s, s),
                cornerRadius = CornerRadius(5.dp.toPx()),
            )
        }
        // Text list — fades in (alpha t).
        val barW = 64.dp.toPx()
        val lx = left + (fw - barW) / 2f
        var ly = top + 30.dp.toPx()
        for (i in 0..3) {
            drawRoundRect(
                color = onBg.copy(alpha = t),
                topLeft = Offset(lx, ly),
                size = Size(barW, 8.dp.toPx()),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            ly += 20.dp.toPx()
        }
    }
}

/** Gestures: three chevrons (left / right / up) lighting up in sequence around a center point. */
@Composable
private fun GesturesArt(modifier: Modifier) {
    val onBg = MaterialTheme.colorScheme.onBackground
    val sec = MaterialTheme.colorScheme.secondary
    val phase by rememberInfiniteTransition(label = "gest").animateFloat(
        0f, 3f,
        infiniteRepeatable(tween(2700, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(modifier) {
        val c = center
        drawCircle(sec, radius = 4.dp.toPx(), center = c)
        val reach = 56.dp.toPx()
        val arm = 13.dp.toPx()
        val w = 2.5.dp.toPx()
        // Alpha peaks when the moving phase reaches this chevron's slot (wrapped).
        fun a(i: Int): Float {
            val raw = kotlin.math.abs(phase - i)
            val d = minOf(raw, 3f - raw)
            return (1f - d).coerceIn(0f, 1f) * 0.8f + 0.15f
        }
        drawChevron(Offset(c.x - reach, c.y), 0, arm, onBg.copy(alpha = a(0)), w)   // left
        drawChevron(Offset(c.x + reach, c.y), 1, arm, onBg.copy(alpha = a(1)), w)   // right
        drawChevron(Offset(c.x, c.y - reach), 2, arm, onBg.copy(alpha = a(2)), w)   // up
    }
}

/** dir: 0 = points left, 1 = points right, 2 = points up. */
private fun DrawScope.drawChevron(apex: Offset, dir: Int, arm: Float, color: Color, stroke: Float) {
    val a1: Offset
    val a2: Offset
    when (dir) {
        0 -> { a1 = Offset(apex.x + arm, apex.y - arm); a2 = Offset(apex.x + arm, apex.y + arm) }
        1 -> { a1 = Offset(apex.x - arm, apex.y - arm); a2 = Offset(apex.x - arm, apex.y + arm) }
        else -> { a1 = Offset(apex.x - arm, apex.y + arm); a2 = Offset(apex.x + arm, apex.y + arm) }
    }
    drawLine(color, a1, apex, strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, a2, apex, strokeWidth = stroke, cap = StrokeCap.Round)
}

/** Widgets: a card that breathes between two heights (resizable), with content lines + a handle. */
@Composable
private fun WidgetsArt(modifier: Modifier) {
    val onBg = MaterialTheme.colorScheme.onBackground
    val sec = MaterialTheme.colorScheme.secondary
    val outline = MaterialTheme.colorScheme.outline
    val h by rememberInfiniteTransition(label = "wdg").animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h",
    )
    Canvas(modifier) {
        val cw = 122.dp.toPx()
        val minH = 52.dp.toPx()
        val maxH = 104.dp.toPx()
        val ch = minH + (maxH - minH) * h
        val left = (size.width - cw) / 2f
        val top = (size.height - ch) / 2f - 12.dp.toPx()
        drawRoundRect(
            color = outline,
            topLeft = Offset(left, top),
            size = Size(cw, ch),
            cornerRadius = CornerRadius(14.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx()),
        )
        val lx = left + 16.dp.toPx()
        drawRoundRect(onBg.copy(alpha = 0.9f), Offset(lx, top + 18.dp.toPx()), Size(cw * 0.5f, 11.dp.toPx()), CornerRadius(5.dp.toPx()))
        drawRoundRect(sec.copy(alpha = 0.65f), Offset(lx, top + 37.dp.toPx()), Size(cw * 0.72f, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
        // Resize handle riding the bottom edge.
        drawRoundRect(onBg, Offset(center.x - 13.dp.toPx(), top + ch - 3.dp.toPx()), Size(26.dp.toPx(), 4.dp.toPx()), CornerRadius(2.dp.toPx()))
        // A favorites line resting below the card.
        val bw = 70.dp.toPx()
        drawRoundRect(sec.copy(alpha = 0.45f), Offset((size.width - bw) / 2f, top + ch + 22.dp.toPx()), Size(bw, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
    }
}

/** Focus: an app tile dimming under a warm countdown sweep — the pause, made visible. */
@Composable
private fun FocusTileArt(modifier: Modifier) {
    val onBg = MaterialTheme.colorScheme.onBackground
    val t = rememberInfiniteTransition(label = "focus")
    val sweep by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    val breath by t.animateFloat(
        0.5f, 1f,
        infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    Canvas(modifier) {
        val c = center
        val ringR = 52.dp.toPx()
        // Soft warm glow behind everything.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(FocusWarm.copy(alpha = 0.22f * breath), Color.Transparent),
                center = c,
                radius = ringR * 1.9f,
            ),
            radius = ringR * 1.9f,
            center = c,
        )
        // The app tile, dimming as the countdown fills.
        val tile = 52.dp.toPx()
        val dim = 1f - 0.7f * sweep
        drawRoundRect(
            color = onBg.copy(alpha = 0.85f * dim),
            topLeft = Offset(c.x - tile / 2f, c.y - tile / 2f),
            size = Size(tile, tile),
            cornerRadius = CornerRadius(14.dp.toPx()),
        )
        // Latent full ring + the sweeping arc. The ring stays clearly present so the tile always
        // reads as an app held inside a countdown, not a bare white square.
        drawCircle(FocusWarm.copy(alpha = 0.4f * breath), radius = ringR, center = c, style = Stroke(width = 3.dp.toPx()))
        drawArc(
            color = FocusWarm.copy(alpha = 0.95f),
            startAngle = -90f,
            sweepAngle = 360f * sweep,
            useCenter = false,
            topLeft = Offset(c.x - ringR, c.y - ringR),
            size = Size(ringR * 2f, ringR * 2f),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/** Friction: a mindful pause — the pause glyph breathing inside a warm ring, a beat before you enter. */
@Composable
private fun FrictionArt(modifier: Modifier) {
    val breath by rememberInfiniteTransition(label = "friction").animateFloat(
        0.45f, 1f,
        infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    Canvas(modifier) {
        val c = center
        val ringR = 46.dp.toPx()
        // Breathing warm halo.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(FocusWarm.copy(alpha = 0.20f * breath), Color.Transparent),
                center = c,
                radius = ringR * 2f,
            ),
            radius = ringR * 2f,
            center = c,
        )
        // Ring that swells a touch with the breath.
        drawCircle(
            FocusWarm.copy(alpha = 0.35f + 0.35f * breath),
            radius = ringR * (0.92f + 0.08f * breath),
            center = c,
            style = Stroke(width = 3.dp.toPx()),
        )
        // Pause glyph — two amber bars: the beat before you open.
        val barW = 8.dp.toPx()
        val barH = 34.dp.toPx()
        val gap = 9.dp.toPx()
        val topY = c.y - barH / 2f
        val radius = CornerRadius(barW / 2f)
        drawRoundRect(FocusWarm, Offset(c.x - gap - barW, topY), Size(barW, barH), radius)
        drawRoundRect(FocusWarm, Offset(c.x + gap, topY), Size(barW, barH), radius)
    }
}

/** Daily limit: a time bar filling up to an amber limit mark, then stopping dead at the ceiling. */
@Composable
private fun LimitArt(modifier: Modifier) {
    val onBg = MaterialTheme.colorScheme.onBackground
    val outline = MaterialTheme.colorScheme.outline
    val fill by rememberInfiniteTransition(label = "limit").animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "fill",
    )
    Canvas(modifier) {
        val barW = 150.dp.toPx()
        val barH = 22.dp.toPx()
        val left = (size.width - barW) / 2f
        val top = center.y - barH / 2f
        val limitFrac = 0.68f
        drawRoundRect(
            color = outline,
            topLeft = Offset(left, top),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(barH / 2f),
            style = Stroke(width = 1.5.dp.toPx()),
        )
        // Fill, clamped at the limit; it turns amber the moment it hits the ceiling.
        val inset = 3.dp.toPx()
        val innerH = barH - 2 * inset
        val fillFrac = minOf(fill, limitFrac)
        val innerW = (barW - 2 * inset) * fillFrac
        if (innerW > 0f) {
            drawRoundRect(
                color = if (fill >= limitFrac) FocusWarm else onBg.copy(alpha = 0.85f),
                topLeft = Offset(left + inset, top + inset),
                size = Size(innerW.coerceAtLeast(innerH), innerH),
                cornerRadius = CornerRadius(innerH / 2f),
            )
        }
        // The limit mark.
        val lx = left + barW * limitFrac
        drawLine(
            FocusWarm,
            Offset(lx, top - 12.dp.toPx()),
            Offset(lx, top + barH + 12.dp.toPx()),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** Strict mode: a closed padlock, its keyhole glowing amber — no way out until time's up. */
@Composable
private fun StrictArt(modifier: Modifier) {
    val onBg = MaterialTheme.colorScheme.onBackground
    val breath by rememberInfiniteTransition(label = "lock").animateFloat(
        0.45f, 1f,
        infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    Canvas(modifier) {
        val c = center
        val bodyW = 66.dp.toPx()
        val bodyH = 52.dp.toPx()
        val bodyTop = c.y - 4.dp.toPx()
        val bodyLeft = c.x - bodyW / 2f
        val stroke = 2.5.dp.toPx()
        val line = onBg.copy(alpha = 0.9f)
        // Shackle: a closed arch with two legs dropping into the body.
        val shackleR = 17.dp.toPx()
        val legTop = bodyTop - 12.dp.toPx()
        drawArc(
            color = line,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(c.x - shackleR, legTop - shackleR),
            size = Size(shackleR * 2f, shackleR * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(line, Offset(c.x - shackleR, legTop), Offset(c.x - shackleR, bodyTop), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(line, Offset(c.x + shackleR, legTop), Offset(c.x + shackleR, bodyTop), strokeWidth = stroke, cap = StrokeCap.Round)
        // Body.
        drawRoundRect(
            color = line,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(12.dp.toPx()),
            style = Stroke(width = stroke),
        )
        // Keyhole, amber, breathing.
        val kc = Offset(c.x, bodyTop + bodyH * 0.42f)
        drawCircle(FocusWarm.copy(alpha = breath), radius = 5.dp.toPx(), center = kc)
        drawLine(FocusWarm.copy(alpha = breath), kc, Offset(kc.x, kc.y + 12.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** Do Not Disturb: the universal minus-in-a-circle, breathing a soft warm halo. */
@Composable
private fun DndArt(modifier: Modifier) {
    val onBg = MaterialTheme.colorScheme.onBackground
    val breath by rememberInfiniteTransition(label = "dnd").animateFloat(
        0.4f, 1f,
        infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    Canvas(modifier) {
        val c = center
        val r = 40.dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(FocusWarm.copy(alpha = 0.20f * breath), Color.Transparent),
                center = c,
                radius = r * 2.1f,
            ),
            radius = r * 2.1f,
            center = c,
        )
        drawCircle(onBg.copy(alpha = 0.9f), radius = r, center = c, style = Stroke(width = 2.5.dp.toPx()))
        val half = r * 0.5f
        drawLine(
            FocusWarm,
            Offset(c.x - half, c.y),
            Offset(c.x + half, c.y),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** Screen Time: a week of bars growing in, today's bar warming toward amber with [warmth]. */
@Composable
private fun ScreenTimeArt(modifier: Modifier, warmth: Float) {
    val onBg = MaterialTheme.colorScheme.onBackground
    val sec = MaterialTheme.colorScheme.secondary
    val grow by rememberInfiniteTransition(label = "bars").animateFloat(
        0f, 1f,
        infiniteRepeatable(
            tween(1800, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
            initialStartOffset = StartOffset(400),
        ),
        label = "grow",
    )
    val peak = lerp(onBg, FocusWarm, warmth.coerceIn(0f, 1f))
    Canvas(modifier) {
        val n = 7
        val bw = 13.dp.toPx()
        val gap = 12.dp.toPx()
        val totalW = n * bw + (n - 1) * gap
        val baseY = center.y + 48.dp.toPx()
        val maxBar = 96.dp.toPx()
        val heights = floatArrayOf(0.35f, 0.55f, 0.4f, 0.72f, 0.5f, 0.9f, 0.62f)
        val startX = (size.width - totalW) / 2f
        drawLine(
            sec.copy(alpha = 0.4f),
            Offset(startX - 8.dp.toPx(), baseY),
            Offset(startX + totalW + 8.dp.toPx(), baseY),
            strokeWidth = 1.dp.toPx(),
        )
        for (i in 0 until n) {
            val hh = maxBar * heights[i] * (0.6f + 0.4f * grow)
            val x = startX + i * (bw + gap)
            val col = if (i == 5) peak else onBg.copy(alpha = 0.8f)
            drawRoundRect(
                color = col,
                topLeft = Offset(x, baseY - hh),
                size = Size(bw, hh),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }
    }
}
