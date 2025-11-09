package com.vultisig.wallet.data.db.converters

import androidx.room.TypeConverter
import java.util.Date

/**
 * Type converters for ActiveBondedNode entity
 */
class ActiveBondedNodeConverters {
    
    @TypeConverter
    fun fromDate(value: Long?): Date? {
        return value?.let { Date(it) }
    }
    
    @TypeConverter
    fun toDate(date: Date?): Long? {
        return date?.time
    }
}