package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A THORChain limit order the vault has placed, recorded locally on successful broadcast (Phase 1
 * has no in-app open-orders list — that lands in TX History in Phase 2). Keyed by the inbound
 * deposit tx hash. All amounts/prices are stored as strings to avoid precision loss.
 */
@Entity(tableName = "pending_limit_order")
data class PendingLimitOrderEntity(
    @PrimaryKey @ColumnInfo(name = "inbound_tx_hash") val inboundTxHash: String,
    @ColumnInfo(name = "vault_id") val vaultId: String,
    @ColumnInfo(name = "source_asset") val sourceAsset: String,
    @ColumnInfo(name = "source_amount") val sourceAmount: String,
    @ColumnInfo(name = "target_asset") val targetAsset: String,
    @ColumnInfo(name = "dest_addr") val destAddr: String,
    /** Target price as buy-asset units per 1 sell-asset unit. */
    @ColumnInfo(name = "target_price") val targetPrice: String,
    @ColumnInfo(name = "expiry_blocks") val expiryBlocks: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "status") val status: String,
)
