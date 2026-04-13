package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "accountOrder",
    primaryKeys = ["vaultId", "chain"],
    foreignKeys = [
        ForeignKey(
            entity = VaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaultId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        )
    ],
)
data class AccountOrderEntity(
    @ColumnInfo(name = "vaultId") val vaultId: String,
    @ColumnInfo(name = "chain") val chain: String,
    @ColumnInfo(name = "order") val order: Float,
    @ColumnInfo(name = "isPinned") val isPinned: Boolean = false,
)
