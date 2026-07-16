package com.martin.foco.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.Calendar
import java.util.Locale

data class DayUsage(val label: String, val totalMs: Long)

data class UsageSnapshot(
    val hasPermission: Boolean = false,
    val totalTodayMs: Long = 0L,
    val unlocksToday: Int = 0,
    val perAppToday: Map<String, Long> = emptyMap(),  // packageName -> foreground ms
    val weekly: List<DayUsage> = emptyList(),
)

/** Reads UsageStatsManager. Requires the special PACKAGE_USAGE_STATS permission (granted manually). */
class UsageStatsRepository(private val context: Context) {

    private val usm: UsageStatsManager
        get() = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun snapshot(): UsageSnapshot {
        if (!hasPermission()) return UsageSnapshot(hasPermission = false)

        val now = System.currentTimeMillis()
        val startOfDay = startOfDayMillis(now)

        // Foreground time and unlocks are computed from the event stream clipped to today's window.
        // queryAndAggregateUsageStats returns whole daily buckets whose totalTimeInForeground is not
        // clipped to the requested range, so just after midnight it still reports the full previous
        // session (hours of usage, phantom unlocks). Pairing RESUMED/PAUSED events and clamping an
        // unmatched session to startOfDay keeps "today" honest right after the day rolls over.
        val (perApp, unlocks) = todayStats(startOfDay, now)
        val total = perApp.values.sum()

        return UsageSnapshot(
            hasPermission = true,
            totalTodayMs = total,
            unlocksToday = unlocks,
            perAppToday = perApp,
            weekly = weekly(now, todayTotalMs = total),
        )
    }

    /** Foreground ms per package and unlock count for [start, end], computed from UsageEvents. */
    @Suppress("DEPRECATION") // MOVE_TO_* are API 21+; ACTIVITY_* equivalents are only API 29+.
    private fun todayStats(start: Long, end: Long): Pair<Map<String, Long>, Int> {
        val totals = HashMap<String, Long>()
        val resumeAt = HashMap<String, Long>() // pkg -> timestamp it last entered the foreground
        var unlocks = 0
        val events = usm.queryEvents(start, end)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> resumeAt[pkg] = e.timeStamp
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    // No matching RESUMED means the app was already foregrounded before the window
                    // (e.g. across midnight); count only from the window start.
                    val from = resumeAt.remove(pkg) ?: start
                    if (e.timeStamp > from) totals.merge(pkg, e.timeStamp - from, Long::plus)
                }
                UsageEvents.Event.KEYGUARD_HIDDEN -> unlocks++
            }
        }
        // Whatever is still in the foreground at [end] counts up to now.
        for ((pkg, from) in resumeAt) {
            if (end > from) totals.merge(pkg, end - from, Long::plus)
        }
        return totals.filterValues { it > 0L } to unlocks
    }

    private fun weekly(now: Long, todayTotalMs: Long): List<DayUsage> {
        val locale = Locale.getDefault()
        val out = ArrayList<DayUsage>(7)
        for (offset in 6 downTo 0) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, -offset)
            }
            val dayStart = startOfDayMillis(cal.timeInMillis)
            val dayEnd = (dayStart + 24L * 60 * 60 * 1000).coerceAtMost(now)
            // Today reuses the precise event-based total; past full days use the cheaper aggregate.
            val total = if (offset == 0) todayTotalMs
            else usm.queryAndAggregateUsageStats(dayStart, dayEnd)
                .values.sumOf { it.totalTimeInForeground }
            // Localized short weekday name (e.g. "Mon" / "lun").
            val label = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, locale).orEmpty()
            out.add(DayUsage(label, total))
        }
        return out
    }

    private fun startOfDayMillis(time: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = time
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
