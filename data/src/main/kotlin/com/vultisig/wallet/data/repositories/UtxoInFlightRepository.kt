package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.blockchain.utxo.UtxoInFlightTx
import com.vultisig.wallet.data.db.dao.UtxoInFlightOutpointDao
import com.vultisig.wallet.data.db.models.UtxoInFlightOutpointEntity
import com.vultisig.wallet.data.db.models.UtxoInFlightOutpointEntity.Companion.KIND_CREATED
import com.vultisig.wallet.data.db.models.UtxoInFlightOutpointEntity.Companion.KIND_SPENT
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.UtxoInfo
import javax.inject.Inject

/**
 * The wallet's own record of what its recent UTXO-chain broadcasts did to the sending address — the
 * [UtxoInFlightTx] ledger that [com.vultisig.wallet.data.blockchain.utxo.SpendableUtxos] replays
 * over the provider's snapshot.
 *
 * Entries are replayed for [UtxoInFlightRepositoryImpl.REPLAY_WINDOW_MS] after broadcast and then
 * dropped. The ledger only matters while the provider's snapshot predates the broadcast (see
 * [com.vultisig.wallet.data.blockchain.utxo.SpendableUtxos]), and Blockchair's cache plus mempool
 * ingest lag is measured in minutes, so ten of them is generous cover. The window is also the bound
 * on the one way the ledger can be wrong — a transaction the mempool evicted frees its inputs, and
 * until its entry expires a snapshot listing them again would be "corrected" by re-spending outputs
 * that no longer exist. Nothing local can distinguish that from a stale snapshot (a UTXO history
 * row never becomes `FAILED` on eviction), so the window is kept as short as the lag it exists to
 * cover: an eviction inside it costs one rejected broadcast and no money, the outcome iOS accepts
 * for a child built on an evicted parent.
 */
interface UtxoInFlightRepository {

    /**
     * Records that [txHash], broadcast from [address] on [chain], consumed [spent] and paid
     * [created] back to [address]. Persistence failures propagate; the caller records best-effort.
     */
    suspend fun record(
        chain: Chain,
        address: String,
        txHash: String,
        spent: List<UtxoInfo>,
        created: List<UtxoInfo>,
    )

    /** Entries for [address] on [chain] still inside the replay window, oldest broadcast first. */
    suspend fun getInFlight(chain: Chain, address: String): List<UtxoInFlightTx>
}

internal class UtxoInFlightRepositoryImpl
@Inject
constructor(private val dao: UtxoInFlightOutpointDao) : UtxoInFlightRepository {

    override suspend fun record(
        chain: Chain,
        address: String,
        txHash: String,
        spent: List<UtxoInfo>,
        created: List<UtxoInfo>,
    ) {
        val now = System.currentTimeMillis()
        dao.deleteBroadcastBefore(now - REPLAY_WINDOW_MS)

        fun rows(kind: String, utxos: List<UtxoInfo>) =
            utxos.map {
                UtxoInFlightOutpointEntity(
                    chain = chain.raw,
                    address = address,
                    txHash = txHash,
                    broadcastAt = now,
                    kind = kind,
                    hash = it.hash,
                    index = it.index.toLong(),
                    amount = it.amount,
                )
            }
        dao.insertAll(rows(KIND_SPENT, spent) + rows(KIND_CREATED, created))
    }

    override suspend fun getInFlight(chain: Chain, address: String): List<UtxoInFlightTx> =
        dao.getSince(chain.raw, address, since = System.currentTimeMillis() - REPLAY_WINDOW_MS)
            .groupBy { it.txHash }
            .map { (txHash, rows) ->
                UtxoInFlightTx(
                    txHash = txHash,
                    broadcastAt = rows.first().broadcastAt,
                    spent = rows.filter { it.kind == KIND_SPENT }.map { it.toUtxoInfo() },
                    created = rows.filter { it.kind == KIND_CREATED }.map { it.toUtxoInfo() },
                )
            }
            .sortedBy(UtxoInFlightTx::broadcastAt)

    private fun UtxoInFlightOutpointEntity.toUtxoInfo() =
        UtxoInfo(hash = hash, amount = amount, index = index.toUInt())

    companion object {
        const val REPLAY_WINDOW_MS = 10L * 60 * 1000
    }
}
