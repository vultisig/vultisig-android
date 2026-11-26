package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.models.thorchain.ChurnEntry
import com.vultisig.wallet.data.api.models.thorchain.MIDGARD_ACTIONS_PAGE_LIMIT
import com.vultisig.wallet.data.api.models.thorchain.MidgardUnbondAction
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.BondRewardCacheRepository
import com.vultisig.wallet.data.repositories.MayachainBondRepository
import com.vultisig.wallet.data.repositories.ThorchainBondRepository
import java.math.BigInteger
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import timber.log.Timber

/** What one churn paid a bond provider, in the chain's base units. */
data class BondChurnReward(val churnHeight: Long, val date: Date, val amount: BigInteger)

/**
 * A bond provider's per-churn rewards on one THORChain or MayaChain node.
 *
 * Provider rewards never reach the wallet as a transfer: the node accrues an award every block, and
 * each churn pays the operator fee and compounds the rest into the providers' bonds. So the history
 * is read off the node one block before each churn, while the award about to be paid is still on
 * its ledger. THORNode reports only the node's award, so it is split by the provider's bond share
 * at that same block; MAYANode reports each provider's payout directly.
 *
 * The node is only asked about churns from the last [BOND_REWARD_FETCH_WINDOW]. Every reward read
 * is kept on the device for good, so the history grows each time it is opened and older churns
 * come from the device alone.
 */
interface BondRewardHistoryUseCase {

    /**
     * What the latest churn paid [bondAddress] on [nodeAddress]: the saved row, else a read of the
     * node when the churn is recent enough. Null when there is no churn, the address was not a bond
     * provider at the time, or the churn is older than the window and was never saved.
     */
    suspend fun getLastReward(
        chain: Chain,
        nodeAddress: String,
        bondAddress: String,
    ): BondChurnReward?

    /**
     * What each churn paid [bondAddress] on [nodeAddress], newest first, from saved rows and recent
     * reads together.
     *
     * The walk stops at the first churn where the address was not a bond provider, since nothing
     * older can belong to this position. A failed read ends it too, since that churn may be where
     * the position began. A churn older than the window that was never saved is skipped, so saved
     * rows behind it still show. Churns that paid nothing, such as those while the node sat on
     * standby, are left out. Throws only when nothing could be shown and a churn was unknown.
     */
    suspend fun getRewardHistory(
        chain: Chain,
        nodeAddress: String,
        bondAddress: String,
    ): List<BondChurnReward>

    companion object {
        // liquify only keeps about 13 days of node state, so older heights are never requested.
        val BOND_REWARD_FETCH_WINDOW: Duration = 13.days
    }
}

internal class BondRewardHistoryUseCaseImpl
@Inject
constructor(
    private val thorchainBondRepository: ThorchainBondRepository,
    private val mayachainBondRepository: MayachainBondRepository,
    private val bondRewardCacheRepository: BondRewardCacheRepository,
    private val clock: Clock,
) : BondRewardHistoryUseCase {

    // A node at a past height never changes, so its payouts are kept for the life of the process.
    private val providerRewards = ConcurrentHashMap<String, Map<String, BigInteger>>()

    override suspend fun getLastReward(
        chain: Chain,
        nodeAddress: String,
        bondAddress: String,
    ): BondChurnReward? {
        val churn = fetchChurns(chain).firstOrNull() ?: return null
        val saved = bondRewardCacheRepository.getRewards(chain, nodeAddress, bondAddress)
        return when (val read = readChurn(chain, nodeAddress, bondAddress, churn, saved)) {
            is ChurnRead.Reward -> {
                saveNew(chain, nodeAddress, bondAddress, listOf(read.reward), saved)
                read.reward
            }
            ChurnRead.NotAProvider,
            is ChurnRead.OutsideWindow -> null
        }
    }

    override suspend fun getRewardHistory(
        chain: Chain,
        nodeAddress: String,
        bondAddress: String,
    ): List<BondChurnReward> {
        val saved = bondRewardCacheRepository.getRewards(chain, nodeAddress, bondAddress)
        val walk = walk(chain, nodeAddress, bondAddress, fetchChurns(chain), saved)
        saveNew(chain, nodeAddress, bondAddress, walk.steps.mapNotNull { it.reward }, saved)
        val rewards =
            bridgeGaps(chain, nodeAddress, bondAddress, walk.steps).filter {
                it.amount > BigInteger.ZERO
            }
        if (rewards.isEmpty() && (walk.failure != null || walk.steps.any { it.isGap })) {
            walk.failure?.let { throw it }
            error("No reward history in reach for $nodeAddress")
        }
        return rewards
    }

    /**
     * Each churn newest first, as a reward or a gap (never saved and too old to read), up to the
     * first churn where the address was not a provider or whose read failed.
     */
    private suspend fun walk(
        chain: Chain,
        nodeAddress: String,
        bondAddress: String,
        churns: List<ParsedChurn>,
        saved: Map<Long, BondChurnReward>,
    ): Walk {
        val steps = mutableListOf<WalkStep>()
        var failure: Exception? = null
        for (batch in churns.chunked(PARALLEL_REQUESTS)) {
            // Supervised and awaited in order, so a read older than the churn that ends the walk
            // can fail without touching the history it no longer belongs to.
            val reachedEnd = supervisorScope {
                val pending =
                    batch.map { churn ->
                        async { readChurn(chain, nodeAddress, bondAddress, churn, saved) }
                    }
                for (deferred in pending) {
                    val churnRead =
                        try {
                            deferred.await()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failure = e
                            null
                        }
                    when (churnRead) {
                        is ChurnRead.Reward -> steps += WalkStep(churnRead.reward.churnHeight, churnRead.reward)
                        is ChurnRead.OutsideWindow -> steps += WalkStep(churnRead.churnHeight, null)
                        ChurnRead.NotAProvider,
                        null -> {
                            pending.forEach { it.cancel() }
                            return@supervisorScope true
                        }
                    }
                }
                false
            }
            if (reachedEnd) break
        }
        return Walk(steps, failure)
    }

    /**
     * The rewards in [steps] down to the first gap the bond may have left the node in: an unbond
     * from this node between the churns around it. Midgard is asked once, and only when a gap has
     * rewards below it; where it cannot answer, the history ends at that gap.
     */
    private suspend fun bridgeGaps(
        chain: Chain,
        nodeAddress: String,
        bondAddress: String,
        steps: List<WalkStep>,
    ): List<BondChurnReward> {
        val gaps = gapSpans(steps)
        if (gaps.isEmpty()) return steps.mapNotNull { it.reward }

        val unbondHeights =
            nodeUnbondHeights(
                chain = chain,
                nodeAddress = nodeAddress,
                bondAddress = bondAddress,
                fromHeight = gaps.values.minOf { it.fromHeight },
                beforeHeight =
                    if (gaps.values.any { it.beforeHeight == null }) null
                    else gaps.values.maxOf { requireNotNull(it.beforeHeight) },
            )
        val rewards = mutableListOf<BondChurnReward>()
        for ((index, step) in steps.withIndex()) {
            val reward = step.reward
            if (reward != null) {
                rewards += reward
                continue
            }
            val gap = gaps[index] ?: continue
            if (unbondHeights == null || unbondHeights.any { it in gap }) break
        }
        return rewards
    }

    /**
     * Heights of the unbonds from [nodeAddress] that took [bondAddress]'s bond, whoever sent them,
     * at [fromHeight] or later and before [beforeHeight] (no upper bound when null); null when
     * Midgard could not say for certain.
     */
    private suspend fun nodeUnbondHeights(
        chain: Chain,
        nodeAddress: String,
        bondAddress: String,
        fromHeight: Long,
        beforeHeight: Long?,
    ): List<Long>? {
        val actions =
            try {
                when (chain) {
                    Chain.ThorChain ->
                        thorchainBondRepository.getUnbondActions(
                            nodeAddress,
                            fromHeight,
                            beforeHeight,
                        )
                    Chain.MayaChain ->
                        mayachainBondRepository.getUnbondActions(
                            nodeAddress,
                            fromHeight,
                            beforeHeight,
                        )
                    else -> error("Bond rewards are not supported on ${chain.raw}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to read unbonds from %s", nodeAddress)
                return null
            }
        // A full page may leave unbonds out.
        if (actions.size >= MIDGARD_ACTIONS_PAGE_LIMIT) return null
        val heights = actions.map { it.height.toLongOrNull() ?: return null }
        // An unbond that names no provider and no sender may have taken this bond.
        return heights.filterIndexed { index, _ ->
            actions[index].provider(chain, nodeAddress)?.equals(bondAddress, ignoreCase = true)
                ?: true
        }
    }

    private suspend fun readChurn(
        chain: Chain,
        nodeAddress: String,
        bondAddress: String,
        churn: ParsedChurn,
        saved: Map<Long, BondChurnReward>,
    ): ChurnRead {
        saved[churn.height]?.let {
            return ChurnRead.Reward(it)
        }
        val fetchSince = clock.now() - BondRewardHistoryUseCase.BOND_REWARD_FETCH_WINDOW
        if (churn.date.time < fetchSince.toEpochMilliseconds()) {
            return ChurnRead.OutsideWindow(churn.height)
        }
        val amount =
            providerRewardsBefore(chain, nodeAddress, churn.height)[bondAddress]
                ?: return ChurnRead.NotAProvider
        return ChurnRead.Reward(
            BondChurnReward(churnHeight = churn.height, date = churn.date, amount = amount)
        )
    }

    private suspend fun saveNew(
        chain: Chain,
        nodeAddress: String,
        bondAddress: String,
        rewards: List<BondChurnReward>,
        saved: Map<Long, BondChurnReward>,
    ) {
        bondRewardCacheRepository.saveRewards(
            chain,
            nodeAddress,
            bondAddress,
            rewards.filter { it.churnHeight !in saved },
        )
    }

    private suspend fun providerRewardsBefore(
        chain: Chain,
        nodeAddress: String,
        churnHeight: Long,
    ): Map<String, BigInteger> {
        val height = churnHeight - 1
        val key = "${chain.raw}:$nodeAddress:$height"
        providerRewards[key]?.let {
            return it
        }
        val rewards =
            when (chain) {
                Chain.ThorChain -> {
                    val node = thorchainBondRepository.getNodeDetailsAtHeight(nodeAddress, height)
                    NodeRewardSnapshot(
                            award = node.currentAward.toBigIntegerOrZero(),
                            operatorFeeBps =
                                node.bondProviders.nodeOperatorFee.toBigIntegerOrZero(),
                            providerBonds =
                                node.bondProviders.providers.associate {
                                    it.bondAddress to it.bond.toBigIntegerOrZero()
                                },
                        )
                        .providerShares()
                }
                // MAYANode splits the award itself, weighing each provider's CACAO-valued `bond`
                // and paying the operator fee into the operator's own row.
                Chain.MayaChain ->
                    mayachainBondRepository
                        .getNodeDetailsAtHeight(nodeAddress, height)
                        .bondProviders
                        .providers
                        .associate { it.bondAddress to it.reward.toBigIntegerOrZero() }
                else -> error("Bond rewards are not supported on ${chain.raw}")
            }
        providerRewards[key] = rewards
        return rewards
    }

    private suspend fun fetchChurns(chain: Chain): List<ParsedChurn> {
        val churns =
            when (chain) {
                Chain.ThorChain -> thorchainBondRepository.getChurns()
                Chain.MayaChain -> mayachainBondRepository.getChurns()
                else -> error("Bond rewards are not supported on ${chain.raw}")
            }
        return churns.mapNotNull { it.parse() }.sortedByDescending { it.height }
    }

    private companion object {
        const val PARALLEL_REQUESTS = 5
    }
}

/**
 * The provider whose bond this unbond from [nodeAddress] took: the address the memo names besides
 * the node, else the sender, which is then the provider unbonding itself. Null when Midgard gave
 * neither.
 *
 * Read by address rather than position, since the memo grammars differ: THORChain
 * `UNBOND:<node>:<amount>[:<provider>]`, MAYAChain `UNBOND:<asset>:<units>:<node>[:<provider>]` and
 * its older `UNBOND:<node>:<provider>[:<units>]`.
 */
internal fun MidgardUnbondAction.provider(chain: Chain, nodeAddress: String): String? {
    val addressPrefix =
        when (chain) {
            Chain.ThorChain -> "thor1"
            Chain.MayaChain -> "maya1"
            else -> error("Bond rewards are not supported on ${chain.raw}")
        }
    return memo?.split(':')?.drop(1)?.firstOrNull {
        it.startsWith(addressPrefix, ignoreCase = true) && !it.equals(nodeAddress, ignoreCase = true)
    } ?: sender
}

/** One churn on the walk: its reward, or null for a gap (never saved and too old to read). */
private data class WalkStep(val churnHeight: Long, val reward: BondChurnReward?) {
    val isGap: Boolean
        get() = reward == null
}

/**
 * Each gap that has rewards below it, by its index in [steps], to the blocks an unbond leaving the
 * node during it could be in. A reward is read the block before its churn, so the span runs from
 * the churn below the gap up to, not including, the churn above it (open when none is above).
 */
private fun gapSpans(steps: List<WalkStep>): Map<Int, GapSpan> {
    val spans = mutableMapOf<Int, GapSpan>()
    var newerHeight: Long? = null
    for ((index, step) in steps.withIndex()) {
        if (!step.isGap) {
            newerHeight = step.churnHeight
            continue
        }
        val older = steps.subList(index + 1, steps.size).firstOrNull { !it.isGap } ?: break
        spans[index] = GapSpan(fromHeight = older.churnHeight, beforeHeight = newerHeight)
    }
    return spans
}

/** Blocks from [fromHeight] up to, not including, [beforeHeight] (no end when null). */
private data class GapSpan(val fromHeight: Long, val beforeHeight: Long?) {
    operator fun contains(height: Long): Boolean =
        height >= fromHeight && (beforeHeight == null || height < beforeHeight)
}

/** The walk's churns newest first, and the read failure that ended it, if one did. */
private data class Walk(val steps: List<WalkStep>, val failure: Exception?)

/** What the walk knows about one churn. */
private sealed interface ChurnRead {
    /** A reward, zero included, saved or just read. */
    data class Reward(val reward: BondChurnReward) : ChurnRead

    /** The node was read and the address is not among its bond providers: the position ends. */
    data object NotAProvider : ChurnRead

    /** Never saved and too old to ask the node about. */
    data class OutsideWindow(val churnHeight: Long) : ChurnRead
}

internal data class NodeRewardSnapshot(
    val award: BigInteger,
    val operatorFeeBps: BigInteger,
    val providerBonds: Map<String, BigInteger>,
) {
    /**
     * The award minus the operator fee, split by [bondAddress]'s share of the node's bond; null
     * when the address is not a bond provider on the node.
     */
    fun providerShare(bondAddress: String): BigInteger? {
        val myBond = providerBonds[bondAddress] ?: return null
        val totalBond = providerBonds.values.fold(BigInteger.ZERO, BigInteger::add)
        if (totalBond.signum() <= 0) return BigInteger.ZERO
        val providersPart = (FEE_DENOMINATOR - operatorFeeBps).max(BigInteger.ZERO)
        return award * providersPart * myBond / (FEE_DENOMINATOR * totalBond)
    }

    /** [providerShare] for every provider on the node. */
    fun providerShares(): Map<String, BigInteger> =
        providerBonds.keys.associateWith { providerShare(it) ?: BigInteger.ZERO }

    private companion object {
        val FEE_DENOMINATOR: BigInteger = BigInteger.valueOf(10_000)
    }
}

private data class ParsedChurn(val height: Long, val date: Date)

// Midgard reports a churn's date in nanoseconds.
private fun ChurnEntry.parse(): ParsedChurn? {
    val height = height.toLongOrNull() ?: return null
    val nanos = date.toLongOrNull() ?: return null
    return ParsedChurn(height = height, date = Date(nanos / 1_000_000))
}

private fun String.toBigIntegerOrZero(): BigInteger = toBigIntegerOrNull() ?: BigInteger.ZERO
