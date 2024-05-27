package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(
    tableName = "chainOrder"
)
internal data class ChainOrderEntity(
    @PrimaryKey
    @ColumnInfo(name = "value")
    val value: String = "",

    @ColumnInfo(name = "trueFraction")
    val trueFraction: String,

    @ColumnInfo(name = "fractionInFloat")
    val fractionInFloat: Float
)