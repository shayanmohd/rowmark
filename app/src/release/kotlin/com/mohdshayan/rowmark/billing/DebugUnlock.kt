package com.mohdshayan.rowmark.billing

import android.content.Context

/** Release builds carry no unlock override: every answer comes from Google Play. */
object DebugUnlock {
    @Suppress("UNUSED_PARAMETER")
    fun forcedFrom(raw: String?): Boolean? = null

    @Suppress("UNUSED_PARAMETER")
    fun forced(context: Context): Boolean? = null
}
