package com.martin.foco.data

/**
 * What should happen when an app is opened, per the focus rules. Single source of truth shared by
 * the launcher choke point ([com.martin.foco.ui.LauncherRoot]) and the system-wide accessibility
 * guard ([com.martin.foco.service.NotificationAccessibilityService]).
 */
sealed interface BlockDecision {
    data object Allow : BlockDecision
    data object FocusBlock : BlockDecision
    data class LimitReached(val usedTodayMs: Long, val limitMinutes: Int) : BlockDecision
    data class Friction(val usedTodayMs: Long) : BlockDecision
}

/** True if a focus (a scheduled session or a manual one) is active at the given instant. */
fun LauncherSettings.focusActiveAt(nowMs: Long, weekday: Int, minuteOfDay: Int): Boolean {
    val scheduled = focusSessions.any { it.isActiveAt(weekday, minuteOfDay) } && nowMs >= focusSkipUntil
    val manual = nowMs < manualFocusUntil
    return scheduled || manual
}

/**
 * Decides what happens when [pkg] is opened. Priority: active focus session (firm) → daily limit
 * (firm) → friction (soft). The limit branch needs a real [usedTodayMs] (usage permission).
 */
fun decideBlock(
    pkg: String,
    settings: LauncherSettings,
    usedTodayMs: Long,
    nowMs: Long,
    weekday: Int,
    minuteOfDay: Int,
): BlockDecision {
    val distracting = pkg in settings.distracting
    val limitMin = settings.appLimits[pkg]
    return when {
        distracting && settings.focusActiveAt(nowMs, weekday, minuteOfDay) -> BlockDecision.FocusBlock
        limitMin != null && usedTodayMs >= limitMin * 60_000L -> BlockDecision.LimitReached(usedTodayMs, limitMin)
        settings.frictionEnabled && distracting -> BlockDecision.Friction(usedTodayMs)
        else -> BlockDecision.Allow
    }
}
