package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "tokenValue",
    primaryKeys = ["chain", "address", "ticker"],
)
data class TokenValueEntity(
    @ColumnInfo("chain")
    val chain: String,
    @ColumnInfo("address")
    val address: String,
    @ColumnInfo("ticker")
    val ticker: String,
    @ColumnInfo("tokenValue")
    val tokenValue: String,
){
    val tokenId: String
        get() = "$ticker-$chain"
}
