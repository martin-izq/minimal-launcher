package com.martin.foco.service

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory coordination between the accessibility guard, the launcher and the block screen
 * ([BlockActivity]).
 *
 * - [showingFor] prevents the guard from relaunching a block while one is already on screen.
 * - [bridge] is a one-shot "just opened" pass, consumed when the app reaches the foreground: set
 *   when Foco launches an app or the user chose "open anyway", so that open itself isn't blocked.
 * - Sessions model friction as *once per sitting* rather than once per window event. Passing the
 *   gate for an app opens a session ([openSession]); while it's alive that app never re-blocks. A
 *   detour to a *neutral* package (a link's custom tab, any app opened from within it) only *parks*
 *   the session ([parkSession]) — it keeps living for the idle window, so following a link and
 *   coming back is still the same sitting. Reaching Foco's home or switching to another *watched*
 *   app ends it ([endSession]), so the next open re-friction-pauses.
 *
 * The idle window measures *time away*, never time in use: a session held open by the foreground
 * app never expires. Expiring it while the app was still in front made any window event that
 * momentarily displaced the tracked package (a keyboard, a custom tab) turn the app's *next*
 * internal screen into a fresh open — friction while opening a profile, writing a post, closing a
 * story, all without ever leaving the app.
 */
object FocusGuard {

    /** Default idle window (minutes) a parked session survives; user-configurable via settings. */
    const val DEFAULT_SESSION_IDLE_MIN = 5

    /** How long a Foco-launched / "open anyway" open stays exempt while the app comes to the fore. */
    const val BRIDGE_MS = 12_000L

    @Volatile
    var showingFor: String? = null

    /** Deadline standing for "in the foreground": the sitting can't age out while the app is up. */
    private const val IN_USE = Long.MAX_VALUE

    private val bridge = ConcurrentHashMap<String, Long>()
    // pkg -> epoch ms at which a parked session expires ([IN_USE] while it's the foreground app).
    // Present = the app has a live session.
    private val sessions = ConcurrentHashMap<String, Long>()

    /** Exempt [pkg]'s next foreground entry from a block (Foco launched it / user chose to continue). */
    fun grantBridge(pkg: String) {
        bridge[pkg] = System.currentTimeMillis() + BRIDGE_MS
    }

    /** One-shot: true (and clears it) if [pkg] still has an unexpired open bridge. */
    fun consumeBridge(pkg: String): Boolean {
        val until = bridge.remove(pkg) ?: return false
        return System.currentTimeMillis() < until
    }

    /**
     * Start (or resume) [pkg]'s session: while alive the app won't re-block. Called on a passed open
     * and on a return from a detour, i.e. whenever the app takes the foreground — so it holds with
     * no deadline until [parkSession] or [endSession] says otherwise.
     */
    fun openSession(pkg: String) {
        sessions[pkg] = IN_USE
    }

    /**
     * The app left the foreground for a *neutral* package (a detour). If it had a session, start the
     * idle countdown from now; a return within [idleMs] keeps the sitting. No-op if it had none.
     */
    fun parkSession(pkg: String, idleMs: Long) {
        sessions.computeIfPresent(pkg) { _, _ -> System.currentTimeMillis() + idleMs }
    }

    /** True if [pkg] still has a live session (returned within the idle window). Prunes if expired. */
    fun sessionAlive(pkg: String): Boolean {
        val until = sessions[pkg] ?: return false
        if (System.currentTimeMillis() < until) return true
        sessions.remove(pkg)
        return false
    }

    /** End [pkg]'s session (reached home / switched to another watched app): next open re-frictions. */
    fun endSession(pkg: String) {
        sessions.remove(pkg)
    }
}
