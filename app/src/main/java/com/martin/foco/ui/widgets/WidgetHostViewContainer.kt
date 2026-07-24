package com.martin.foco.ui.widgets

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.AdapterViewFlipper
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ListView
import android.widget.ScrollView
import android.widget.StackView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.abs

/**
 * Container that, on touch, asks its parent (Compose's scroll) not to intercept the
 * gesture so the widget can scroll internally. When [grabTouches] is false (edit mode)
 * it lets the gesture pass through to the Compose overlay.
 */
private class WidgetFrame(context: Context) : FrameLayout(context) {
    var grabTouches: Boolean = true

    /**
     * Set while the widget must not react to touches — edit mode, or a menu opened on top of it.
     * Intercepting cancels the child's pending tap; staying unhandled in [onTouchEvent] then lets
     * the gesture fall through to Compose, so the page keeps scrolling.
     */
    var blockTouches: Boolean = false

    // Last size reported to the widget, so we only push options when it actually changes:
    // updateAppWidgetOptions fires onAppWidgetOptionsChanged, and doing that every frame makes
    // some widgets flicker or re-render from scratch.
    var lastWidthDp: Int = 0
    var lastHeightDp: Int = 0

    // Only yield the gesture to the widget if its content scrolls; otherwise let the
    // widgets page scroll normally.
    private var widgetScrollable = false
    private var downX = 0f
    private var downY = 0f
    private var decided = false

    // Deliberately *half* the touch slop. Compose's scroll claims the gesture at the full slop, so
    // deciding at the same threshold is a tie whose winner depends on dispatch timing — which is
    // why scrollable widgets (Gmail, agenda) would stop scrolling after unrelated changes nearby.
    // Half means the widget always calls dibs first; a horizontal drag still releases in time for
    // the pager, which only starts at the full slop.
    private val decideSlop =
        (ViewConfiguration.get(context).scaledTouchSlop / 2).coerceAtLeast(2)

    // A scrolling widget should only own gestures along ITS scroll axis (vertical). Horizontal
    // drags must still reach the Compose pager so the user can swipe between screens over the
    // widget — so decide per-gesture from the drag direction instead of grabbing everything on DOWN.
    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        ev ?: return false
        if (blockTouches) return true
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                decided = false
                widgetScrollable = grabTouches && (getChildAt(0)?.let { hasScrollableContent(it) } ?: false)
                // Let ancestors decide until we know the direction.
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_MOVE -> {
                if (widgetScrollable && !decided) {
                    val dx = abs(ev.x - downX)
                    val dy = abs(ev.y - downY)
                    if (dx > decideSlop || dy > decideSlop) {
                        decided = true
                        // Vertical → the widget keeps it; horizontal → hand it to the pager.
                        parent?.requestDisallowInterceptTouchEvent(dy >= dx)
                    }
                }
            }
        }
        return false
    }
}

/** Detects whether the widget hierarchy contains a self-scrolling view. */
private fun hasScrollableContent(view: View): Boolean {
    if (view is ListView || view is GridView || view is ScrollView ||
        view is HorizontalScrollView || view is StackView || view is AdapterViewFlipper
    ) {
        return true
    }
    if (view.javaClass.name.contains("RecyclerView")) return true
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            if (hasScrollableContent(view.getChildAt(i))) return true
        }
    }
    return false
}

/**
 * Renders a widget hosted inside Compose at the given height (in dp). The width fills
 * the available space. When [scrollable] is true, the widget keeps vertical gestures so
 * it can scroll its own content.
 *
 * [heightDp] must already be within what the provider supports — see [widgetSizing].
 */
@Composable
fun WidgetHostViewItem(
    appWidgetId: Int,
    controller: WidgetController,
    heightDp: Int,
    scrollable: Boolean = true,
    interactive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // The real laid-out width: guessing it from the screen ignores padding, insets and landscape,
    // and reporting a width the widget doesn't have breaks its layout choice. Measured with
    // onSizeChanged rather than BoxWithConstraints on purpose — the latter is a SubcomposeLayout,
    // and a re-subcomposition rebuilds the AndroidView, which re-parents the hosted view and kills
    // any touch in flight (widgets stopped scrolling mid-gesture).
    var widthDp by remember { mutableIntStateOf(0) }

    AndroidView(
        factory = { ctx ->
            val frame = WidgetFrame(ctx)
            val host = controller.obtainHostView(appWidgetId) ?: AppWidgetHostView(ctx)
            // The cached view may still be attached to a previous frame: detach it.
            (host.parent as? ViewGroup)?.removeView(host)
            frame.addView(
                host,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            frame
        },
        update = { frame ->
            (frame as WidgetFrame).grabTouches = scrollable
            frame.blockTouches = !interactive
            (frame.getChildAt(0) as? AppWidgetHostView)?.let { host ->
                if (widthDp > 0 && (frame.lastWidthDp != widthDp || frame.lastHeightDp != heightDp)) {
                    frame.lastWidthDp = widthDp
                    frame.lastHeightDp = heightDp
                    applyWidgetSize(host, appWidgetId, widthDp, heightDp)
                }
                // After the cached view is re-attached to a fresh frame, its children can
                // stay collapsed (size 0) until something forces a new layout pass — the
                // widget then looks invisible even though it occupies space. Forcing a
                // layout/redraw here (and once more on the next frame, when it's attached to
                // the window) reproduces what a manual resize did to recover it.
                host.requestLayout()
                host.invalidate()
                host.post {
                    host.requestLayout()
                    host.invalidate()
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .onSizeChanged { size ->
                val measured = with(density) { size.width.toDp().value.toInt() }
                if (measured > 0) widthDp = measured
            },
    )
}

/**
 * Tells the widget its real size. On Android 12+ it uses OPTION_APPWIDGET_SIZES, which is
 * what responsive widgets (calendar, Google Home, etc.) read to pick the right layout;
 * without it they stay in their minimum layout, and if the size doesn't fit any layout they
 * provide they render their "can't display content" state instead.
 *
 * Only call this when the size actually changed — see [WidgetFrame.lastHeightDp].
 */
private fun applyWidgetSize(host: AppWidgetHostView, appWidgetId: Int, widthDp: Int, heightDp: Int) {
    val mgr = AppWidgetManager.getInstance(host.context)
    val options = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            putParcelableArrayList(
                AppWidgetManager.OPTION_APPWIDGET_SIZES,
                arrayListOf(SizeF(widthDp.toFloat(), heightDp.toFloat())),
            )
        }
    }
    runCatching { mgr.updateAppWidgetOptions(appWidgetId, options) }
    runCatching { host.updateAppWidgetSize(options, widthDp, heightDp, widthDp, heightDp) }
}
