package com.martin.foco.ui.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.staticCompositionLocalOf
import com.martin.foco.data.WidgetPlacement

/**
 * Orchestrates hosting third-party widgets: [AppWidgetHost] lifecycle, binding (with the
 * user's consent) and the provider's configuration screen.
 *
 * Must be built in [ComponentActivity.onCreate] (before onStart) because it registers
 * ActivityResult launchers.
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

    /** List of installed widget providers. */
    fun installedProviders(): List<AppWidgetProviderInfo> = appWidgetManager.installedProviders

    /** Starts the flow: allocate id → bind → (configure) → persist. */
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
                // The provider doesn't allow opening its config directly: place the widget anyway.
                finishPending()
            }
        } else {
            finishPending()
        }
    }

    /** Called by MainActivity from onActivityResult for the configuration step. */
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

    // Cache views by id: recreating them when returning from another page leaves the
    // widget blank until the next update; reusing them keeps what was already rendered.
    private val viewCache = mutableMapOf<Int, AppWidgetHostView>()

    /** Releases the widget's id when removed. */
    fun removeWidget(appWidgetId: Int) {
        viewCache.remove(appWidgetId)
        host.deleteAppWidgetId(appWidgetId)
    }

    /** Returns the (cached) widget view, or null if the id is no longer valid. */
    fun obtainHostView(appWidgetId: Int): AppWidgetHostView? {
        viewCache[appWidgetId]?.let { return it }
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return null
        return host.createView(activity, appWidgetId, info).also { viewCache[appWidgetId] = it }
    }

    companion object {
        const val HOST_ID = 0x4D4C // "ML"
        private const val REQ_CONFIGURE = 9001
    }
}

/** Available to composables that need to host/add widgets. */
val LocalWidgetController = staticCompositionLocalOf<WidgetController?> { null }
