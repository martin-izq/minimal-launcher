package com.martin.minimallauncher.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.martin.minimallauncher.service.NotificationAccessibilityService

/**
 * Despliega el panel de notificaciones. Intenta primero el servicio de
 * accesibilidad (oficial y confiable) y, si no está activo, cae al método por
 * reflexión sobre StatusBarManager (bloqueado en algunos equipos, p. ej. MIUI).
 *
 * Devuelve true si alguna vía funcionó.
 */
fun openNotificationShade(context: Context): Boolean {
    if (NotificationAccessibilityService.openNotifications()) return true
    return expandViaReflection(context)
}

/** Abre los ajustes de accesibilidad para que el usuario active el servicio. */
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
    for (name in listOf("expandNotificationsPanel", "expand")) {
        val ok = runCatching { statusBarManager.getMethod(name).invoke(service) }.isSuccess
        if (ok) return true
    }
    return false
}
