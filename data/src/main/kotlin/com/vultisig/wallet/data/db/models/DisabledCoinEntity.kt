package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "disabledCoin")
data class DisabledCoinEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo("id")
    val id: Long = 0,

    @ColumnInfo("coinId")
    val coinId: String,

    @ColumnInfo("vaultId")
    val vaultId: String,

)