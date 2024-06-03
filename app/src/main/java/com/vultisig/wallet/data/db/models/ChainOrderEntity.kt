package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "chainOrder", primaryKeys = ["value", "parentId"])
internal data class ChainOrderEntity(
    @ColumnInfo(name = "value")
    override val value: String = "",

    @ColumnInfo(name = "order")
    override val order: Float,

    @ColumnInfo(name = "parentId")
    override val parentId: String
) : BaseOrderEntity()