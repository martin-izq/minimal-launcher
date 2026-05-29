package com.martin.minimallauncher.ui.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.staticCompositionLocalOf
import com.martin.minimallauncher.data.WidgetPlacement

/**
 * Orquesta el alojamiento de widgets de terceros: ciclo de vida del [AppWidgetHost],
 * binding (con consentimiento del usuario) y pantalla de configuración del proveedor.
 *
 * Debe construirse en [ComponentActivity.onCreate] (antes de onStart) porque registra
 * launchers de ActivityResult.
 */
class WidgetController(
    private val activity: ComponentActivity,
    private val appWidgetManager: AppWidgetManager,
    private val onBound: (WidgetPlacement) -> Unit,
) {
    val host = AppWidgetHost(activity, HOST_ID)

    private var pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingProvider: AppWidgetProviderInfo? = null

    private val bindLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) proceedToConfigure() else cancelPending()
    }

    fun startListening() = host.startListening()
    fun stopListening() = host.stopListening()

    /** Lista de proveedores de widgets instalados. */
    fun installedProviders(): List<AppWidgetProviderInfo> = appWidgetManager.installedProviders

    /** Inicia el flujo: reservar id → bind → (config) → persistir. */
    fun startAddWidgetFlow(provider: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pendingId = id
        pendingProvider = provider
        val allowed = appWidgetManager.bindAppWidgetIdIfAllowed(id, provider.provider)
        if (allowed) {
            proceedToConfigure()
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            }
            bindLauncher.launch(intent)
        }
    }

    private fun proceedToConfigure() {
        val provider = pendingProvider ?: return cancelPending()
        if (provider.configure != null) {
            try {
                host.startAppWidgetConfigureActivityForResult(activity, pendingId, 0, REQ_CONFIGURE, null)
            } catch (_: Exception) {
                // El proveedor no permite abrir su config directamente: colocamos igual el widget.
                finishPending()
            }
        } else {
            finishPending()
        }
    }

    /** Lo llama MainActivity desde onActivityResult para el paso de configuración. */
    fun handleConfigureResult(requestCode: Int, resultCode: Int) {
        if (requestCode != REQ_CONFIGURE) return
        if (resultCode == Activity.RESULT_OK) finishPending() else cancelPending()
    }

    private fun finishPending() {
        if (pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            onBound(WidgetPlacement(appWidgetId = pendingId))
        }
        clearPending()
    }

    private fun cancelPending() {
        if (pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) host.deleteAppWidgetId(pendingId)
        clearPending()
    }

    private fun clearPending() {
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingProvider = null
    }

    /** Libera el id del widget al quitarlo. */
    fun removeWidget(appWidgetId: Int) = host.deleteAppWidgetId(appWidgetId)

    /** Crea la vista nativa del widget, o null si el id ya no es válido. */
    fun createHostView(appWidgetId: Int): AppWidgetHostView? {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return null
        return host.createView(activity, appWidgetId, info)
    }

    companion object {
        const val HOST_ID = 0x4D4C // "ML"
        private const val REQ_CONFIGURE = 9001
    }
}

/** Disponible para los composables que necesitan alojar/agregar widgets. */
val LocalWidgetController = staticCompositionLocalOf<WidgetController?> { null }
