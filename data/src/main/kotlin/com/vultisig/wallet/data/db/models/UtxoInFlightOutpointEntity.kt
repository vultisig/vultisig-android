package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * One outpoint a transaction this wallet broadcast either consumed or paid back to the sending
 * address — the per-row form of [com.vultisig.wallet.data.blockchain.utxo.UtxoInFlightTx].
 *
 * Kept as plain rows rather than a serialized list per transaction so the table needs no converter
 * and can be queried by address directly. Rows are short-lived: the provider catches up with a
 * broadcast within minutes, after which they change nothing, and the repository prunes them on a
 * fixed age (see [com.vultisig.wallet.data.repositories.UtxoInFlightRepository]).
 */
@Entity(
    tableName = "utxo_inflight_outpoint",
    primaryKeys = ["chain", "tx_hash", "kind", "hash", "idx"],
)
data class UtxoInFlightOutpointEntity(
    /**
     * [com.vultisig.wallet.data.models.Chain.raw] of the chain the transaction was broadcast to.
     */
    @ColumnInfo(name = "chain") val chain: String,
    /** The address the transaction was sent from — the address whose UTXO set this row amends. */
    @ColumnInfo(name = "address") val address: String,
    @ColumnInfo(name = "tx_hash") val txHash: String,
    @ColumnInfo(name = "broadcast_at") val broadcastAt: Long,
    /** [KIND_SPENT] for an input the transaction consumed, [KIND_CREATED] for an output it made. */
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "hash") val hash: String,
    @ColumnInfo(name = "idx") val index: Long,
    @ColumnInfo(name = "amount") val amount: Long,
) {
    companion object {
        const val KIND_SPENT = "spent"
        const val KIND_CREATED = "created"
    }
}
