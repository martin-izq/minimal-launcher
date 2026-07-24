package com.martin.foco

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.martin.foco.data.BlockDecision
import com.martin.foco.service.FocusGuard
import com.martin.foco.service.NotificationAccessibilityService
import com.martin.foco.ui.FocusBlockDialog
import com.martin.foco.ui.FrictionDialog
import com.martin.foco.ui.LimitReachedDialog
import com.martin.foco.ui.LimitWarningDialog
import com.martin.foco.ui.theme.MinimalLauncherTheme

/**
 * Full-screen block shown over a restricted app when it's opened from anywhere (not just Foco).
 * Reuses the same dialogs as the launcher choke point. Dismissing sends the user home; "open
 * anyway" (friction/limit only) grants a short grace and reveals the app underneath.
 *
 * Launched by [com.martin.foco.service.NotificationAccessibilityService] in its own task
 * (taskAffinity="") so finishing returns to whatever was in front.
 */
class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PKG)
        if (pkg == null) {
            finish()
            return
        }
        val label = intent.getStringExtra(EXTRA_LABEL) ?: pkg
        val type = intent.getIntExtra(EXTRA_TYPE, TYPE_FRICTION)
        val used = intent.getLongExtra(EXTRA_USED, 0L)
        val total = intent.getLongExtra(EXTRA_TOTAL, 0L)
        val limit = intent.getIntExtra(EXTRA_LIMIT, 0)
        val seconds = intent.getIntExtra(EXTRA_SECONDS, 10)
        val amoled = intent.getBooleanExtra(EXTRA_AMOLED, true)
        val accent = intent.getIntExtra(EXTRA_ACCENT, 0)

        setContent {
            MinimalLauncherTheme(amoledDark = amoled, accent = accent) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when (type) {
                        TYPE_FOCUS -> FocusBlockDialog(appLabel = label, onDismiss = { goHome() })
                        TYPE_WARNING -> LimitWarningDialog(
                            appLabel = label,
                            remainingMinutes = intent.getIntExtra(EXTRA_REMAINING, 1),
                            // Going back in is a legitimate continue: bridge it so the guard doesn't
                            // read the return as a fresh open and friction-pause it.
                            onContinue = { openAnyway(pkg) },
                            onLeave = { goHome() },
                        )
                        TYPE_LIMIT -> LimitReachedDialog(
                            appLabel = label,
                            usedTodayMs = used,
                            limitMinutes = limit,
                            onDismiss = { goHome() },
                        )
                        else -> FrictionDialog(
                            appLabel = label,
                            usedTodayMs = used,
                            totalTodayMs = total,
                            seconds = seconds,
                            onProceed = { openAnyway(pkg) },
                            onDismiss = { goHome() },
                        )
                    }
                }
            }
        }
    }

    // Set by the deliberate exits so onDestroy doesn't release the guard a second time. The release
    // must happen *before* finish() on those paths: the system reveals the app underneath — and
    // delivers its window event to the guard — before this activity's onDestroy runs, so re-arming
    // only in onDestroy is too late. The reveal would hit `pkg == currentApp` and be swallowed as
    // "same app", leaving the live limit watch dead and the friction sitting never reopened.
    private var released = false

    /** Let the user into the app they chose — a one-shot bridge so the guard doesn't re-block the open. */
    private fun openAnyway(pkg: String) {
        FocusGuard.grantBridge(pkg)
        releaseGuard()
        finish()
    }

    /** Bounce out of the blocked app. */
    private fun goHome() {
        releaseGuard()
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finish()
    }

    /**
     * Drop the "a block is on screen" latch and re-arm the guard's switch tracker, so the app's
     * reveal after this finishes counts as a fresh entry — consuming the bridge, reopening the
     * sitting and restarting the live limit watch. Done before finish() so it wins the race with the
     * reveal event; idempotent, and marks the exit so onDestroy won't re-arm on top of it.
     */
    private fun releaseGuard() {
        released = true
        FocusGuard.showingFor = null
        NotificationAccessibilityService.rearmTracker()
    }

    override fun onStop() {
        super.onStop()
        // The user left the block by navigating away (recents / app switch / home) rather than
        // proceeding or dismissing. Don't let it linger behind the app: close it so it can't be
        // bypassed by switching back to the blocked app. onDestroy re-arms the guard.
        if (!isFinishing) finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Only the abandoned path (system/back closed it without openAnyway/goHome) still needs
        // this; the deliberate exits already released the guard before finishing, and re-arming
        // again here would null out the tracker the reveal event just re-established.
        if (!released) releaseGuard()
    }

    companion object {
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_USED = "used"
        private const val EXTRA_TOTAL = "total"
        private const val EXTRA_LIMIT = "limit"
        private const val EXTRA_REMAINING = "remaining"
        private const val EXTRA_SECONDS = "seconds"
        private const val EXTRA_AMOLED = "amoled"
        private const val EXTRA_ACCENT = "accent"

        private const val TYPE_FOCUS = 0
        private const val TYPE_LIMIT = 1
        private const val TYPE_FRICTION = 2
        private const val TYPE_WARNING = 3

        /**
         * The heads-up shown [remainingMinutes] before a daily limit runs out. Not a [BlockDecision]:
         * nothing is being blocked yet, the app stays open behind it.
         */
        fun warningIntent(
            context: Context,
            pkg: String,
            label: String,
            remainingMinutes: Int,
            amoled: Boolean,
            accent: Int,
        ): Intent = Intent(context, BlockActivity::class.java)
            .putExtra(EXTRA_PKG, pkg)
            .putExtra(EXTRA_LABEL, label)
            .putExtra(EXTRA_TYPE, TYPE_WARNING)
            .putExtra(EXTRA_REMAINING, remainingMinutes)
            .putExtra(EXTRA_AMOLED, amoled)
            .putExtra(EXTRA_ACCENT, accent)

        fun intent(
            context: Context,
            pkg: String,
            label: String,
            decision: BlockDecision,
            seconds: Int,
            amoled: Boolean,
            accent: Int,
            totalTodayMs: Long = 0L,
        ): Intent {
            val (type, used, limit) = when (decision) {
                BlockDecision.FocusBlock -> Triple(TYPE_FOCUS, 0L, 0)
                is BlockDecision.LimitReached -> Triple(TYPE_LIMIT, decision.usedTodayMs, decision.limitMinutes)
                is BlockDecision.Friction -> Triple(TYPE_FRICTION, decision.usedTodayMs, 0)
                BlockDecision.Allow -> Triple(TYPE_FRICTION, 0L, 0) // never reached
            }
            return Intent(context, BlockActivity::class.java)
                .putExtra(EXTRA_PKG, pkg)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_TYPE, type)
                .putExtra(EXTRA_USED, used)
                .putExtra(EXTRA_TOTAL, totalTodayMs)
                .putExtra(EXTRA_LIMIT, limit)
                .putExtra(EXTRA_SECONDS, seconds)
                .putExtra(EXTRA_AMOLED, amoled)
                .putExtra(EXTRA_ACCENT, accent)
        }
    }
}
