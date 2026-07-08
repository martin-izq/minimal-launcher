package com.martin.minimallauncher.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional notification listener that publishes a per-package count of active (clearable)
 * notifications, used to show unread badges. Requires the user to grant notification access.
 * Reads only counts — never notification content.
 */
class NotificationService : NotificationListenerService() {

    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        _counts.value = runCatching {
            activeNotifications
                .filter { it.isClearable }
                .groupingBy { it.packageName }
                .eachCount()
        }.getOrDefault(emptyMap())
    }

    companion object {
        private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
        /** package name → number of active clearable notifications. */
        val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()
    }
}
