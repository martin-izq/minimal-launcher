package com.martin.foco.ui

import androidx.annotation.ArrayRes
import com.martin.foco.R

/**
 * The friction prompt escalates with how long the app has already been used today: gentle when
 * you've barely touched it, blunt once the time has piled up.
 */
enum class FrictionTone(@ArrayRes val linesRes: Int) {
    CALM(R.array.friction_lines_calm),
    MID(R.array.friction_lines_mid),
    DIRECT(R.array.friction_lines_direct),
}

/**
 * Without the usage permission this always gets 0 and stays [FrictionTone.CALM] — the right
 * default, since there's nothing to call the user out on.
 */
fun frictionToneFor(usedTodayMs: Long): FrictionTone = when {
    usedTodayMs >= 60 * 60_000L -> FrictionTone.DIRECT
    usedTodayMs >= 20 * 60_000L -> FrictionTone.MID
    else -> FrictionTone.CALM
}

/**
 * Picks which prompt to show. A single fixed line stops being read after a few days, so the pause
 * rotates — but with a shuffle bag rather than plain random: every line of a tone is used once
 * before any repeats, so the same sentence never lands twice in a row (which plain random does
 * often enough to be noticed, and is exactly what makes it feel stale).
 *
 * State is per-process and shared by both callers (`LauncherRoot` and `BlockActivity` live in the
 * same process). Losing it when the process dies just means a reshuffle — not worth a DataStore
 * write in the moment the user is opening an app.
 */
object FrictionMessages {
    private val bags = mutableMapOf<FrictionTone, MutableList<Int>>()
    private val last = mutableMapOf<FrictionTone, Int>()

    @Synchronized
    fun next(tone: FrictionTone, size: Int): Int {
        if (size <= 0) return 0
        val bag = bags.getOrPut(tone) { mutableListOf() }
        bag.removeAll { it >= size }
        if (bag.isEmpty()) {
            bag.addAll((0 until size).shuffled())
            // Don't let a fresh bag open with the line just shown.
            if (size > 1 && bag.first() == last[tone]) bag.add(bag.removeAt(0))
        }
        return bag.removeAt(0).also { last[tone] = it }
    }
}
