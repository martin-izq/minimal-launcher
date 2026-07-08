package com.martin.minimallauncher.data

import kotlinx.serialization.Serializable

/**
 * A scheduled focus window that soft-blocks distracting apps.
 *
 * @param start minute of day (0..1439) when the session starts
 * @param end   minute of day (0..1439) when it ends (must be after [start]; same-day only)
 * @param days  active weekdays as [java.time.DayOfWeek] values (1 = Monday .. 7 = Sunday)
 */
@Serializable
data class FocusSession(
    val id: String,
    val start: Int,
    val end: Int,
    val days: Set<Int>,
    val enabled: Boolean = true,
) {
    /** True if this session is active on weekday [day] (1..7) at [minute] of day. */
    fun isActiveAt(day: Int, minute: Int): Boolean =
        enabled && day in days && start < end && minute >= start && minute < end
}

/** Formats a minute-of-day as HH:mm. */
fun minuteOfDayLabel(minute: Int): String {
    val m = ((minute % 1440) + 1440) % 1440
    return "%02d:%02d".format(m / 60, m % 60)
}
