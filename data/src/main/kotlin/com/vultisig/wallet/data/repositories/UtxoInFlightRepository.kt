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
 * Entries expire on age alone, [TTL_MS] after broadcast. The ledger only ever matters while the
 * provider's snapshot predates the broadcast (see `SpendableUtxos.reconcile`), and Blockchair's
 * cache plus mempool ingest lag is measured in minutes, so an hour is generous cover. It is also
 * the bound on the one way the ledger can be wrong: a transaction evicted from the mempool frees
 * its inputs, and until its entry expires a snapshot listing those inputs again would be
 * "corrected" by re-spending outputs that no longer exist. That costs one rejected broadcast and no
 * money — the same outcome iOS accepts for a child built on an evicted parent — and is far rarer
 * than the provider lag the ledger exists for.
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

    /** Unexpired entries for [address] on [chain], oldest broadcast first. */
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
        dao.deleteBroadcastBefore(now - TTL_MS)

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
        dao.getSince(chain.raw, address, since = System.currentTimeMillis() - TTL_MS)
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

    private companion object {
        const val TTL_MS = 60L * 60 * 1000
    }
}
