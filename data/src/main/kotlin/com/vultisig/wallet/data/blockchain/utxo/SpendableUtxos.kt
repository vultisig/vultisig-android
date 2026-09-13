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
     *
     * [inFlight] is then replayed over the result — see [reconcile] — so a provider snapshot that
     * predates this wallet's own recent broadcasts cannot offer up an input the wallet already
     * spent.
     */
    fun select(
        rows: List<BlockChairUtxoInfo>,
        dustThreshold: Long,
        ownUnconfirmedTxHashes: Set<String>,
        inFlight: List<UtxoInFlightTx> = emptyList(),
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
            .reconcile(inFlight, dustThreshold)
    }

    /**
     * Replays this wallet's own recent broadcasts over a provider snapshot, smallest output first
     * in the result.
     *
     * Blockchair serves the address dashboard from a 60–120 s cache and ingests our broadcast into
     * its mempool view with its own lag, so right after a send the snapshot can still list the
     * inputs that send consumed and not yet the change it paid back. The wallet knows both
     * ([UtxoInFlightTx]), and it is the only party that can: nobody else can spend these outputs.
     *
     * An in-flight transaction is applied only when the snapshot still lists at least one input it
     * spent — the evidence that the snapshot predates it. Then those inputs are removed and the
     * outputs it paid back are added. A transaction none of whose inputs are listed is left alone:
     * the provider has already caught up, and its own view of that transaction's outputs (present
     * as `block_id -1` and admitted through `ownUnconfirmedTxHashes`, or absent) is authoritative.
     * That gate is what keeps the ledger from overriding a provider that already knows better.
     *
     * Replay runs to a fixpoint rather than in recorded order: a pass applies every transaction
     * whose inputs are present, and passes repeat until one applies nothing. A chain of sends
     * therefore works regardless of how its entries are ordered — the second send's input is the
     * first send's change, absent from the snapshot until the first is applied, so the second is
     * skipped in that pass and applied in the next. Ordering by recorded time alone would let a
     * clock adjustment between two sends put the child first, skip it for good, and then have the
     * parent re-offer the very output the child spent. Within a pass, entries go in recorded order
     * so the result is deterministic.
     *
     * The gate has one blind spot, accepted and bounded rather than closed: a transaction the
     * mempool evicted frees its inputs, and a snapshot listing them again is indistinguishable from
     * a stale one. Until its entry expires, replay would then re-spend outputs that no longer
     * exist, and the child is rejected at broadcast — one failed broadcast, no money, the same
     * outcome iOS accepts for a child built on an evicted parent. Nothing local can tell the two
     * apart (no UTXO status ever becomes `FAILED` on eviction), so the exposure is bounded by how
     * long an entry is replayed at all: see `UtxoInFlightRepositoryImpl.REPLAY_WINDOW_MS`.
     *
     * Injected outputs go through the same dust threshold as provider rows; a sub-dust change
     * output is money this wallet will not spend after it confirms either.
     */
    private fun List<UtxoInfo>.reconcile(
        inFlight: List<UtxoInFlightTx>,
        dustThreshold: Long,
    ): List<UtxoInfo> {
        if (inFlight.isEmpty()) return sortedBy(UtxoInfo::amount)

        val candidates = LinkedHashMap<OutPointKey, UtxoInfo>()
        for (utxo in this) candidates[utxo.outPointKey] = utxo

        val pending = inFlight.sortedBy(UtxoInFlightTx::broadcastAt).toMutableList()
        do {
            var applied = false
            val iterator = pending.iterator()
            while (iterator.hasNext()) {
                val tx = iterator.next()
                val spentKeys = tx.spent.map { it.outPointKey }
                if (spentKeys.none { it in candidates }) continue

                spentKeys.forEach(candidates::remove)
                tx.created
                    .filter { it.amount >= dustThreshold }
                    .forEach { candidates.putIfAbsent(it.outPointKey, it) }
                iterator.remove()
                applied = true
            }
        } while (applied && pending.isNotEmpty())

        return candidates.values.sortedBy(UtxoInfo::amount)
    }

    /**
     * Hash casing is normalised for the same reason as in [select]: the stored hash can come from a
     * broadcast proxy, Blockchair's is lower-case.
     */
    private data class OutPointKey(val hash: String, val index: UInt)

    private val UtxoInfo.outPointKey: OutPointKey
        get() = OutPointKey(hash.lowercase(), index)

    /**
     * The balance of exactly the set [select] admits, in the chain's smallest unit. Defined in
     * terms of [select] rather than alongside it so the two cannot drift.
     *
     * Unlike [select], a row that cannot name an outpoint fails the read rather than being dropped:
     * this number gets persisted over the cached balance, and an understated balance written to the
     * cache is worse than a stale one kept. Coin selection can afford to drop the row — fewer
     * inputs can only under-fund a send, never overspend.
     */
    fun balance(
        rows: List<BlockChairUtxoInfo>,
        dustThreshold: Long,
        ownUnconfirmedTxHashes: Set<String>,
        inFlight: List<UtxoInFlightTx> = emptyList(),
    ): BigInteger {
        val unusable = rows.count { !it.isUsable }
        check(unusable == 0) {
            "$unusable of ${rows.size} Blockchair UTXO rows have no usable transaction_hash/index" +
                " — refusing to persist an understated balance"
        }
        return select(rows, dustThreshold, ownUnconfirmedTxHashes, inFlight).fold(
            BigInteger.ZERO
        ) { total, utxo ->
            total + BigInteger.valueOf(utxo.amount)
        }
    }

    /**
     * [reconcile] for a UTXO set that did not come from Blockchair — Dash's own address index,
     * which is built from connected blocks and blind to the mempool in both directions, so it keeps
     * listing a spent input until the spending transaction confirms.
     */
    fun reconcile(
        candidates: List<UtxoInfo>,
        dustThreshold: Long,
        inFlight: List<UtxoInFlightTx>,
    ): List<UtxoInfo> = candidates.reconcile(inFlight, dustThreshold)

    private val BlockChairUtxoInfo.isUsable: Boolean
        get() = transactionHash.isNotBlank() && index >= 0
}
