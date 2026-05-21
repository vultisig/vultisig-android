package com.vultisig.wallet.ui.utils

import com.vultisig.wallet.R

private const val SECONDS_PER_HOUR = 3_600L
private const val HOURS_PER_DAY = 24L

internal fun cacaoUnlocksInUiText(remainingSeconds: Long): UiText {
    val totalHours = remainingSeconds / SECONDS_PER_HOUR
    val days = totalHours / HOURS_PER_DAY
    val hours = totalHours % HOURS_PER_DAY
    return when {
        days > 0L ->
            UiText.FormattedText(
                R.string.unstake_cacao_unlocks_in_days_hours_format,
                listOf(days.toInt(), hours.toInt()),
            )

        hours > 0L ->
            UiText.FormattedText(
                R.string.unstake_cacao_unlocks_in_hours_format,
                listOf(hours.toInt()),
            )

        else -> UiText.StringResource(R.string.unstake_cacao_unlocks_soon)
    }
}
