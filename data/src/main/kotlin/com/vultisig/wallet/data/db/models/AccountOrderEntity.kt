package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "accountOrder", primaryKeys = ["vaultId", "chain"])
data class AccountOrderEntity(
    @ColumnInfo(name = "vaultId") val vaultId: String,
    @ColumnInfo(name = "chain") val chain: String,
    @ColumnInfo(name = "order") val order: Float,
    @ColumnInfo(name = "isPinned") val isPinned: Boolean = false,
)
