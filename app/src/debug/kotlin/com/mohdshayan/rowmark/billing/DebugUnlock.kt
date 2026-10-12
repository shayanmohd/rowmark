package com.mohdshayan.rowmark.billing

import android.content.Context
import android.provider.Settings

/**
 * Debug builds only: `adb shell settings put global rowmark_debug_unlock 1` forces the unlocked tier
 * and 0 forces free, for screenshots. The release source set returns null for everything.
 */
object DebugUnlock {
    fun forcedFrom(raw: String?): Boolean? = when (raw) {
        "1" -> true
        "0" -> false
        else -> null
    }

    fun forced(context: Context): Boolean? =
        forcedFrom(runCatching { Settings.Global.getString(context.contentResolver, "rowmark_debug_unlock") }.getOrNull())
}
