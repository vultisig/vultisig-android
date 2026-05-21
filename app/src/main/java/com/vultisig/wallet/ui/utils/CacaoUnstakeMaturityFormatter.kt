package com.vultisig.wallet.ui.utils

import com.vultisig.wallet.R

private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L

internal fun cacaoUnlocksInUiText(remainingSeconds: Long): UiText {
    val totalMinutes = remainingSeconds / SECONDS_PER_MINUTE
    val totalHours = totalMinutes / MINUTES_PER_HOUR
    val days = totalHours / HOURS_PER_DAY
    val hours = totalHours % HOURS_PER_DAY
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (days > 0L) {
        // Match iOS DateComponentsFormatter([.day, .hour, .minute], maximumUnitCount = 2): once we
        // have days, drop the minutes component so the hint stays at two units.
        UiText.FormattedText(
            R.string.unstake_cacao_unlocks_in_days_hours_format,
            listOf(days.toInt(), hours.toInt()),
        )
    } else {
        // Always emit hours + minutes (sub-hour reads as e.g. "0h 30m") so a user 30 minutes from
        // unlock sees real time remaining instead of the older "Unlocks soon" collapse.
        UiText.FormattedText(
            R.string.unstake_cacao_unlocks_in_hours_format,
            listOf(hours.toInt(), minutes.toInt()),
        )
    }
}
