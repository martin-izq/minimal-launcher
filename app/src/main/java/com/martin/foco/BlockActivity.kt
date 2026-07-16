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
import com.martin.foco.ui.FocusBlockDialog
import com.martin.foco.ui.FrictionDialog
import com.martin.foco.ui.LimitReachedDialog
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
        val limit = intent.getIntExtra(EXTRA_LIMIT, 0)
        val seconds = intent.getIntExtra(EXTRA_SECONDS, 10)
        val amoled = intent.getBooleanExtra(EXTRA_AMOLED, true)
        val accent = intent.getIntExtra(EXTRA_ACCENT, 0)

        setContent {
            MinimalLauncherTheme(amoledDark = amoled, accent = accent) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when (type) {
                        TYPE_FOCUS -> FocusBlockDialog(appLabel = label, onDismiss = { goHome() })
                        TYPE_LIMIT -> LimitReachedDialog(
                            appLabel = label,
                            usedTodayMs = used,
                            limitMinutes = limit,
                            seconds = seconds,
                            onProceed = { openAnyway(pkg) },
                            onDismiss = { goHome() },
                        )
                        else -> FrictionDialog(
                            appLabel = label,
                            usedTodayMs = used,
                            seconds = seconds,
                            onProceed = { openAnyway(pkg) },
                            onDismiss = { goHome() },
                        )
                    }
                }
            }
        }
    }

    /** Let the user into the app they chose, without an immediate re-block. */
    private fun openAnyway(pkg: String) {
        FocusGuard.grant(pkg, GRACE_MS)
        finish()
    }

    /** Bounce out of the blocked app. */
    private fun goHome() {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        FocusGuard.showingFor = null
    }

    companion object {
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_USED = "used"
        private const val EXTRA_LIMIT = "limit"
        private const val EXTRA_SECONDS = "seconds"
        private const val EXTRA_AMOLED = "amoled"
        private const val EXTRA_ACCENT = "accent"

        private const val TYPE_FOCUS = 0
        private const val TYPE_LIMIT = 1
        private const val TYPE_FRICTION = 2

        // Short grace so the finish()→app-foreground transition doesn't re-trigger a block.
        private const val GRACE_MS = 3_000L

        fun intent(
            context: Context,
            pkg: String,
            label: String,
            decision: BlockDecision,
            seconds: Int,
            amoled: Boolean,
            accent: Int,
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
                .putExtra(EXTRA_LIMIT, limit)
                .putExtra(EXTRA_SECONDS, seconds)
                .putExtra(EXTRA_AMOLED, amoled)
                .putExtra(EXTRA_ACCENT, accent)
        }
    }
}
