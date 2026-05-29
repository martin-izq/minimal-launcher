package com.martin.minimallauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_settings")

/** Estado completo de configuración del launcher. */
data class LauncherSettings(
    val favorites: List<String> = emptyList(),     // orden importa
    val hidden: Set<String> = emptySet(),
    val distracting: Set<String> = emptySet(),
    val renames: Map<String, String> = emptyMap(),
    val showClock: Boolean = true,
    val showDate: Boolean = true,
    val showBattery: Boolean = false,
    val showScreenTimeHome: Boolean = true,
    val frictionEnabled: Boolean = true,
    val frictionSeconds: Int = 5,
    val amoledDark: Boolean = true,
    // Personalización de la pantalla de inicio
    val clockSize: Int = 64,          // tamaño del reloj en sp
    val dateSize: Int = 16,           // tamaño de la fecha en sp
    val favoritesSize: Int = 18,      // tamaño de los favoritos en sp
    val homeAlign: Int = 0,           // 0 = izquierda, 1 = centro, 2 = derecha
    val verticalPos: Int = 0,         // 0 = arriba, 1 = centro, 2 = abajo
    val clockOpensAlarms: Boolean = true,
    val widgetsOnLeft: Boolean = true, // lado de la pantalla de widgets respecto al inicio
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val FAVORITES = stringPreferencesKey("favorites")            // pkgs unidos por \n (mantiene orden)
        val HIDDEN = stringSetPreferencesKey("hidden")
        val DISTRACTING = stringSetPreferencesKey("distracting")
        val RENAMES = stringPreferencesKey("renames")                // JSON Map<String,String>
        val SHOW_CLOCK = booleanPreferencesKey("show_clock")
        val SHOW_DATE = booleanPreferencesKey("show_date")
        val SHOW_BATTERY = booleanPreferencesKey("show_battery")
        val SHOW_ST_HOME = booleanPreferencesKey("show_st_home")
        val FRICTION = booleanPreferencesKey("friction_enabled")
        val FRICTION_SECONDS = intPreferencesKey("friction_seconds")
        val AMOLED = booleanPreferencesKey("amoled_dark")
        val CLOCK_SIZE = intPreferencesKey("clock_size")
        val DATE_SIZE = intPreferencesKey("date_size")
        val FAVORITES_SIZE = intPreferencesKey("favorites_size")
        val HOME_ALIGN = intPreferencesKey("home_align")
        val VERTICAL_POS = intPreferencesKey("vertical_pos")
        val CLOCK_OPENS_ALARMS = booleanPreferencesKey("clock_opens_alarms")
        val WIDGETS_ON_LEFT = booleanPreferencesKey("widgets_on_left")
    }

    val settings: Flow<LauncherSettings> = context.dataStore.data.map { p ->
        LauncherSettings(
            favorites = p[Keys.FAVORITES]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList(),
            hidden = p[Keys.HIDDEN] ?: emptySet(),
            distracting = p[Keys.DISTRACTING] ?: emptySet(),
            renames = p[Keys.RENAMES]?.let { decodeRenames(it) } ?: emptyMap(),
            showClock = p[Keys.SHOW_CLOCK] ?: true,
            showDate = p[Keys.SHOW_DATE] ?: true,
            showBattery = p[Keys.SHOW_BATTERY] ?: false,
            showScreenTimeHome = p[Keys.SHOW_ST_HOME] ?: true,
            frictionEnabled = p[Keys.FRICTION] ?: true,
            frictionSeconds = p[Keys.FRICTION_SECONDS] ?: 5,
            amoledDark = p[Keys.AMOLED] ?: true,
            clockSize = p[Keys.CLOCK_SIZE] ?: 64,
            dateSize = p[Keys.DATE_SIZE] ?: 16,
            favoritesSize = p[Keys.FAVORITES_SIZE] ?: 18,
            homeAlign = p[Keys.HOME_ALIGN] ?: 0,
            verticalPos = p[Keys.VERTICAL_POS] ?: 0,
            clockOpensAlarms = p[Keys.CLOCK_OPENS_ALARMS] ?: true,
            widgetsOnLeft = p[Keys.WIDGETS_ON_LEFT] ?: true,
        )
    }

    suspend fun toggleFavorite(pkg: String) = context.dataStore.edit { p ->
        val list = p[Keys.FAVORITES]?.split("\n")?.filter { it.isNotBlank() }?.toMutableList()
            ?: mutableListOf()
        if (!list.remove(pkg)) list.add(pkg)
        p[Keys.FAVORITES] = list.joinToString("\n")
    }

    suspend fun moveFavorite(pkg: String, up: Boolean) = context.dataStore.edit { p ->
        val list = p[Keys.FAVORITES]?.split("\n")?.filter { it.isNotBlank() }?.toMutableList()
            ?: return@edit
        val i = list.indexOf(pkg)
        if (i < 0) return@edit
        val j = if (up) i - 1 else i + 1
        if (j < 0 || j >= list.size) return@edit
        list[i] = list[j].also { list[j] = list[i] }
        p[Keys.FAVORITES] = list.joinToString("\n")
    }

    suspend fun setHidden(pkg: String, hidden: Boolean) = context.dataStore.edit { p ->
        val set = (p[Keys.HIDDEN] ?: emptySet()).toMutableSet()
        if (hidden) set.add(pkg) else set.remove(pkg)
        p[Keys.HIDDEN] = set
    }

    suspend fun setDistracting(pkg: String, distracting: Boolean) = context.dataStore.edit { p ->
        val set = (p[Keys.DISTRACTING] ?: emptySet()).toMutableSet()
        if (distracting) set.add(pkg) else set.remove(pkg)
        p[Keys.DISTRACTING] = set
    }

    suspend fun rename(pkg: String, newName: String) = context.dataStore.edit { p ->
        val map = (p[Keys.RENAMES]?.let { decodeRenames(it) } ?: emptyMap()).toMutableMap()
        if (newName.isBlank()) map.remove(pkg) else map[pkg] = newName.trim()
        p[Keys.RENAMES] = Json.encodeToString(map)
    }

    suspend fun setShowClock(v: Boolean) = putBool(Keys.SHOW_CLOCK, v)
    suspend fun setShowDate(v: Boolean) = putBool(Keys.SHOW_DATE, v)
    suspend fun setShowBattery(v: Boolean) = putBool(Keys.SHOW_BATTERY, v)
    suspend fun setShowScreenTimeHome(v: Boolean) = putBool(Keys.SHOW_ST_HOME, v)
    suspend fun setFrictionEnabled(v: Boolean) = putBool(Keys.FRICTION, v)
    suspend fun setAmoled(v: Boolean) = putBool(Keys.AMOLED, v)
    suspend fun setFrictionSeconds(v: Int) = context.dataStore.edit { it[Keys.FRICTION_SECONDS] = v }
    suspend fun setClockSize(v: Int) = context.dataStore.edit { it[Keys.CLOCK_SIZE] = v }
    suspend fun setDateSize(v: Int) = context.dataStore.edit { it[Keys.DATE_SIZE] = v }
    suspend fun setFavoritesSize(v: Int) = context.dataStore.edit { it[Keys.FAVORITES_SIZE] = v }
    suspend fun setHomeAlign(v: Int) = context.dataStore.edit { it[Keys.HOME_ALIGN] = v }
    suspend fun setVerticalPos(v: Int) = context.dataStore.edit { it[Keys.VERTICAL_POS] = v }
    suspend fun setClockOpensAlarms(v: Boolean) = putBool(Keys.CLOCK_OPENS_ALARMS, v)
    suspend fun setWidgetsOnLeft(v: Boolean) = putBool(Keys.WIDGETS_ON_LEFT, v)

    private suspend fun putBool(key: Preferences.Key<Boolean>, v: Boolean) =
        context.dataStore.edit { it[key] = v }

    private fun decodeRenames(raw: String): Map<String, String> =
        runCatching { Json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
}
