package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "addressBookOrder")
internal data class AddressBookOrderEntity(
    @PrimaryKey
    @ColumnInfo(name = "value")
    override val value: String = "",

    @ColumnInfo(name = "order")
    override val order: Float,
) : BaseOrderEntity() {

    @Ignore
    override val parentId: String? = null
}