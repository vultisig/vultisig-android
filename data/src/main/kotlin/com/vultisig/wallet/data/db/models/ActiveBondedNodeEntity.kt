package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.util.Date

@Entity(
    tableName = "active_bonded_nodes",
    indices = [
        Index(value = ["vault_id"], name = "index_active_bonded_nodes_vault_id"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = VaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["vault_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        )
    ]
)
@TypeConverters(ActiveBondedNodeConverters::class)
data class ActiveBondedNodeEntity(
    @PrimaryKey
    @ColumnInfo("id")
    val id: String,

    @Embedded(prefix = "node_")
    val node: BondedNodeEntity,

    @ColumnInfo(name = "coin_id")
    val coinId: String,

    @ColumnInfo(name = "vault_id")
    val vaultId: String, // foreign key to vault

    @ColumnInfo(name = "amount")
    val amount: String,

    @ColumnInfo(name = "apy")
    val apy: Double,

    @ColumnInfo(name = "next_reward")
    val nextReward: Double,

    @ColumnInfo(name = "next_churn")
    val nextChurn: Date?,
)

data class BondedNodeEntity(
    @ColumnInfo(name = "address")
    val address: String,

    @ColumnInfo(name = "state")
    val state: String,
)

class ActiveBondedNodeConverters {
    @TypeConverter
    fun fromDate(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun toDate(date: Date?): Long? {
        return date?.time
    }
}