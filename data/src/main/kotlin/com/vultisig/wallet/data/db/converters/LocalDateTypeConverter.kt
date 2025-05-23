package com.vultisig.wallet.data.db.converters

import androidx.room.TypeConverter
import kotlinx.datetime.LocalDate

class LocalDateTypeConverter {

    @TypeConverter
    fun toLocalDate(value: Int): LocalDate = LocalDate.fromEpochDays(value)

    @TypeConverter
    fun fromLocalDate(value: LocalDate): Int = value.toEpochDays()

}