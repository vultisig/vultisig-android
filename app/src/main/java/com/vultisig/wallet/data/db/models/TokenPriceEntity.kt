package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "tokenPrice",
    primaryKeys = ["priceProviderId", "currency"],
)
internal data class TokenPriceEntity(
    @ColumnInfo("priceProviderId")
    val priceProviderId: String,
    @ColumnInfo("currency")
    val currency: String,

    @ColumnInfo("price")
    val price: String, // BigDecimal
)