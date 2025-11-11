package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import java.util.Date

@Entity(
    tableName = "staking_details",
    indices = [
        Index(value = ["vault_id"], name = "index_staking_details_vault_id"),
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
data class StakingDetailsEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo("id")
    val id: Long = 0,

    @ColumnInfo(name = "vault_id")
    val vaultId: String, // foreign key to vault

    @ColumnInfo(name = "coin_id")
    val coinId: String,

    @ColumnInfo(name = "stake_amount")
    val stakeAmount: String,

    @ColumnInfo(name = "apr")
    val apr: Double?,

    @ColumnInfo(name = "estimated_rewards")
    val estimatedRewards: String?,

    @ColumnInfo(name = "next_payout_date")
    val nextPayoutDate: Date?,

    @ColumnInfo(name = "rewards")
    val rewards: String?,

    @ColumnInfo(name = "rewards_coin_id")
    val rewardsCoinId: String?,
)