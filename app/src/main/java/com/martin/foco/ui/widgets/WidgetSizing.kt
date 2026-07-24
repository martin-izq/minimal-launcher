package com.martin.foco.ui.widgets

import android.appwidget.AppWidgetProviderInfo
import android.os.Build

// Used when the provider info is gone (stale id) — nothing to read bounds from.
private const val FALLBACK_DEFAULT_DP = 110
private const val FALLBACK_MIN_DP = 40

// A launcher grid row is roughly this tall. Only used to turn the API 31+ cell count into dp;
// the result is clamped to the provider's real bounds anyway, so the estimate is harmless.
private const val CELL_HEIGHT_DP = 100

// Below this the range isn't worth a drag handle: the widget really only has one size.
private const val RESIZABLE_RANGE_DP = 8

/**
 * The heights a widget provider supports, in dp.
 *
 * A widget only renders at the sizes it declares: stretched past its `maxResizeHeight` it has no
 * matching RemoteViews to apply and falls back to its "can't display content" state. These bounds
 * are the single source of truth for both the resize UI and the size we report to the widget.
 *
 * Note this deliberately does *not* lock a widget whose `resizeMode` omits `RESIZE_VERTICAL`:
 * doing so strands it at whatever height we guessed, with no way for the user to correct it.
 * `resizeMode` only tightens the fallback cap — see [widgetSizing].
 */
data class WidgetSizing(
    val defaultHeightDp: Int,
    val minHeightDp: Int,
    val maxHeightDp: Int,
    val resizable: Boolean,
) {
    /** Clamps a stored or dragged height into the range the provider supports. */
    fun clamp(heightDp: Int): Int = heightDp.coerceIn(minHeightDp, maxHeightDp)

    /** The height to render at: [clamp]ed, falling back to the default when nothing is stored. */
    fun resolve(storedHeightDp: Int): Int =
        clamp(if (storedHeightDp > 0) storedHeightDp else defaultHeightDp)
}

/**
 * Reads the height bounds a provider declares. [info] is null when the widget id no longer
 * resolves (app uninstalled), in which case a permissive fallback keeps the row usable.
 *
 * `minHeight` / `min|maxResizeHeight` come from the manifest in **pixels**, hence [density].
 */
fun widgetSizing(
    info: AppWidgetProviderInfo?,
    density: Float,
    screenHeightDp: Int,
): WidgetSizing {
    val screenMax = (screenHeightDp * 0.85f).toInt().coerceAtLeast(FALLBACK_MIN_DP)
    if (info == null) {
        return WidgetSizing(
            defaultHeightDp = FALLBACK_DEFAULT_DP,
            minHeightDp = FALLBACK_MIN_DP,
            maxHeightDp = screenMax,
            resizable = true,
        )
    }

    fun Int.toDp() = (this / density).toInt()

    val natural = info.minHeight.toDp().coerceAtLeast(FALLBACK_MIN_DP)
    val min = info.minResizeHeight.takeIf { it > 0 }?.toDp()?.coerceAtLeast(FALLBACK_MIN_DP) ?: natural

    val declaredMax = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        info.maxResizeHeight.takeIf { it > 0 }?.toDp()
    } else {
        null
    }
    // With no declared maximum, a widget that says it isn't vertically resizable still gets room
    // to be corrected — just not enough rope to be stretched back into its error state.
    val fallbackMax = if (info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0) {
        screenMax
    } else {
        (natural * 2).coerceAtMost(screenMax)
    }
    // Never let the screen cap raise the floor: a widget taller than the screen still needs min <= max.
    val max = maxOf(min, minOf(declaredMax ?: fallbackMax, screenMax))

    // targetCellHeight is the size the provider is designed for; minHeight is only its floor.
    val target = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        info.targetCellHeight.takeIf { it > 0 }?.let { it * CELL_HEIGHT_DP }
    } else {
        null
    }

    return WidgetSizing(
        defaultHeightDp = (target ?: natural).coerceIn(min, max),
        minHeightDp = min,
        maxHeightDp = max,
        resizable = max - min >= RESIZABLE_RANGE_DP,
    )
}
