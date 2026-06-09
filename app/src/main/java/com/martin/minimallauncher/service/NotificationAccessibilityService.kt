package com.martin.minimallauncher.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * Minimal accessibility service whose only purpose is to expand the notification
 * shade via [performGlobalAction] — the official, reliable way to do it (reflection
 * over StatusBarManager is blocked on many devices).
 *
 * The user must enable it manually in Settings → Accessibility.
 */
class NotificationAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* unused */ }

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
