package com.martin.minimallauncher.ui.widgets

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
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
private class WidgetFrame(context: Context) : FrameLayout(context) {
    var grabTouches: Boolean = true

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (grabTouches) parent?.requestDisallowInterceptTouchEvent(true)
        return false // no interceptamos: el widget hijo recibe el gesto
    }
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
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val widthDp = (configuration.screenWidthDp - 32).coerceAtLeast(80)

    AndroidView(
        factory = { ctx ->
            val frame = WidgetFrame(ctx)
            val host = controller.createHostView(appWidgetId) ?: AppWidgetHostView(ctx)
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
