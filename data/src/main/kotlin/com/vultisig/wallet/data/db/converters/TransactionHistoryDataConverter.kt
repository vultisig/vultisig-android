package com.vultisig.wallet.data.db.converters

import androidx.room.TypeConverter
import com.vultisig.wallet.data.models.TransactionHistoryData
import com.vultisig.wallet.data.models.UnknownTransactionHistoryData
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

class TransactionHistoryDataConverter {

    @TypeConverter
    fun fromJson(value: String): TransactionHistoryData =
        try {
            json.decodeFromString(TransactionHistoryData.serializer(), value)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            UnknownTransactionHistoryData(rawPayload = value)
        }

    @TypeConverter
    fun toJson(data: TransactionHistoryData): String =
        when (data) {
            is UnknownTransactionHistoryData -> data.rawPayload
            else -> json.encodeToString(TransactionHistoryData.serializer(), data)
        }
}
