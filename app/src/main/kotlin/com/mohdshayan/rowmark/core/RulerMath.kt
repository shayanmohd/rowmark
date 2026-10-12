package com.mohdshayan.rowmark.core

/** Where the ruler band sits: a page index and the band's top as a fraction of the page height. */
data class RulerPos(val page: Int, val top: Float)

/**
 * The ruler is stored as page fractions so it survives zoom and rotation. One count moves it one band
 * height: up for charts (read bottom to top), down for written patterns. Leaving a page carries on to
 * the next page's far edge; the last page clamps.
 */
object RulerMath {
    const val MIN_HEIGHT = 0.01f
    const val MAX_HEIGHT = 0.5f

    /** A NaN (a drag measured on a zero-height page) falls back to the smallest band. */
    fun clampHeight(height: Float): Float = if (height.isNaN()) MIN_HEIGHT else height.coerceIn(MIN_HEIGHT, MAX_HEIGHT)

    fun clampTop(top: Float, height: Float): Float {
        val h = clampHeight(height)
        return if (top.isNaN()) 0f else top.coerceIn(0f, (1f - h).coerceAtLeast(0f))
    }

    fun step(pos: RulerPos, height: Float, readsUpward: Boolean, pageCount: Int, steps: Int = 1): RulerPos {
        var result = pos
        val forward = steps > 0
        repeat(kotlin.math.abs(steps)) {
            result = stepOnce(result, height, moveUp = if (forward) readsUpward else !readsUpward, forward = forward, pageCount = pageCount)
        }
        return result
    }

    /**
     * One band in [moveUp]'s direction. Leaving a page goes to the next page when counting forward and
     * back to the previous page when undoing, landing on that page's far edge, so a count and its undo
     * always meet at the same place. The first and last pages clamp.
     */
    private fun stepOnce(pos: RulerPos, height: Float, moveUp: Boolean, forward: Boolean, pageCount: Int): RulerPos {
        val h = clampHeight(height)
        val lastPage = (pageCount - 1).coerceAtLeast(0)
        val target = if (forward) pos.page + 1 else pos.page - 1
        val canTurn = target in 0..lastPage
        return if (moveUp) {
            val top = pos.top - h
            if (top >= -0.0005f) pos.copy(top = top.coerceAtLeast(0f))
            else if (canTurn) RulerPos(target, 1f - h)
            else pos.copy(top = 0f)
        } else {
            val top = pos.top + h
            if (top + h <= 1.0005f) pos.copy(top = top.coerceAtMost(1f - h))
            else if (canTurn) RulerPos(target, 0f)
            else pos.copy(top = 1f - h)
        }
    }
}
