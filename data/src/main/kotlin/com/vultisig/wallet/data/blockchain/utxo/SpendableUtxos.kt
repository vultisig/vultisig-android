package com.vultisig.wallet.data.blockchain.utxo

import com.vultisig.wallet.data.api.models.BlockChairUtxoInfo
import com.vultisig.wallet.data.models.payload.UtxoInfo
import java.math.BigInteger
import timber.log.Timber

/**
 * The single definition of which of an address's unspent outputs this wallet treats as its own
 * spendable money on the Blockchair-backed UTXO chains.
 *
 * Both numbers the user meets come from here: the balance on the wallet screen
 * ([com.vultisig.wallet.data.repositories.BalanceRepository]) and the candidate set a transaction
 * is funded from ([com.vultisig.wallet.data.repositories.BlockChainSpecificRepository]). They used
 * to be derived independently — the balance from Blockchair's `address.balance`, the inputs from a
 * separately filtered `utxo` array — and everything in the gap between them was money the wallet
 * displayed and then refused to spend: a send cleared the balance check and failed at input
 * selection, and send-max drained less than the screen showed. One predicate with two callers is
 * what keeps that gap shut (#5867; iOS closed the same gap in vultisig-ios#4978).
 */
object SpendableUtxos {

    /**
     * Narrows Blockchair's rows to the outputs that may fund a transaction, smallest first.
     * - **Not explicitly unspendable.** `is_spendable` is absent from Blockchair's Bitcoin
     *   responses, so absent means spendable and only an explicit `false` disqualifies an output.
     * - **Confirmed, or unconfirmed and ours.** Blockchair reports mempool outputs with `block_id
     *   == -1` and counts them in `address.balance`. Zero-conf someone else controls can be
     *   replaced or evicted at will, and the MPC pairing window leaves minutes for that between
     *   selection and broadcast. Zero-conf produced by a transaction this wallet broadcast is
     *   different: only this wallet can spend that parent's inputs, so nobody else can replace it.
     *   Withholding it would blank the wallet for a block after every send, because the input
     *   leaves the UTXO set the moment the parent reaches the mempool while the change arrives
     *   unconfirmed.
     * - **At or above the chain's dust threshold** (`>=`, matching iOS so both platforms spend the
     *   same outputs).
     *
     * [ownUnconfirmedTxHashes] can only ever rescue a row, never add one: every candidate was
     * returned by the provider as part of the address's current unspent set, so a parent that was
     * dropped or never propagated contributes nothing to spend in the first place. An empty set
     * collapses the predicate back to confirmed-only.
     *
     * Rows that cannot name an outpoint (negative index, blank hash) are dropped and counted in the
     * log rather than failing the whole set: unlike the policy exclusions above there is no
     * legitimate reason for one to exist, and silently dropping it would understate the balance
     * with nothing anywhere failing.
     */
    fun select(
        rows: List<BlockChairUtxoInfo>,
        dustThreshold: Long,
        ownUnconfirmedTxHashes: Set<String>,
    ): List<UtxoInfo> {
        // Blockchair lower-cases transaction hashes, but the stored hash can come back from a
        // broadcast proxy, so neither side's casing is guaranteed.
        val ownHashes = ownUnconfirmedTxHashes.mapTo(HashSet()) { it.lowercase() }

        val unusable = rows.count { !it.isUsable }
        if (unusable > 0) {
            Timber.e(
                "Dropped %d of %d Blockchair UTXO rows with no usable transaction_hash/index — " +
                    "the balance understates this address by whatever they held",
                unusable,
                rows.size,
            )
        }

        return rows
            .filter { row ->
                row.isUsable &&
                    row.isSpendable != false &&
                    row.value >= dustThreshold &&
                    (row.blockId > 0 || row.transactionHash.lowercase() in ownHashes)
            }
            .map {
                UtxoInfo(hash = it.transactionHash, amount = it.value, index = it.index.toUInt())
            }
            .sortedBy(UtxoInfo::amount)
    }

    /**
     * The balance of exactly the set [select] admits, in the chain's smallest unit. Defined in
     * terms of [select] rather than alongside it so the two cannot drift.
     */
    fun balance(
        rows: List<BlockChairUtxoInfo>,
        dustThreshold: Long,
        ownUnconfirmedTxHashes: Set<String>,
    ): BigInteger =
        select(rows, dustThreshold, ownUnconfirmedTxHashes).fold(BigInteger.ZERO) { total, utxo ->
            total + BigInteger.valueOf(utxo.amount)
        }

    private val BlockChairUtxoInfo.isUsable: Boolean
        get() = transactionHash.isNotBlank() && index >= 0
}
