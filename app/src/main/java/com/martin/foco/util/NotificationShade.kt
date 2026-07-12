package com.martin.foco.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.martin.foco.service.NotificationAccessibilityService

/**
 * Expands the notification shade. Tries the accessibility service first (official and
 * reliable) and, if it isn't active, falls back to reflection over StatusBarManager
 * (blocked on some devices, e.g. MIUI).
 *
 * Returns true if any path worked.
 */
fun openNotificationShade(context: Context): Boolean {
    if (NotificationAccessibilityService.openNotifications()) return true
    return expandViaReflection(context)
}

/** Opens the accessibility settings so the user can enable the service. */
fun openAccessibilitySettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@SuppressLint("WrongConstant")
private fun expandViaReflection(context: Context): Boolean {
    val service = context.getSystemService("statusbar") ?: return false
    val statusBarManager = runCatching { Class.forName("android.app.StatusBarManager") }
        .getOrNull() ?: return false
    return listOf("expandNotificationsPanel", "expand").any { method ->
        runCatching { statusBarManager.getMethod(method).invoke(service) }.isSuccess
    }
}
