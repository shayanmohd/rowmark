package com.mohdshayan.rowmark.core

/** An open or closed working session: counts closer than the idle limit belong to the same one. */
data class SessionState(
    val startedAt: Long,
    val lastAt: Long,
    val activeMs: Long,
    val rows: Int,
    val endedAt: Long? = null,
)

/** The outcome of one count: a session that closed, if any, and the one that now holds the count. */
data class SessionTransition(val closed: SessionState?, val current: SessionState)

object SessionClock {
    const val IDLE_MS: Long = 10 * 60 * 1000L

    fun onCount(current: SessionState?, nowMs: Long, countsAsRow: Boolean, idleMs: Long = IDLE_MS): SessionTransition {
        val rowDelta = if (countsAsRow) 1 else 0
        if (current == null || current.endedAt != null || nowMs - current.lastAt > idleMs || nowMs < current.lastAt) {
            val closed = current?.let { closeAtLastCount(it) }
            return SessionTransition(closed, SessionState(nowMs, nowMs, 0L, rowDelta))
        }
        val gap = nowMs - current.lastAt
        return SessionTransition(null, current.copy(lastAt = nowMs, activeMs = current.activeMs + gap, rows = current.rows + rowDelta))
    }

    /** Closed sessions end at their last count, so idle minutes never count as work. */
    fun closeAtLastCount(session: SessionState): SessionState =
        if (session.endedAt != null) session else session.copy(endedAt = session.lastAt)

    fun isIdle(session: SessionState, nowMs: Long, idleMs: Long = IDLE_MS): Boolean =
        session.endedAt == null && nowMs - session.lastAt > idleMs
}
