package com.martin.foco.util

fun formatDuration(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        totalMin > 0 -> "${m}m"
        ms > 0 -> "<1m"
        else -> "0m"
    }
}

fun formatDurationShort(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h${if (m > 0) " ${m}m" else ""}" else "${m}m"
}
