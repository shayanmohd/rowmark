package com.mohdshayan.rowmark.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mohdshayan.rowmark.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** "just now", "12 min ago", "3 h ago", "yesterday", "5 days ago", or a date past a fortnight. */
@Composable
fun relativeTime(atMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val diff = nowMs - atMs
    val minutes = diff / 60_000L
    val hours = diff / 3_600_000L
    val days = diff / 86_400_000L
    return when {
        minutes < 1 -> stringResource(R.string.time_just_now)
        minutes < 60 -> stringResource(R.string.time_minutes_ago, minutes.toInt())
        hours < 24 -> stringResource(R.string.time_hours_ago, hours.toInt())
        days < 2 -> stringResource(R.string.time_yesterday)
        days < 14 -> stringResource(R.string.time_days_ago, days.toInt())
        else -> shortDate(atMs)
    }
}

fun shortDate(atMs: Long): String =
    DateTimeFormatter.ofPattern("d MMM").format(Instant.ofEpochMilli(atMs).atZone(ZoneId.systemDefault()))

fun shortDateTime(atMs: Long): String =
    DateTimeFormatter.ofPattern("d MMM, HH:mm").format(Instant.ofEpochMilli(atMs).atZone(ZoneId.systemDefault()))

@Composable
fun duration(ms: Long): String {
    val minutes = (ms / 60_000L).toInt()
    return if (minutes < 60) stringResource(R.string.stats_minutes, minutes)
    else stringResource(R.string.stats_hours, minutes / 60, minutes % 60)
}
