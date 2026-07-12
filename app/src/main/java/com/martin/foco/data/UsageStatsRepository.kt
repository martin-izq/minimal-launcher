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

        val perApp = usm.queryAndAggregateUsageStats(startOfDay, now)
            .mapValues { it.value.totalTimeInForeground }
            .filterValues { it > 0L }

        val total = perApp.values.sum()

        return UsageSnapshot(
            hasPermission = true,
            totalTodayMs = total,
            unlocksToday = unlockCount(startOfDay, now),
            perAppToday = perApp,
            weekly = weekly(now),
        )
    }

    private fun unlockCount(start: Long, end: Long): Int {
        var count = 0
        val events = usm.queryEvents(start, end)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) count++
        }
        return count
    }

    private fun weekly(now: Long): List<DayUsage> {
        val locale = Locale.getDefault()
        val out = ArrayList<DayUsage>(7)
        for (offset in 6 downTo 0) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, -offset)
            }
            val dayStart = startOfDayMillis(cal.timeInMillis)
            val dayEnd = (dayStart + 24L * 60 * 60 * 1000).coerceAtMost(now)
            val total = usm.queryAndAggregateUsageStats(dayStart, dayEnd)
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
