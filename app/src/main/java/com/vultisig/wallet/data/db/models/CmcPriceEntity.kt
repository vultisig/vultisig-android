package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cmcPrice")
internal data class CmcPriceEntity(
    @PrimaryKey
    @ColumnInfo(name = "contractAddress")
    val contractAddress: String,

    @ColumnInfo(name = "cmcId")
    val cmcId: Int? = null,
)