package com.vultisig.wallet.data.utils

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object VultiDate {
    fun getEpochMonth(): Int {
        val epochDate = LocalDate.ofEpochDay(0)
        val today = LocalDate.now(ZoneId.systemDefault())
        return ChronoUnit.MONTHS.between(epochDate, today).toInt()
    }
}
