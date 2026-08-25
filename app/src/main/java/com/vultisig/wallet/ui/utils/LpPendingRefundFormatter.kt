package com.vultisig.wallet.ui.utils

import com.vultisig.wallet.R

private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L

/**
 * How long a half-finished symmetric add has before THORChain refunds it, as `"1d 4h"` or `"21h
 * 14m"`.
 *
 * Days and hours are capped at two units the way the unstake hint is, but sub-hour values still
 * read as `"0h 30m"`: someone half an hour from losing their deposit needs the minutes, not a
 * rounded "soon".
 */
internal fun lpRefundsInUiText(remainingSeconds: Long): UiText {
    val totalMinutes = remainingSeconds / SECONDS_PER_MINUTE
    val totalHours = totalMinutes / MINUTES_PER_HOUR
    val days = totalHours / HOURS_PER_DAY
    val hours = totalHours % HOURS_PER_DAY
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (days > 0L) {
        UiText.FormattedText(
            R.string.lp_pending_duration_days_hours_format,
            listOf(days.toInt(), hours.toInt()),
        )
    } else {
        UiText.FormattedText(
            R.string.lp_pending_duration_hours_minutes_format,
            listOf(hours.toInt(), minutes.toInt()),
        )
    }
}
