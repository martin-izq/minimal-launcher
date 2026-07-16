package com.martin.foco.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.martin.foco.BlockActivity
import com.martin.foco.data.BlockDecision
import com.martin.foco.data.LauncherSettings
import com.martin.foco.data.SettingsRepository
import com.martin.foco.data.UsageStatsRepository
import com.martin.foco.data.decideBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Minimal accessibility service with two jobs, both triggered by an explicit user action and
 * neither reading screen content ([android.R.attr.canRetrieveWindowContent] is false):
 *  1. Expand the notification shade / lock the screen via [performGlobalAction] (home gestures).
 *  2. Enforce the user's focus blocks system-wide: when a distracting or over-limit app comes to
 *     the foreground (from anywhere, not just Foco), show the block screen over it.
 *
 * The user must enable it manually in Settings → Accessibility.
 */
class NotificationAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settingsRepo by lazy { SettingsRepository(this) }
    private val usageRepo by lazy { UsageStatsRepository(this) }

    @Volatile private var settings: LauncherSettings? = null
    @Volatile private var usageToday: Map<String, Long> = emptyMap()
    @Volatile private var currentApp: String? = null
    // Whether a package is a real launchable app, cached — used to ignore IMEs, system UI and
    // transient dialogs whose window-state events would otherwise look like app switches.
    private val launchable = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // Keep a live copy of settings and a periodically-refreshed usage snapshot so the guard
        // can decide without touching disk on the (main-thread) event callback.
        scope.launch { settingsRepo.settings.collect { settings = it } }
        scope.launch {
            while (isActive) {
                usageToday = runCatching { usageRepo.snapshot().perAppToday }.getOrDefault(emptyMap())
                delay(60_000)
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        scope.cancel()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        guard(pkg)
    }

    /** If [pkg] is one the user chose to restrict and it's currently blocked, show the block screen. */
    private fun guard(pkg: String) {
        // Our own UI. The block screen is transient (keep the tracker so "open anyway" returns to
        // an app that still counts as current); the home screen means the user left the previous
        // app, so reset the tracker — reopening the same app then counts as a fresh entry.
        if (pkg == packageName) {
            if (FocusGuard.showingFor == null) currentApp = pkg
            return
        }
        // Ignore anything that isn't a launchable app (IME, system UI, transient dialogs). Their
        // window-state events would otherwise masquerade as app switches and re-trigger blocks
        // while the user is mid-use.
        if (!isRealApp(pkg)) return
        // Only act on a genuine app switch, not on in-app navigation within the same app.
        val fresh = pkg != currentApp
        currentApp = pkg
        if (!fresh) return
        if (FocusGuard.showingFor != null) return    // a block is already on screen
        // Skip once if Foco just launched this app (its own friction already ran) or the user just
        // chose "open anyway".
        if (FocusGuard.consumeGrace(pkg)) return

        val s = settings ?: return
        if (!s.enforceBlocks) return
        // Cheap pre-check: only the apps the user actually configured can ever block.
        if (pkg !in s.distracting && !s.appLimits.containsKey(pkg)) return

        val now = System.currentTimeMillis()
        val ldt = LocalDateTime.now()
        val decision = decideBlock(
            pkg = pkg,
            settings = s,
            usedTodayMs = usageToday[pkg] ?: 0L,
            nowMs = now,
            weekday = ldt.dayOfWeek.value,
            minuteOfDay = ldt.hour * 60 + ldt.minute,
        )
        if (decision is BlockDecision.Allow) return

        FocusGuard.showingFor = pkg
        runCatching {
            startActivity(
                BlockActivity.intent(this, pkg, label(pkg), decision, s.frictionSeconds, s.amoledDark, s.accentColor)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { FocusGuard.showingFor = null }
    }

    /** True if [pkg] is a launchable app (has a launcher entry); cached. Filters IMEs / system UI. */
    private fun isRealApp(pkg: String): Boolean = launchable.getOrPut(pkg) {
        runCatching { packageManager.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)
    }

    /** The app's visible name, honoring the user's rename. */
    private fun label(pkg: String): String {
        settings?.renames?.get(pkg)?.let { return it }
        return runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    override fun onInterrupt() { /* unused */ }

    companion object {
        @Volatile
        private var instance: NotificationAccessibilityService? = null

        /** True if the service is currently connected. */
        fun isActive(): Boolean = instance != null

        /** Expands the notification shade. Returns true if the service was active. */
        fun openNotifications(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) ?: false

        /** Locks the screen (API 28+). Returns true if it could run. */
        fun lockScreen(): Boolean {
            val service = instance ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            } else {
                false
            }
        }
    }
}
