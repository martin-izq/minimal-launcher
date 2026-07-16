package com.martin.foco.service

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory coordination between the accessibility guard, the launcher and the block screen
 * ([BlockActivity]).
 *
 * - [showingFor] prevents the guard from relaunching a block while one is already on screen.
 * - the grace map lets a fresh entry into [pkg] be skipped once: it's set when Foco itself launches
 *   an app (so its own friction/limit isn't shown a second time by the guard) and when the user
 *   chose "open anyway", so they can actually use the app instead of being bounced right back.
 */
object FocusGuard {

    @Volatile
    var showingFor: String? = null

    private val grace = ConcurrentHashMap<String, Long>()

    /** Skip the next block for [pkg] within [durationMs] (Foco launched it / user chose to continue). */
    fun grant(pkg: String, durationMs: Long = 10_000L) {
        grace[pkg] = System.currentTimeMillis() + durationMs
    }

    /** One-shot: returns true (and clears the grace) if [pkg] currently has unexpired grace. */
    fun consumeGrace(pkg: String): Boolean {
        val until = grace.remove(pkg) ?: return false
        return System.currentTimeMillis() < until
    }
}
