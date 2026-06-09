package com.martin.minimallauncher.ui.widgets

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.MotionEvent
import android.view.View
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Container that, on touch, asks its parent (Compose's scroll) not to intercept the
 * gesture so the widget can scroll internally. When [grabTouches] is false (edit mode)
 * it lets the gesture pass through to the Compose overlay.
 */
private class WidgetFrame(context: Context) : FrameLayout(context) {
    var grabTouches: Boolean = true

    // Only yield the gesture to the widget if its content scrolls; otherwise let the
    // widgets page scroll normally.
    private var widgetScrollable = false

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (grabTouches && ev?.actionMasked == MotionEvent.ACTION_DOWN) {
            widgetScrollable = getChildAt(0)?.let { hasScrollableContent(it) } ?: false
        }
        if (grabTouches && widgetScrollable) parent?.requestDisallowInterceptTouchEvent(true)
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
 */
@Composable
fun WidgetHostViewItem(
    appWidgetId: Int,
    controller: WidgetController,
    heightDp: Int,
    scrollable: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val widthDp = (configuration.screenWidthDp - 32).coerceAtLeast(80)

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
            (frame.getChildAt(0) as? AppWidgetHostView)?.let { host ->
                applyWidgetSize(host, appWidgetId, widthDp, heightDp)
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
            .height(heightDp.dp),
    )
}

/**
 * Tells the widget its real size. On Android 12+ it uses OPTION_APPWIDGET_SIZES, which is
 * what responsive widgets (calendar, Google Home, etc.) read to pick the right layout;
 * without it they stay in their minimum layout.
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
