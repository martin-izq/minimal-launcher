package com.martin.minimallauncher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.martin.minimallauncher.data.AppInfo
import com.martin.minimallauncher.data.AppRepository
import com.martin.minimallauncher.data.LauncherSettings
import com.martin.minimallauncher.data.SettingsRepository
import com.martin.minimallauncher.data.UsageSnapshot
import com.martin.minimallauncher.data.UsageStatsRepository
import com.martin.minimallauncher.data.WidgetPlacement
import com.martin.minimallauncher.data.WidgetsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LauncherUiState(
    val settings: LauncherSettings = LauncherSettings(),
    val allApps: List<AppInfo> = emptyList(),
    val visibleApps: List<AppInfo> = emptyList(),
    val favoriteApps: List<AppInfo> = emptyList(),
    val usage: UsageSnapshot = UsageSnapshot(),
    val widgets: List<WidgetPlacement> = emptyList(),
)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val appRepo = AppRepository(app)
    private val settingsRepo = SettingsRepository(app)
    private val usageRepo = UsageStatsRepository(app)
    private val widgetsRepo = WidgetsRepository(app)

    private val allAppsFlow = MutableStateFlow<List<AppInfo>>(emptyList())
    private val usageFlow = MutableStateFlow(UsageSnapshot())

    /** Evento para volver al inicio (al presionar HOME). */
    private val _goHome = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val goHome = _goHome.asSharedFlow()

    val uiState: StateFlow<LauncherUiState> =
        combine(
            allAppsFlow,
            settingsRepo.settings,
            usageFlow,
            widgetsRepo.widgets,
        ) { apps, settings, usage, widgets ->
            val visible = apps
                .filter { it.packageName !in settings.hidden }
                .sortedBy { it.displayLabel(settings.renames).lowercase() }
            val favs = settings.favorites.mapNotNull { pkg ->
                apps.firstOrNull { it.packageName == pkg }
            }
            LauncherUiState(settings, apps, visible, favs, usage, widgets)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherUiState())

    init {
        refresh()
    }

    /** Recarga lista de apps y estadísticas (llamar en onResume). */
    fun refresh() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { appRepo.loadApps() }
            allAppsFlow.value = apps
            val snap = withContext(Dispatchers.IO) { usageRepo.snapshot() }
            usageFlow.value = snap
        }
    }

    fun emitGoHome() {
        _goHome.tryEmit(Unit)
    }

    fun hasUsagePermission(): Boolean = usageRepo.hasPermission()

    // --- Acciones ---
    fun launch(app: AppInfo) = appRepo.launch(app.packageName)
    fun openAlarms() = appRepo.openAlarms()
    fun openAppInfo(app: AppInfo) = appRepo.openAppInfo(app.packageName)
    fun uninstall(app: AppInfo) = appRepo.requestUninstall(app.packageName)

    fun toggleFavorite(app: AppInfo) { viewModelScope.launch { settingsRepo.toggleFavorite(app.packageName) } }
    fun moveFavorite(app: AppInfo, up: Boolean) { viewModelScope.launch { settingsRepo.moveFavorite(app.packageName, up) } }
    fun setHidden(app: AppInfo, hidden: Boolean) { viewModelScope.launch { settingsRepo.setHidden(app.packageName, hidden) } }
    fun setDistracting(app: AppInfo, v: Boolean) { viewModelScope.launch { settingsRepo.setDistracting(app.packageName, v) } }
    fun rename(app: AppInfo, newName: String) { viewModelScope.launch { settingsRepo.rename(app.packageName, newName) } }

    fun setShowClock(v: Boolean) { viewModelScope.launch { settingsRepo.setShowClock(v) } }
    fun setShowDate(v: Boolean) { viewModelScope.launch { settingsRepo.setShowDate(v) } }
    fun setShowBattery(v: Boolean) { viewModelScope.launch { settingsRepo.setShowBattery(v) } }
    fun setShowScreenTimeHome(v: Boolean) { viewModelScope.launch { settingsRepo.setShowScreenTimeHome(v) } }
    fun setFrictionEnabled(v: Boolean) { viewModelScope.launch { settingsRepo.setFrictionEnabled(v) } }
    fun setFrictionSeconds(v: Int) { viewModelScope.launch { settingsRepo.setFrictionSeconds(v) } }
    fun setAmoled(v: Boolean) { viewModelScope.launch { settingsRepo.setAmoled(v) } }
    fun setClockSize(v: Int) { viewModelScope.launch { settingsRepo.setClockSize(v) } }
    fun setDateSize(v: Int) { viewModelScope.launch { settingsRepo.setDateSize(v) } }
    fun setFavoritesSize(v: Int) { viewModelScope.launch { settingsRepo.setFavoritesSize(v) } }
    fun setHomeAlign(v: Int) { viewModelScope.launch { settingsRepo.setHomeAlign(v) } }
    fun setVerticalPos(v: Int) { viewModelScope.launch { settingsRepo.setVerticalPos(v) } }
    fun setClockOpensAlarms(v: Boolean) { viewModelScope.launch { settingsRepo.setClockOpensAlarms(v) } }
    fun setWidgetsOnLeft(v: Boolean) { viewModelScope.launch { settingsRepo.setWidgetsOnLeft(v) } }
    fun setAppDrawerSize(v: Int) { viewModelScope.launch { settingsRepo.setAppDrawerSize(v) } }
    fun setAppDrawerAlign(v: Int) { viewModelScope.launch { settingsRepo.setAppDrawerAlign(v) } }
    fun setAlphabetIndex(v: Boolean) { viewModelScope.launch { settingsRepo.setAlphabetIndex(v) } }

    // --- Widgets ---
    fun addWidget(placement: WidgetPlacement) { viewModelScope.launch { widgetsRepo.add(placement) } }
    fun removeWidget(appWidgetId: Int) { viewModelScope.launch { widgetsRepo.remove(appWidgetId) } }
    fun setWidgetHeight(appWidgetId: Int, heightDp: Int) { viewModelScope.launch { widgetsRepo.setHeight(appWidgetId, heightDp) } }
    fun moveWidget(appWidgetId: Int, up: Boolean) { viewModelScope.launch { widgetsRepo.move(appWidgetId, up) } }
}
