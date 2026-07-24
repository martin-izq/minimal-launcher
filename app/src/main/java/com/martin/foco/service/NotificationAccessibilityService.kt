package com.martin.foco.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.martin.foco.BlockActivity
import com.martin.foco.data.BlockDecision
import com.martin.foco.data.LauncherSettings
import com.martin.foco.data.SettingsRepository
import com.martin.foco.data.UsageStatsRepository
import com.martin.foco.data.decideBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Minimal accessibility service with two jobs, both triggered by an explicit user action and
 * neither reading screen content ([android.R.attr.canRetrieveWindowContent] is false):
 *  1. Expand the notification shade / lock the screen via [performGlobalAction] (home gestures).
 *  2. Enforce the user's focus blocks system-wide: when a distracting or over-limit app comes to
 *     the foreground (from anywhere, not just Foco), show the block screen over it — and keep
 *     watching a limited app *while it's in use*, so the limit also cuts a sitting that started
 *     under it (see [restartLimitWatch]).
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
    // Watches the foreground app's daily limit while it's being used; null when it has none.
    @Volatile private var limitWatch: Job? = null
    // Whether a package is a real launchable app, cached — used to ignore system UI and transient
    // dialogs whose window-state events would otherwise look like app switches.
    private val launchable = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    // Enabled keyboards. Having a launcher entry doesn't rule them out (Gboard has one), and a
    // keyboard popping up is never leaving the app — but it would displace [currentApp] and make
    // the app's next internal screen look like a fresh open. Refreshed with the usage snapshot.
    @Volatile private var imePackages: Set<String> = emptySet()
    // pkg -> epoch day on which the "limit almost up" heads-up was already shown (once per day).
    private val warnedOn = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private val power by lazy { getSystemService(Context.POWER_SERVICE) as PowerManager }
    private val ime by lazy { getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // Keep a live copy of settings and a periodically-refreshed usage snapshot so the guard
        // can decide without touching disk on the (main-thread) event callback.
        scope.launch { settingsRepo.settings.collect { settings = it } }
        scope.launch {
            while (isActive) {
                usageToday = runCatching { usageRepo.perAppToday() }.getOrDefault(emptyMap())
                imePackages = runCatching {
                    ime.enabledInputMethodList.mapTo(mutableSetOf()) { it.packageName }
                }.getOrDefault(emptySet())
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
        // Our own UI. The block screen is transient (keep the tracker so "open anyway" returns to an
        // app that still counts as current). Reaching the real home ends the previous app's session,
        // so the next open is a fresh sitting that re-friction-pauses.
        if (pkg == packageName) {
            if (FocusGuard.showingFor == null) {
                currentApp?.takeIf { it != packageName }?.let { FocusGuard.endSession(it) }
                currentApp = pkg
                stopLimitWatch()
            }
            return
        }
        // Ignore anything that isn't the user leaving for another app: system UI and transient
        // dialogs (no launcher entry), and keyboards (which do have one).
        if (pkg in imePackages || !isRealApp(pkg)) return
        // In-app navigation within the same app never re-triggers.
        if (pkg == currentApp) return

        // A genuine window switch. What it means for the app we're leaving depends on where we go:
        // switching to another watched app re-arms it (a new sitting), while a detour to a neutral
        // package (a link's custom tab, any app opened from within) only parks its session so a
        // return within the idle window stays the same sitting.
        val s = settings
        val idleMs = (s?.sessionIdleMinutes ?: FocusGuard.DEFAULT_SESSION_IDLE_MIN) * 60_000L
        val newIsWatched = s != null && (pkg in s.distracting || s.appLimits.containsKey(pkg))
        currentApp?.takeIf { it != packageName }?.let { prev ->
            if (newIsWatched) FocusGuard.endSession(prev) else FocusGuard.parkSession(prev, idleMs)
        }
        currentApp = pkg
        // Keep an eye on the new app's daily limit for as long as it stays in front. Self-cancels
        // if it has none, so this single call covers every path below (bridge, live session, allow).
        restartLimitWatch(pkg)

        if (FocusGuard.showingFor != null) return          // a block is already on screen
        if (FocusGuard.consumeBridge(pkg)) {               // Foco/"open anyway" just opened this app
            FocusGuard.openSession(pkg)                     // starts the sitting
            return
        }
        // Returned within a live session (came back from a neutral detour)? Same sitting: don't
        // re-block, and hold it open again since the app is back in front.
        if (FocusGuard.sessionAlive(pkg)) {
            FocusGuard.openSession(pkg)
            return
        }

        if (s == null || !s.enforceBlocks) return
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
                BlockActivity.intent(
                    this, pkg, label(pkg), decision, s.frictionSeconds, s.amoledDark, s.accentColor,
                    totalTodayMs = usageToday.values.sum(),
                )
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { FocusGuard.showingFor = null }
    }

    /**
     * Keep checking [pkg]'s daily limit for as long as it stays in front. Both choke points only
     * look at the limit when an app *opens*, so without this a sitting that starts under the limit
     * runs unbounded. Cancels any previous watch; a no-op for apps without a limit.
     *
     * Each tick re-reads the real usage instead of counting locally: [UsageStatsRepository] is the
     * source of truth and stops accruing on its own when the app is paused. The delay shrinks as
     * the limit approaches, so it polls rarely when there's plenty left and cuts within [MIN_POLL_MS].
     */
    private fun restartLimitWatch(pkg: String) {
        stopLimitWatch()
        val s = settings ?: return
        if (!s.enforceBlocks || !s.appLimits.containsKey(pkg)) return
        limitWatch = scope.launch {
            while (isActive && currentApp == pkg) {
                // Don't count (or block) against a dark screen: the app is paused, so its usage
                // isn't growing and a block would only pile up behind the keyguard.
                if (!power.isInteractive) {
                    delay(SCREEN_OFF_POLL_MS)
                    continue
                }
                // A block is already up (the entry check beat us to it): nothing to measure.
                if (FocusGuard.showingFor != null) {
                    delay(MIN_POLL_MS)
                    continue
                }
                val live = settings ?: return@launch
                if (!live.enforceBlocks) return@launch
                val limitMin = live.appLimits[pkg] ?: return@launch   // limit cleared while in use
                val used = runCatching { usageRepo.perAppToday() }.getOrNull()?.also { usageToday = it }
                    ?: return@launch
                val usedMs = used[pkg] ?: 0L
                val remaining = limitMin * 60_000L - usedMs
                if (remaining <= 0L) {
                    fireLimitBlock(pkg, usedMs, limitMin)
                    return@launch
                }
                val warnMs = live.limitWarnMinutes * 60_000L
                if (warnMs > 0L && remaining <= warnMs) warnOnce(pkg, remaining)
                // Sleep until the next thing that must happen — the heads-up first, then the cut.
                // Without aiming at the heads-up the tick can step straight over its window (a 1-min
                // warning against a 60 s cap lands anywhere from 60 s to a few seconds before the
                // block, which reads as "it never warned me").
                val nextEvent = if (warnMs > 0L && remaining > warnMs) remaining - warnMs else remaining
                delay(nextEvent.coerceIn(MIN_POLL_MS, MAX_POLL_MS))
            }
        }
    }

    private fun stopLimitWatch() {
        limitWatch?.cancel()
        limitWatch = null
    }

    /** The limit ran out with the app in front: end the sitting and drop the firm block over it. */
    private fun fireLimitBlock(pkg: String, usedMs: Long, limitMinutes: Int) {
        if (FocusGuard.showingFor != null) return
        val s = settings ?: return
        // Without this the sitting would still be alive on the way back in and the guard would wave
        // the user through instead of re-blocking.
        FocusGuard.endSession(pkg)
        FocusGuard.showingFor = pkg
        runCatching {
            startActivity(
                BlockActivity.intent(
                    this, pkg, label(pkg), BlockDecision.LimitReached(usedMs, limitMinutes),
                    s.frictionSeconds, s.amoledDark, s.accentColor,
                    totalTodayMs = usageToday.values.sum(),
                )
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { FocusGuard.showingFor = null }
    }

    /**
     * Heads-up before the limit runs out, so the cut isn't a surprise. Once per app per day.
     *
     * It's a screen and not a Toast because a Toast never arrives: the system drops toasts from a
     * background app whose notifications are off, which is any install that was never granted
     * POST_NOTIFICATIONS ("Suppressing toast from package … by user request" in the log).
     */
    private fun warnOnce(pkg: String, remainingMs: Long) {
        if (FocusGuard.showingFor != null) return
        val s = settings ?: return
        val today = LocalDate.now().toEpochDay()
        if (warnedOn.put(pkg, today) == today) return
        val minutes = ((remainingMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
        // Claimed like a block on purpose: it keeps our own window from resetting the foreground
        // tracker, which would stop the watch and lose the cut a minute later.
        FocusGuard.showingFor = pkg
        runCatching {
            startActivity(
                BlockActivity.warningIntent(this, pkg, label(pkg), minutes, s.amoledDark, s.accentColor)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { FocusGuard.showingFor = null }
    }

    /** True if [pkg] is a launchable app (has a launcher entry); cached. Filters system UI. */
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
        /** Bounds for the live limit check: it tightens to [MIN_POLL_MS] as the limit approaches. */
        private const val MIN_POLL_MS = 10_000L
        private const val MAX_POLL_MS = 60_000L
        private const val SCREEN_OFF_POLL_MS = 30_000L

        @Volatile
        private var instance: NotificationAccessibilityService? = null

        /** True if the service is currently connected. */
        fun isActive(): Boolean = instance != null

        /**
         * Re-arm the app-switch tracker after a block screen closes, so returning to the just-blocked
         * app (e.g. via the recents switcher) counts as a fresh entry and is re-evaluated instead of
         * slipping through as "same app". The legitimate "open anyway" path is covered by the grace.
         */
        fun rearmTracker() { instance?.currentApp = null }

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
