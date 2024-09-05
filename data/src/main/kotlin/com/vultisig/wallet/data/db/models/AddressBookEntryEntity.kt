package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "address_book_entry",
    primaryKeys = ["chainId", "address"],
)
data class AddressBookEntryEntity(
    @ColumnInfo("chainId")
    val chainId: String,
    @ColumnInfo("address")
    val address: String,
    @ColumnInfo("title")
    val title: String,
)