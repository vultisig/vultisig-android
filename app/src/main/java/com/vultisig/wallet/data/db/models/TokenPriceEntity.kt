package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "tokenPrice",
    primaryKeys = ["tokenId", "currency"],
)
internal data class TokenPriceEntity(
    @ColumnInfo("tokenId")
    val tokenId: String,
    @ColumnInfo("currency")
    val currency: String,

    @ColumnInfo("price")
    val price: String, // BigDecimal
)