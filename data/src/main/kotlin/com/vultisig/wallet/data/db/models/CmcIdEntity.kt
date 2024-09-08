package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cmcId")
data class CmcIdEntity(
    @PrimaryKey
    @ColumnInfo(name = "contractAddress")
    val contractAddress: String,

    @ColumnInfo(name = "id")
    val id: Int? = null,
)