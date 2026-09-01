package com.vultisig.wallet.data.utils

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

object VultiDate {
    fun getEpochMonth(): Int {
        val epochDate = LocalDate.ofEpochDay(0)
        val today = LocalDate.now(ZoneId.systemDefault())
        return ChronoUnit.MONTHS.between(epochDate, today).toInt()
    }
}

operator fun Instant.plus(duration: kotlin.time.Duration): Instant =
    this.plus(duration.toJavaDuration())

operator fun Instant.minus(other: Instant): kotlin.time.Duration =
    java.time.Duration.between(other, this).toKotlinDuration()
