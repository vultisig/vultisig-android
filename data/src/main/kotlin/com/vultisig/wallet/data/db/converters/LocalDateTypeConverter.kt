package com.vultisig.wallet.data.db.converters

import androidx.room.TypeConverter
import java.time.LocalDate

class LocalDateTypeConverter {

    @TypeConverter fun toLocalDate(value: Long): LocalDate = LocalDate.ofEpochDay(value)

    @TypeConverter fun fromLocalDate(value: LocalDate): Long = value.toEpochDay()
}
