package com.mohdshayan.rowmark.core

/** "Increase every 6 rows, 8 times, from row 12": due on rows 12, 18, ... 54. */
data class ScheduleSpec(
    val id: Long,
    val action: String,
    val label: String,
    val startRow: Int,
    val every: Int,
    val times: Int,
)

object ScheduleEngine {
    fun valid(spec: ScheduleSpec): Boolean = spec.startRow >= 1 && spec.every >= 1 && spec.times >= 1

    fun lastRow(spec: ScheduleSpec): Int = spec.startRow + spec.every * (spec.times - 1)

    /** 1-based step number when [row] is a due row, else null. */
    fun stepAt(spec: ScheduleSpec, row: Int): Int? {
        if (!valid(spec) || row < spec.startRow) return null
        val offset = row - spec.startRow
        if (offset % spec.every != 0) return null
        val step = offset / spec.every + 1
        return if (step <= spec.times) step else null
    }

    fun isDue(spec: ScheduleSpec, row: Int): Boolean = stepAt(spec, row) != null

    /** The first due row at or after [row], or null when the schedule is finished. */
    fun nextDueRow(spec: ScheduleSpec, row: Int): Int? {
        if (!valid(spec)) return null
        if (row <= spec.startRow) return spec.startRow
        val offset = row - spec.startRow
        val step = (offset + spec.every - 1) / spec.every
        val next = spec.startRow + step * spec.every
        return if (next <= lastRow(spec)) next else null
    }

    fun isFinished(spec: ScheduleSpec, row: Int): Boolean = valid(spec) && row > lastRow(spec)
}
