package com.martin.foco.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_settings")

/** Full launcher configuration state. */
@Serializable
data class LauncherSettings(
    val favorites: List<String> = emptyList(),     // order matters
    val hidden: Set<String> = emptySet(),
    val distracting: Set<String> = emptySet(),
    val renames: Map<String, String> = emptyMap(),
    val appLimits: Map<String, Int> = emptyMap(), // package -> daily limit in minutes
    val focusSessions: List<FocusSession> = emptyList(),
    val manualFocusUntil: Long = 0L,   // epoch ms; focus forced ON while now < this (MAX = indefinite)
    val focusSkipUntil: Long = 0L,      // epoch ms; scheduled sessions suppressed while now < this
    val showFocusOnHome: Boolean = true, // quick focus chip on the home screen
    val dndInFocus: Boolean = true,     // turn on Do Not Disturb during focus
    val strictFocus: Boolean = false,   // once started with a fixed time, focus can't be exited early
    val showClock: Boolean = true,
    val showDate: Boolean = true,
    val showBattery: Boolean = true,
    val showScreenTimeHome: Boolean = true,
    val frictionEnabled: Boolean = true,
    val frictionSeconds: Int = 10,
    val amoledDark: Boolean = true,
    val accentColor: Int = 0, // index into AccentColors; 0 = monochrome
    // Home screen customization
    val clockSize: Int = 95,          // clock size in sp
    val dateSize: Int = 22,           // date size in sp
    val favoritesSize: Int = 36,      // favorites size in sp
    val homeAlign: Int = 0,           // 0 = left, 1 = center, 2 = right
    val verticalPos: Int = 2,         // 0 = top, 1 = center, 2 = bottom
    val clockOpensAlarms: Boolean = true,
    val hideStatusBar: Boolean = false,
    val showNotificationBadges: Boolean = false,
    // Gesture directions (0 = left, 1 = right, 2 = up). Always a permutation of {0,1,2};
    // the remaining free direction "down" is reserved for the notification shade.
    // Default: widgets left, drawer right, quick-launch up.
    val widgetsDir: Int = DIR_LEFT,
    val quickLaunchDir: Int = DIR_UP,
    val drawerDir: Int = DIR_RIGHT,
    // App drawer
    val appDrawerSize: Int = 23,       // app label size in sp
    val appDrawerAlign: Int = 0,       // 0 = left, 1 = center, 2 = right
    val alphabetIndex: Boolean = true, // alphabet scrubber on the side
    val scrubberWidth: Int = 49,       // touch band width of the scrubber, in dp
    val searchBarBottom: Boolean = false, // false = search bar on top, true = bottom
    val drawerTopSpace: Int = 152,     // extra space above the drawer content, in dp
    val drawerShowTitle: Boolean = true,
    val drawerTitle: String = "",      // blank → localized default "Apps"
    val drawerShowUsage: Boolean = false,
    // Quick-launch app (swipe toward quickLaunchDir)
    val quickLaunchPackage: String? = null,
    // First-run onboarding completed?
    val onboarded: Boolean = false,
    // Full ("Pro") entitlement. Not part of a backup: it's an entitlement, not a preference.
    @Transient val pro: Boolean = false,
) {
    companion object {
        const val DIR_LEFT = 0
        const val DIR_RIGHT = 1
        const val DIR_UP = 2
    }
}

/**
 * The Full-only preferences (customization/convenience). Light keeps the core focus product but
 * pins these to their defaults. Applied at read time as defense-in-depth so a stale value or an
 * imported backup can never bypass the lock — it never rewrites the stored values, so they return
 * intact once Full is unlocked.
 */
fun LauncherSettings.gated(isPro: Boolean): LauncherSettings {
    if (isPro) return this
    val d = LauncherSettings()
    return copy(
        hideStatusBar = d.hideStatusBar,
        showNotificationBadges = d.showNotificationBadges,
        widgetsDir = d.widgetsDir,
        quickLaunchDir = d.quickLaunchDir,
        drawerDir = d.drawerDir,
        quickLaunchPackage = d.quickLaunchPackage,
        accentColor = d.accentColor,
        appDrawerSize = d.appDrawerSize,
        appDrawerAlign = d.appDrawerAlign,
        alphabetIndex = d.alphabetIndex,
        scrubberWidth = d.scrubberWidth,
        searchBarBottom = d.searchBarBottom,
        drawerTopSpace = d.drawerTopSpace,
        drawerShowTitle = d.drawerShowTitle,
        drawerTitle = d.drawerTitle,
        drawerShowUsage = d.drawerShowUsage,
    )
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val FAVORITES = stringPreferencesKey("favorites")            // pkgs joined by \n (keeps order)
        val HIDDEN = stringSetPreferencesKey("hidden")
        val DISTRACTING = stringSetPreferencesKey("distracting")
        val RENAMES = stringPreferencesKey("renames")                // JSON Map<String,String>
        val APP_LIMITS = stringPreferencesKey("app_limits")          // JSON Map<String,Int> (minutes/day)
        val FOCUS_SESSIONS = stringPreferencesKey("focus_sessions")  // JSON List<FocusSession>
        val MANUAL_FOCUS_UNTIL = longPreferencesKey("manual_focus_until")
        val FOCUS_SKIP_UNTIL = longPreferencesKey("focus_skip_until")
        val SHOW_FOCUS_HOME = booleanPreferencesKey("show_focus_home")
        val DND_IN_FOCUS = booleanPreferencesKey("dnd_in_focus")
        val STRICT_FOCUS = booleanPreferencesKey("strict_focus")
        val SHOW_CLOCK = booleanPreferencesKey("show_clock")
        val SHOW_DATE = booleanPreferencesKey("show_date")
        val SHOW_BATTERY = booleanPreferencesKey("show_battery")
        val SHOW_ST_HOME = booleanPreferencesKey("show_st_home")
        val FRICTION = booleanPreferencesKey("friction_enabled")
        val FRICTION_SECONDS = intPreferencesKey("friction_seconds")
        val AMOLED = booleanPreferencesKey("amoled_dark")
        val ACCENT = intPreferencesKey("accent_color")
        val CLOCK_SIZE = intPreferencesKey("clock_size")
        val DATE_SIZE = intPreferencesKey("date_size")
        val FAVORITES_SIZE = intPreferencesKey("favorites_size")
        val HOME_ALIGN = intPreferencesKey("home_align")
        val VERTICAL_POS = intPreferencesKey("vertical_pos")
        val CLOCK_OPENS_ALARMS = booleanPreferencesKey("clock_opens_alarms")
        val HIDE_STATUS_BAR = booleanPreferencesKey("hide_status_bar")
        val NOTIF_BADGES = booleanPreferencesKey("notif_badges")
        val WIDGETS_ON_LEFT = booleanPreferencesKey("widgets_on_left") // legacy, read for migration
        val WIDGETS_DIR = intPreferencesKey("widgets_dir")
        val QUICK_DIR = intPreferencesKey("quick_dir")
        val DRAWER_DIR = intPreferencesKey("drawer_dir")
        val APP_DRAWER_SIZE = intPreferencesKey("app_drawer_size")
        val APP_DRAWER_ALIGN = intPreferencesKey("app_drawer_align")
        val ALPHABET_INDEX = booleanPreferencesKey("alphabet_index")
        val SCRUBBER_WIDTH = intPreferencesKey("scrubber_width")
        val SEARCH_BAR_BOTTOM = booleanPreferencesKey("search_bar_bottom")
        val DRAWER_TOP_SPACE = intPreferencesKey("drawer_top_space")
        val DRAWER_SHOW_TITLE = booleanPreferencesKey("drawer_show_title")
        val DRAWER_TITLE = stringPreferencesKey("drawer_title")
        val DRAWER_SHOW_USAGE = booleanPreferencesKey("drawer_show_usage")
        val QUICK_LAUNCH_PKG = stringPreferencesKey("quick_launch_pkg")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val PRO = booleanPreferencesKey("pro_unlocked")
    }

    /**
     * Reads the three gesture directions and forces them to be a permutation of {left, right, up}.
     * Widgets keeps its preferred slot, then quick-launch, then drawer fills what's left.
     */
    private fun readDirs(p: Preferences): Triple<Int, Int, Int> {
        val legacyLeft = p[Keys.WIDGETS_ON_LEFT]
        // Migration from the old two-side layout kept the drawer "up"; fresh installs default to
        // widgets left, drawer right, quick-launch up.
        val (defW, defQ, defD) = when (legacyLeft) {
            true -> Triple(LauncherSettings.DIR_LEFT, LauncherSettings.DIR_RIGHT, LauncherSettings.DIR_UP)
            false -> Triple(LauncherSettings.DIR_RIGHT, LauncherSettings.DIR_LEFT, LauncherSettings.DIR_UP)
            null -> Triple(LauncherSettings.DIR_LEFT, LauncherSettings.DIR_UP, LauncherSettings.DIR_RIGHT)
        }
        val prefs = intArrayOf(
            p[Keys.WIDGETS_DIR] ?: defW,
            p[Keys.QUICK_DIR] ?: defQ,
            p[Keys.DRAWER_DIR] ?: defD,
        )
        val used = mutableSetOf<Int>()
        val out = IntArray(3)
        for (i in 0..2) {
            var v = prefs[i].coerceIn(0, 2)
            if (v in used) v = (0..2).first { it !in used }
            used.add(v)
            out[i] = v
        }
        return Triple(out[0], out[1], out[2])
    }

    val settings: Flow<LauncherSettings> = context.dataStore.data.map { p ->
        val (wDir, qDir, dDir) = readDirs(p)
        LauncherSettings(
            favorites = p[Keys.FAVORITES]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList(),
            hidden = p[Keys.HIDDEN] ?: emptySet(),
            distracting = p[Keys.DISTRACTING] ?: emptySet(),
            renames = p[Keys.RENAMES]?.let { decodeRenames(it) } ?: emptyMap(),
            appLimits = p[Keys.APP_LIMITS]?.let { decodeLimits(it) } ?: emptyMap(),
            focusSessions = p[Keys.FOCUS_SESSIONS]?.let { decodeSessions(it) } ?: emptyList(),
            manualFocusUntil = p[Keys.MANUAL_FOCUS_UNTIL] ?: 0L,
            focusSkipUntil = p[Keys.FOCUS_SKIP_UNTIL] ?: 0L,
            showFocusOnHome = p[Keys.SHOW_FOCUS_HOME] ?: true,
            dndInFocus = p[Keys.DND_IN_FOCUS] ?: true,
            strictFocus = p[Keys.STRICT_FOCUS] ?: false,
            showClock = p[Keys.SHOW_CLOCK] ?: true,
            showDate = p[Keys.SHOW_DATE] ?: true,
            showBattery = p[Keys.SHOW_BATTERY] ?: true,
            showScreenTimeHome = p[Keys.SHOW_ST_HOME] ?: true,
            frictionEnabled = p[Keys.FRICTION] ?: true,
            frictionSeconds = p[Keys.FRICTION_SECONDS] ?: 10,
            amoledDark = p[Keys.AMOLED] ?: true,
            accentColor = p[Keys.ACCENT] ?: 0,
            clockSize = p[Keys.CLOCK_SIZE] ?: 95,
            dateSize = p[Keys.DATE_SIZE] ?: 22,
            favoritesSize = p[Keys.FAVORITES_SIZE] ?: 36,
            homeAlign = p[Keys.HOME_ALIGN] ?: 0,
            verticalPos = p[Keys.VERTICAL_POS] ?: 2,
            clockOpensAlarms = p[Keys.CLOCK_OPENS_ALARMS] ?: true,
            hideStatusBar = p[Keys.HIDE_STATUS_BAR] ?: false,
            showNotificationBadges = p[Keys.NOTIF_BADGES] ?: false,
            widgetsDir = wDir,
            quickLaunchDir = qDir,
            drawerDir = dDir,
            appDrawerSize = p[Keys.APP_DRAWER_SIZE] ?: 23,
            appDrawerAlign = p[Keys.APP_DRAWER_ALIGN] ?: 0,
            alphabetIndex = p[Keys.ALPHABET_INDEX] ?: true,
            scrubberWidth = p[Keys.SCRUBBER_WIDTH] ?: 49,
            searchBarBottom = p[Keys.SEARCH_BAR_BOTTOM] ?: false,
            drawerTopSpace = p[Keys.DRAWER_TOP_SPACE] ?: 152,
            drawerShowTitle = p[Keys.DRAWER_SHOW_TITLE] ?: true,
            drawerTitle = p[Keys.DRAWER_TITLE] ?: "",
            drawerShowUsage = p[Keys.DRAWER_SHOW_USAGE] ?: false,
            quickLaunchPackage = p[Keys.QUICK_LAUNCH_PKG],
            onboarded = p[Keys.ONBOARDED] ?: false,
            pro = p[Keys.PRO] ?: false,
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

    /** Sets a daily time limit (minutes) for [pkg]; null or <= 0 removes it. */
    suspend fun setAppLimit(pkg: String, minutes: Int?) = context.dataStore.edit { p ->
        val map = (p[Keys.APP_LIMITS]?.let { decodeLimits(it) } ?: emptyMap()).toMutableMap()
        if (minutes == null || minutes <= 0) map.remove(pkg) else map[pkg] = minutes
        p[Keys.APP_LIMITS] = Json.encodeToString(map)
    }

    /** Adds or replaces a focus session (matched by id). */
    suspend fun upsertFocusSession(session: FocusSession) = context.dataStore.edit { p ->
        val list = (p[Keys.FOCUS_SESSIONS]?.let { decodeSessions(it) } ?: emptyList()).toMutableList()
        val i = list.indexOfFirst { it.id == session.id }
        if (i >= 0) list[i] = session else list.add(session)
        p[Keys.FOCUS_SESSIONS] = Json.encodeToString(list)
    }

    suspend fun removeFocusSession(id: String) = context.dataStore.edit { p ->
        val list = (p[Keys.FOCUS_SESSIONS]?.let { decodeSessions(it) } ?: emptyList()).filterNot { it.id == id }
        p[Keys.FOCUS_SESSIONS] = Json.encodeToString(list)
    }

    suspend fun setManualFocusUntil(v: Long) = context.dataStore.edit { it[Keys.MANUAL_FOCUS_UNTIL] = v }
    suspend fun setFocusSkipUntil(v: Long) = context.dataStore.edit { it[Keys.FOCUS_SKIP_UNTIL] = v }
    suspend fun setShowFocusOnHome(v: Boolean) = putBool(Keys.SHOW_FOCUS_HOME, v)
    suspend fun setDndInFocus(v: Boolean) = putBool(Keys.DND_IN_FOCUS, v)
    suspend fun setStrictFocus(v: Boolean) = putBool(Keys.STRICT_FOCUS, v)

    /** Overwrites all preferences from an imported [LauncherSettings] (backup restore). */
    suspend fun importSettings(s: LauncherSettings) = context.dataStore.edit { p ->
        p[Keys.FAVORITES] = s.favorites.joinToString("\n")
        p[Keys.HIDDEN] = s.hidden
        p[Keys.DISTRACTING] = s.distracting
        p[Keys.RENAMES] = Json.encodeToString(s.renames)
        p[Keys.APP_LIMITS] = Json.encodeToString(s.appLimits)
        p[Keys.FOCUS_SESSIONS] = Json.encodeToString(s.focusSessions)
        p[Keys.SHOW_FOCUS_HOME] = s.showFocusOnHome
        p[Keys.DND_IN_FOCUS] = s.dndInFocus
        p[Keys.STRICT_FOCUS] = s.strictFocus
        p[Keys.MANUAL_FOCUS_UNTIL] = 0L   // don't restore transient focus state
        p[Keys.FOCUS_SKIP_UNTIL] = 0L
        p[Keys.SHOW_CLOCK] = s.showClock
        p[Keys.SHOW_DATE] = s.showDate
        p[Keys.SHOW_BATTERY] = s.showBattery
        p[Keys.SHOW_ST_HOME] = s.showScreenTimeHome
        p[Keys.FRICTION] = s.frictionEnabled
        p[Keys.FRICTION_SECONDS] = s.frictionSeconds
        p[Keys.AMOLED] = s.amoledDark
        p[Keys.ACCENT] = s.accentColor
        p[Keys.CLOCK_SIZE] = s.clockSize
        p[Keys.DATE_SIZE] = s.dateSize
        p[Keys.FAVORITES_SIZE] = s.favoritesSize
        p[Keys.HOME_ALIGN] = s.homeAlign
        p[Keys.VERTICAL_POS] = s.verticalPos
        p[Keys.CLOCK_OPENS_ALARMS] = s.clockOpensAlarms
        p[Keys.HIDE_STATUS_BAR] = s.hideStatusBar
        p[Keys.NOTIF_BADGES] = s.showNotificationBadges
        p[Keys.WIDGETS_DIR] = s.widgetsDir
        p[Keys.QUICK_DIR] = s.quickLaunchDir
        p[Keys.DRAWER_DIR] = s.drawerDir
        p[Keys.APP_DRAWER_SIZE] = s.appDrawerSize
        p[Keys.APP_DRAWER_ALIGN] = s.appDrawerAlign
        p[Keys.ALPHABET_INDEX] = s.alphabetIndex
        p[Keys.SCRUBBER_WIDTH] = s.scrubberWidth
        p[Keys.SEARCH_BAR_BOTTOM] = s.searchBarBottom
        p[Keys.DRAWER_TOP_SPACE] = s.drawerTopSpace
        p[Keys.DRAWER_SHOW_TITLE] = s.drawerShowTitle
        p[Keys.DRAWER_TITLE] = s.drawerTitle
        p[Keys.DRAWER_SHOW_USAGE] = s.drawerShowUsage
        p[Keys.ONBOARDED] = s.onboarded
        if (s.quickLaunchPackage != null) p[Keys.QUICK_LAUNCH_PKG] = s.quickLaunchPackage
        else p.remove(Keys.QUICK_LAUNCH_PKG)
    }

    suspend fun setShowClock(v: Boolean) = putBool(Keys.SHOW_CLOCK, v)
    suspend fun setShowDate(v: Boolean) = putBool(Keys.SHOW_DATE, v)
    suspend fun setShowBattery(v: Boolean) = putBool(Keys.SHOW_BATTERY, v)
    suspend fun setShowScreenTimeHome(v: Boolean) = putBool(Keys.SHOW_ST_HOME, v)
    suspend fun setFrictionEnabled(v: Boolean) = putBool(Keys.FRICTION, v)
    suspend fun setAmoled(v: Boolean) = putBool(Keys.AMOLED, v)
    suspend fun setAccentColor(v: Int) = context.dataStore.edit { it[Keys.ACCENT] = v }
    suspend fun setFrictionSeconds(v: Int) = context.dataStore.edit { it[Keys.FRICTION_SECONDS] = v }
    suspend fun setClockSize(v: Int) = context.dataStore.edit { it[Keys.CLOCK_SIZE] = v }
    suspend fun setDateSize(v: Int) = context.dataStore.edit { it[Keys.DATE_SIZE] = v }
    suspend fun setFavoritesSize(v: Int) = context.dataStore.edit { it[Keys.FAVORITES_SIZE] = v }
    suspend fun setHomeAlign(v: Int) = context.dataStore.edit { it[Keys.HOME_ALIGN] = v }
    suspend fun setVerticalPos(v: Int) = context.dataStore.edit { it[Keys.VERTICAL_POS] = v }
    suspend fun setClockOpensAlarms(v: Boolean) = putBool(Keys.CLOCK_OPENS_ALARMS, v)
    suspend fun setHideStatusBar(v: Boolean) = putBool(Keys.HIDE_STATUS_BAR, v)
    suspend fun setShowNotificationBadges(v: Boolean) = putBool(Keys.NOTIF_BADGES, v)

    /**
     * Sets the direction of one gesture (0 = widgets, 1 = quick-launch, 2 = drawer) to [dir]
     * (0 = left, 1 = right, 2 = up). If another gesture already owned [dir], they swap so the
     * three stay a valid permutation.
     */
    suspend fun setGestureDir(item: Int, dir: Int) = context.dataStore.edit { p ->
        val (w, q, d) = readDirs(p)
        val arr = intArrayOf(w, q, d)
        val target = dir.coerceIn(0, 2)
        if (item !in 0..2 || arr[item] == target) return@edit
        val other = arr.indexOfFirst { it == target }
        val old = arr[item]
        arr[item] = target
        if (other >= 0) arr[other] = old
        p[Keys.WIDGETS_DIR] = arr[0]
        p[Keys.QUICK_DIR] = arr[1]
        p[Keys.DRAWER_DIR] = arr[2]
    }

    suspend fun setAppDrawerSize(v: Int) = context.dataStore.edit { it[Keys.APP_DRAWER_SIZE] = v }
    suspend fun setAppDrawerAlign(v: Int) = context.dataStore.edit { it[Keys.APP_DRAWER_ALIGN] = v }
    suspend fun setAlphabetIndex(v: Boolean) = putBool(Keys.ALPHABET_INDEX, v)
    suspend fun setScrubberWidth(v: Int) = context.dataStore.edit { it[Keys.SCRUBBER_WIDTH] = v }
    suspend fun setSearchBarBottom(v: Boolean) = putBool(Keys.SEARCH_BAR_BOTTOM, v)
    suspend fun setDrawerTopSpace(v: Int) = context.dataStore.edit { it[Keys.DRAWER_TOP_SPACE] = v }
    suspend fun setDrawerShowTitle(v: Boolean) = putBool(Keys.DRAWER_SHOW_TITLE, v)
    suspend fun setDrawerTitle(v: String) = context.dataStore.edit { it[Keys.DRAWER_TITLE] = v.trim() }
    suspend fun setDrawerShowUsage(v: Boolean) = putBool(Keys.DRAWER_SHOW_USAGE, v)
    suspend fun setOnboarded(v: Boolean) = putBool(Keys.ONBOARDED, v)
    suspend fun setPro(v: Boolean) = putBool(Keys.PRO, v)
    suspend fun setQuickLaunchPackage(pkg: String?) = context.dataStore.edit { p ->
        if (pkg == null) p.remove(Keys.QUICK_LAUNCH_PKG) else p[Keys.QUICK_LAUNCH_PKG] = pkg
    }

    private suspend fun putBool(key: Preferences.Key<Boolean>, v: Boolean) =
        context.dataStore.edit { it[key] = v }

    private fun decodeRenames(raw: String): Map<String, String> =
        runCatching { Json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())

    private fun decodeLimits(raw: String): Map<String, Int> =
        runCatching { Json.decodeFromString<Map<String, Int>>(raw) }.getOrDefault(emptyMap())

    private fun decodeSessions(raw: String): List<FocusSession> =
        runCatching { Json.decodeFromString<List<FocusSession>>(raw) }.getOrDefault(emptyList())
}
