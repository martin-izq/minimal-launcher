package com.martin.minimallauncher.ui.widgets

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import kotlin.math.abs
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Contenedor que, al recibir un toque, le pide al padre (el scroll de Compose) que
 * no intercepte el gesto, de modo que el widget pueda scrollear internamente. Cuando
 * [grabTouches] es false (modo edición) deja pasar el gesto al overlay de Compose.
 */
private class WidgetFrame(
    context: Context,
    private val onLongPress: () -> Unit,
) : FrameLayout(context) {
    var grabTouches: Boolean = true

    // Detección propia de mantener-pulsado con un umbral más largo (más amigable
    // que el ~500 ms del sistema), sin robarle los toques/scroll al widget.
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private val longPressRunnable = Runnable { if (grabTouches) onLongPress() }

    private val longPressTimeoutMs = 2000L

    // Solo le cedemos el gesto al widget si su contenido scrollea; si no, dejamos
    // que la página de widgets scrollee normalmente.
    private var widgetScrollable = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (grabTouches) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x
                    downY = ev.y
                    handler.removeCallbacks(longPressRunnable)
                    handler.postDelayed(longPressRunnable, longPressTimeoutMs)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(ev.x - downX) > touchSlop || abs(ev.y - downY) > touchSlop) {
                        handler.removeCallbacks(longPressRunnable)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                }
            }
        } else {
            handler.removeCallbacks(longPressRunnable)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (grabTouches && ev?.actionMasked == MotionEvent.ACTION_DOWN) {
            widgetScrollable = getChildAt(0)?.let { hasScrollableContent(it) } ?: false
        }
        if (grabTouches && widgetScrollable) parent?.requestDisallowInterceptTouchEvent(true)
        return false // no interceptamos: el widget hijo recibe el gesto
    }
}

/** Detecta si la jerarquía del widget contiene una vista con scroll propio. */
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
 * Renderiza un widget alojado dentro de Compose, a la altura indicada (en dp).
 * El ancho ocupa todo el disponible. Cuando [scrollable] es true, el widget se
 * queda con los gestos verticales para poder scrollear su contenido.
 */
@Composable
fun WidgetHostViewItem(
    appWidgetId: Int,
    controller: WidgetController,
    heightDp: Int,
    scrollable: Boolean = true,
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val widthDp = (configuration.screenWidthDp - 32).coerceAtLeast(80)

    AndroidView(
        factory = { ctx ->
            val frame = WidgetFrame(ctx, onLongPress)
            val host = controller.obtainHostView(appWidgetId) ?: AppWidgetHostView(ctx)
            // La vista cacheada puede seguir adjunta a un frame anterior: la despegamos.
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
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp),
    )
}

/**
 * Informa al widget su tamaño real. En Android 12+ usa OPTION_APPWIDGET_SIZES, que es
 * lo que los widgets responsivos (calendario, Google Home, etc.) leen para elegir el
 * layout correcto; sin esto se quedan en su layout mínimo.
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
