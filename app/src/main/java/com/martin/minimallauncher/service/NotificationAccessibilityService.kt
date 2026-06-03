package com.martin.minimallauncher.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * Servicio de accesibilidad mínimo cuyo único fin es desplegar el panel de
 * notificaciones con [performGlobalAction], la forma oficial y confiable de
 * hacerlo (la reflexión sobre StatusBarManager está bloqueada en muchos equipos).
 *
 * El usuario debe activarlo manualmente en Ajustes → Accesibilidad.
 */
class NotificationAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* no se usa */ }

    override fun onInterrupt() { /* no se usa */ }

    companion object {
        @Volatile
        private var instance: NotificationAccessibilityService? = null

        /** True si el servicio está activo. */
        fun isActive(): Boolean = instance != null

        /** Despliega las notificaciones. Devuelve true si el servicio estaba activo. */
        fun openNotifications(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        }

        /** Bloquea la pantalla (API 28+). Devuelve true si pudo ejecutarse. */
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
