package com.martin.foco.service

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory coordination between the accessibility guard and the block screen ([BlockActivity]).
 *
 * - [showingFor] prevents the guard from relaunching a block while one is already on screen.
 * - the grace map lets "open anyway" briefly suppress re-blocking so the user can actually use the
 *   app they chose to open (otherwise the guard would bounce them straight back).
 */
object FocusGuard {

    @Volatile
    var showingFor: String? = null

    private val graceUntil = ConcurrentHashMap<String, Long>()

    /** Suppress blocking of [pkg] for [durationMs] (user chose "open anyway"). */
    fun grant(pkg: String, durationMs: Long) {
        graceUntil[pkg] = System.currentTimeMillis() + durationMs
    }

    fun isInGrace(pkg: String): Boolean {
        val until = graceUntil[pkg] ?: return false
        if (System.currentTimeMillis() >= until) {
            graceUntil.remove(pkg)
            return false
        }
        return true
    }
}
