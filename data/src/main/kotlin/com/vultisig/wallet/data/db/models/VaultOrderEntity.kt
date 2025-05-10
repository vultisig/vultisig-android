package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vaultOrder")
data class VaultOrderEntity(
    @PrimaryKey
    @ColumnInfo(name = "value")
    override val value: String = "",

    @ColumnInfo(name = "order")
    override val order: Float,

    @ColumnInfo(name = "parentId")
    override val parentId: String? = null
) : BaseOrderEntity()