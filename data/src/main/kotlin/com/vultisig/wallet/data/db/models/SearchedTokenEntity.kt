package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "searchedToken",
)
data class SearchedTokenEntity(
    @PrimaryKey
    @ColumnInfo("id")
    val id: String,
    @ColumnInfo("vaultId")
    val vaultId: String,
    @ColumnInfo("chain")
    val chain: String,
    @ColumnInfo("ticker")
    val ticker: String,
    @ColumnInfo("decimals")
    val decimals: Int,
    @ColumnInfo("logo")
    val logo: String,
    @ColumnInfo("priceProviderId")
    val priceProviderID: String,
    @ColumnInfo("contractAddress")
    val contractAddress: String,
)