package com.martin.foco.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A widget placed by the user, stacked vertically in the widgets screen. */
@Serializable
data class WidgetPlacement(
    val appWidgetId: Int,
    val heightDp: Int = 0, // 0 = use the provider's default height
)

private val Context.widgetsDataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_widgets")

/** Persists the list of placed widgets as JSON in DataStore. */
class WidgetsRepository(private val context: Context) {

    private object Keys {
        val WIDGETS = stringPreferencesKey("widgets") // JSON List<WidgetPlacement>
    }

    val widgets: Flow<List<WidgetPlacement>> = context.widgetsDataStore.data.map { prefs ->
        prefs.decodeWidgets()
    }

    /** Overwrites the whole widget list (used by backup restore). */
    suspend fun replaceAll(list: List<WidgetPlacement>) = context.widgetsDataStore.edit { prefs ->
        prefs.store(list)
    }

    suspend fun add(placement: WidgetPlacement) = context.widgetsDataStore.edit { prefs ->
        val list = prefs.decodeWidgets()
            .filter { it.appWidgetId != placement.appWidgetId } + placement
        prefs.store(list)
    }

    suspend fun remove(appWidgetId: Int) = context.widgetsDataStore.edit { prefs ->
        prefs.store(prefs.decodeWidgets().filter { it.appWidgetId != appWidgetId })
    }

    suspend fun setHeight(appWidgetId: Int, heightDp: Int) = context.widgetsDataStore.edit { prefs ->
        prefs.store(
            prefs.decodeWidgets().map {
                if (it.appWidgetId == appWidgetId) it.copy(heightDp = heightDp) else it
            }
        )
    }

    /** Moves a widget one position up/down in the list order. */
    suspend fun move(appWidgetId: Int, up: Boolean) = context.widgetsDataStore.edit { prefs ->
        val list = prefs.decodeWidgets().toMutableList()
        val from = list.indexOfFirst { it.appWidgetId == appWidgetId }
        if (from < 0) return@edit
        val to = if (up) from - 1 else from + 1
        if (to !in list.indices) return@edit
        list[from] = list[to].also { list[to] = list[from] }
        prefs.store(list)
    }

    private fun Preferences.decodeWidgets(): List<WidgetPlacement> =
        this[Keys.WIDGETS]?.let { runCatching { Json.decodeFromString<List<WidgetPlacement>>(it) }.getOrNull() }
            ?: emptyList()

    private fun MutablePreferences.store(list: List<WidgetPlacement>) {
        this[Keys.WIDGETS] = Json.encodeToString(list)
    }
}
