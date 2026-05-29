package com.martin.minimallauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
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

/** Un widget colocado por el usuario. Los campos de celda/span se usan desde la Fase 2. */
@Serializable
data class WidgetPlacement(
    val appWidgetId: Int,
    val page: Int = 0,
    val cellX: Int = 0,
    val cellY: Int = 0,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val heightDp: Int = 0, // 0 = usar la altura por defecto del proveedor
)

private val Context.widgetsDataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_widgets")

/** Persiste la lista de widgets colocados (como JSON en DataStore). */
class WidgetsRepository(private val context: Context) {

    private object Keys {
        val WIDGETS = stringPreferencesKey("widgets") // JSON List<WidgetPlacement>
    }

    val widgets: Flow<List<WidgetPlacement>> = context.widgetsDataStore.data.map { p ->
        p[Keys.WIDGETS]?.let { decode(it) } ?: emptyList()
    }

    suspend fun add(placement: WidgetPlacement) = context.widgetsDataStore.edit { p ->
        val list = (p[Keys.WIDGETS]?.let { decode(it) } ?: emptyList())
            .filter { it.appWidgetId != placement.appWidgetId } + placement
        p[Keys.WIDGETS] = Json.encodeToString(list)
    }

    suspend fun remove(appWidgetId: Int) = context.widgetsDataStore.edit { p ->
        val list = (p[Keys.WIDGETS]?.let { decode(it) } ?: emptyList())
            .filter { it.appWidgetId != appWidgetId }
        p[Keys.WIDGETS] = Json.encodeToString(list)
    }

    suspend fun updatePlacement(placement: WidgetPlacement) = add(placement)

    suspend fun setHeight(appWidgetId: Int, heightDp: Int) = context.widgetsDataStore.edit { p ->
        val list = (p[Keys.WIDGETS]?.let { decode(it) } ?: emptyList()).map {
            if (it.appWidgetId == appWidgetId) it.copy(heightDp = heightDp) else it
        }
        p[Keys.WIDGETS] = Json.encodeToString(list)
    }

    /** Mueve un widget una posición arriba/abajo en el orden de la lista. */
    suspend fun move(appWidgetId: Int, up: Boolean) = context.widgetsDataStore.edit { p ->
        val list = (p[Keys.WIDGETS]?.let { decode(it) } ?: emptyList()).toMutableList()
        val i = list.indexOfFirst { it.appWidgetId == appWidgetId }
        if (i < 0) return@edit
        val j = if (up) i - 1 else i + 1
        if (j < 0 || j >= list.size) return@edit
        list[i] = list[j].also { list[j] = list[i] }
        p[Keys.WIDGETS] = Json.encodeToString(list)
    }

    private fun decode(raw: String): List<WidgetPlacement> =
        runCatching { Json.decodeFromString<List<WidgetPlacement>>(raw) }.getOrDefault(emptyList())
}
