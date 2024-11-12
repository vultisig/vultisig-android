package com.vultisig.wallet.data.utils

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.monthsUntil

object VultiDate {
    fun getEpochMonth(): Int {
        val epochDate = Instant.fromEpochMilliseconds(0)
        val localDate = Clock.System.now()
        return epochDate.monthsUntil(localDate, TimeZone.currentSystemDefault())
    }
}