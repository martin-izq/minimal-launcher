package com.martin.foco.util

import android.app.NotificationManager
import android.content.Context

private fun nm(context: Context) =
    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

/**
 * Whether we can toggle Do Not Disturb. Needs "Do Not Disturb access", which the user grants in
 * system settings (Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) — no root, no adb.
 */
fun canControlDnd(context: Context): Boolean = nm(context).isNotificationPolicyAccessGranted

/** Turns Do Not Disturb (priority-only) on or off. No-op without policy access. */
fun setDnd(context: Context, enabled: Boolean) {
    val manager = nm(context)
    if (!manager.isNotificationPolicyAccessGranted) return
    runCatching {
        manager.setInterruptionFilter(
            if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
    }
}
