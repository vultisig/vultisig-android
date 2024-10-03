package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "vaultFolderRecord",
    primaryKeys = ["vaultId", "folderId"]
)
data class VaultFolderRef(
    @ColumnInfo(name = "vaultId")
    val vaultId: String,

    @ColumnInfo(name = "folderId")
    val folderId: Long,
)