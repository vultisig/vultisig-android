package com.vultisig.wallet.data.db.converters

import androidx.room.TypeConverter
import com.vultisig.wallet.data.models.TransactionHistoryData
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

class TransactionHistoryDataConverter {

    @TypeConverter
    fun fromJson(value: String): TransactionHistoryData =
        json.decodeFromString(TransactionHistoryData.serializer(), value)

    @TypeConverter
    fun toJson(data: TransactionHistoryData): String =
        json.encodeToString(TransactionHistoryData.serializer(), data)
}
