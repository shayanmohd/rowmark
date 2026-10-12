package com.mohdshayan.rowmark.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SessionRecord(val startedAt: Long, val endedAt: Long?, val activeMs: Long, val rows: Int)

data class DayRows(val date: LocalDate, val rows: Int)

object StatsCalc {
    fun totalRows(sessions: List<SessionRecord>): Int = sessions.sumOf { it.rows }

    fun totalActiveMs(sessions: List<SessionRecord>): Long = sessions.sumOf { it.activeMs }

    /** Rows per active hour, or null until at least one active minute is on record. */
    fun rowsPerHour(sessions: List<SessionRecord>): Double? {
        val ms = totalActiveMs(sessions)
        if (ms < 60_000L) return null
        return totalRows(sessions) / (ms / 3_600_000.0)
    }

    /** One bucket per calendar day for the last [days] days ending today, oldest first. */
    fun rowsPerDay(sessions: List<SessionRecord>, days: Int, nowMs: Long, zone: ZoneId): List<DayRows> {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val buckets = LinkedHashMap<LocalDate, Int>()
        for (i in days - 1 downTo 0) buckets[today.minusDays(i.toLong())] = 0
        for (s in sessions) {
            val day = Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDate()
            if (day in buckets) buckets[day] = buckets.getValue(day) + s.rows
        }
        return buckets.map { DayRows(it.key, it.value) }
    }

    /** Average rows per day over the days that had any rows, or null with no rows. */
    fun averageRowsPerWorkingDay(perDay: List<DayRows>): Double? {
        val working = perDay.filter { it.rows > 0 }
        if (working.isEmpty()) return null
        return working.sumOf { it.rows }.toDouble() / working.size
    }

    /** Whole days until the target at the given pace; null when no target, already there, or no pace. */
    fun finishEstimateDays(targetRows: Int?, totalRows: Int, rowsPerWorkingDay: Double?): Int? {
        if (targetRows == null || rowsPerWorkingDay == null || rowsPerWorkingDay <= 0.0) return null
        val remaining = targetRows - totalRows
        if (remaining <= 0) return 0
        return kotlin.math.ceil(remaining / rowsPerWorkingDay).toInt()
    }
}
