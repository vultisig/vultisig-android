package com.vultisig.wallet.data.utils

object VultiDate {
    fun getEpochMonth(): Int {
        val localDate = java.time.LocalDate.now()
        return localDate.minusYears(1970).year * 12 + localDate.monthValue
    }
}