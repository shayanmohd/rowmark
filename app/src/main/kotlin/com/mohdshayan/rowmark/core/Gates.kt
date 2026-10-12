package com.mohdshayan.rowmark.core

/** FULL when entitled; READ_ONLY keeps what a relocked user made; TEASER shows what the unlock adds. */
enum class Access { FULL, READ_ONLY, TEASER }

/** The pure gate rules. Nothing the user made is ever hidden or deleted by a relock. */
object Gates {
    fun patternAccess(entitled: Boolean, patternCount: Int): Access = access(entitled, patternCount)
    fun scheduleAccess(entitled: Boolean, scheduleCount: Int): Access = access(entitled, scheduleCount)
    fun stashAccess(entitled: Boolean, itemCount: Int): Access = access(entitled, itemCount)
    fun statsAccess(entitled: Boolean): Access = if (entitled) Access.FULL else Access.TEASER

    private fun access(entitled: Boolean, count: Int): Access = when {
        entitled -> Access.FULL
        count > 0 -> Access.READ_ONLY
        else -> Access.TEASER
    }
}
