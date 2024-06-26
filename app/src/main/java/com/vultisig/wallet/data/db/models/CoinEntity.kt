package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey


@Entity(
    tableName = "coin",
    primaryKeys = ["id", "vaultId"],
    foreignKeys = [
        ForeignKey(
            entity = VaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaultId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
internal data class CoinEntity(
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

    @ColumnInfo("priceProviderId")
    val priceProviderID: String,
    @ColumnInfo("contractAddress")
    val contractAddress: String,

    @ColumnInfo("address")
    val address: String,
    @ColumnInfo("hexPublicKey")
    val hexPublicKey: String,
)