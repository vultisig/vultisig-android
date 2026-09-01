package com.vultisig.wallet.data.utils

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

object VultiDate {
    fun getEpochMonth(): Int {
        val zone = ZoneId.systemDefault()
        val epochDate = Instant.ofEpochMilli(0).atZone(zone).toLocalDate()
        val today = Instant.now().atZone(zone).toLocalDate()
        return ChronoUnit.MONTHS.between(epochDate, today).toInt()
    }
}

operator fun Instant.plus(duration: kotlin.time.Duration): Instant =
    this.plus(duration.toJavaDuration())

operator fun Instant.minus(other: Instant): kotlin.time.Duration =
    java.time.Duration.between(other, this).toKotlinDuration()
