package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(
    tableName = "customToken"
)
internal data class CustomTokenEntity(
    @PrimaryKey
    @ColumnInfo("id")
    val id: String,
    @ColumnInfo("chain")
    val chain: String,
    @ColumnInfo("ticker")
    val ticker: String,
    @ColumnInfo("decimals")
    val decimals: Int,
    @ColumnInfo("logo")
    val logo: String,
    @ColumnInfo("contractAddress")
    val contractAddress: String,
)