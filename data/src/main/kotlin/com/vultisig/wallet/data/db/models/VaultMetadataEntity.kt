package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import com.vultisig.wallet.data.models.VaultId

@Entity(
    tableName = "vaultMetadata",
    primaryKeys = ["vaultId"],
    foreignKeys = [
        ForeignKey(
            entity = VaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaultId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class VaultMetadataEntity(
    @ColumnInfo("vaultId")
    val vaultId: VaultId,
    // false -- verification required,
    // null -- verification not required,
    // true -- verification completed
    val isServerBackupVerified: Boolean?,
)