package com.mohdshayan.rowmark.core

/** The five counter kinds. ROW moves Total rows; STITCH may reset when its row counts. */
enum class CounterKind { ROW, REPEAT, SECTION, STITCH, CUSTOM }

/** A counter as the engine sees it: no Room, no Android, so every rule is a JVM test. */
data class CounterSnapshot(
    val id: Long,
    val kind: CounterKind,
    val value: Int,
    val length: Int? = null,
    val parentId: Long? = null,
    val resetsWithRow: Boolean = false,
    val alertEvery: Int? = null,
    val alertLabel: String = "",
)

/** One counter moving. [delta] is valueAfter minus valueBefore, so a rollover from 18 to 1 is -17. */
data class Change(val counterId: Long, val delta: Int, val valueAfter: Int) {
    val valueBefore: Int get() = valueAfter - delta
}

/** An "every N rows" alert that came due on this count. */
data class Alert(val counterId: Long, val label: String, val value: Int)

/**
 * Everything one action did. [totalDelta] is how far Total rows moved; [rolledOver] says a counter
 * returned to 1 and carried into its parent, so the feedback and the ruler can show it.
 */
data class CountResult(
    val changes: List<Change>,
    val alerts: List<Alert> = emptyList(),
    val totalDelta: Int = 0,
    val rolledOver: Boolean = false,
) {
    val isEmpty: Boolean get() = changes.isEmpty()
}

object CounterRules {
    const val MIN_LENGTH = 2
    const val MAX_LENGTH = 999
    fun validLength(length: Int?): Boolean = length == null || length in MIN_LENGTH..MAX_LENGTH
    fun validAlert(every: Int?): Boolean = every == null || every in 1..MAX_LENGTH
}

object AlertRules {
    /** Due when the counter lands on a multiple of [every]; never at zero. */
    fun due(value: Int, every: Int?): Boolean = every != null && every > 0 && value > 0 && value % every == 0
}

/**
 * The counting rules. A count past a counter's length returns it to 1 and carries one into its parent,
 * which may carry into its own parent. Minus steps back across that boundary. Undo reverses one whole
 * action, whatever it carried.
 */
object CounterEngine {

    fun count(counters: List<CounterSnapshot>, counterId: Long): CountResult {
        val byId = counters.associateBy { it.id }
        val target = byId[counterId] ?: return CountResult(emptyList())
        val changes = mutableListOf<Change>()
        val alerts = mutableListOf<Alert>()
        var rolled = false
        var current: CounterSnapshot? = target
        val seen = mutableSetOf<Long>()
        while (current != null && seen.add(current.id)) {
            val length = current.length
            val next = if (length != null && length >= CounterRules.MIN_LENGTH && current.value + 1 > length) 1 else current.value + 1
            changes += Change(current.id, next - current.value, next)
            if (AlertRules.due(next, current.alertEvery)) alerts += Alert(current.id, current.alertLabel, next)
            val carried = next == 1 && current.value != 0
            current = if (carried) {
                rolled = true
                current.parentId?.let { byId[it] }
            } else {
                null
            }
        }
        if (target.kind == CounterKind.ROW) {
            for (c in counters) {
                if (c.kind == CounterKind.STITCH && c.resetsWithRow && c.value != 0 && c.id != target.id) {
                    changes += Change(c.id, -c.value, 0)
                }
            }
        }
        return CountResult(changes, alerts, totalDelta = if (target.kind == CounterKind.ROW) 1 else 0, rolledOver = rolled)
    }

    fun minus(counters: List<CounterSnapshot>, counterId: Long): CountResult {
        val byId = counters.associateBy { it.id }
        val target = byId[counterId] ?: return CountResult(emptyList())
        if (target.value <= 0) return CountResult(emptyList())
        val changes = mutableListOf<Change>()
        var current: CounterSnapshot? = target
        val seen = mutableSetOf<Long>()
        while (current != null && seen.add(current.id)) {
            val length = current.length
            val parent = current.parentId?.let { byId[it] }
            // Row 1 of Repeat 1 of Section 2 still has a row before it: Row 18, Repeat 8, Section 1.
            val crosses = length != null && length >= CounterRules.MIN_LENGTH && current.value == 1 && parent != null && canStepBack(parent, byId, seen.toMutableSet())
            if (crosses) {
                changes += Change(current.id, length!! - 1, length)
                current = parent
            } else {
                if (current.value > 0) changes += Change(current.id, -1, current.value - 1)
                current = null
            }
        }
        return CountResult(changes, totalDelta = if (target.kind == CounterKind.ROW) -1 else 0)
    }

    /** True when [c] can give a row back: it is above 1, or at 1 with a parent that can itself step back. */
    private fun canStepBack(c: CounterSnapshot, byId: Map<Long, CounterSnapshot>, seen: MutableSet<Long>): Boolean {
        if (c.value > 1) return true
        if (c.value < 1 || !seen.add(c.id)) return false
        val length = c.length ?: return false
        if (length < CounterRules.MIN_LENGTH) return false
        val parent = c.parentId?.let { byId[it] } ?: return false
        return canStepBack(parent, byId, seen)
    }

    /** The inverse of one action's changes, in reverse order. */
    fun undo(group: List<Change>): List<Change> =
        group.asReversed().map { Change(it.counterId, -it.delta, it.valueBefore) }

    fun apply(counters: List<CounterSnapshot>, changes: List<Change>): List<CounterSnapshot> {
        val values = counters.associate { it.id to it.value }.toMutableMap()
        for (c in changes) values[c.counterId] = c.valueAfter
        return counters.map { it.copy(value = values[it.id] ?: it.value) }
    }
}
