package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.models.thorchain.ChurnEntry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.MayachainBondRepository
import com.vultisig.wallet.data.repositories.ThorchainBondRepository
import java.math.BigInteger
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

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
 */
interface BondRewardHistoryUseCase {

    /**
     * What the latest churn paid [bondAddress] on [nodeAddress], or null when there is no churn or
     * the address was not a bond provider on the node at the time.
     */
    suspend fun getLastReward(
        chain: Chain,
        nodeAddress: String,
        bondAddress: String,
    ): BondChurnReward?

    /**
     * What each of the last [MAX_CHURNS] churns paid [bondAddress] on [nodeAddress], newest first.
     *
     * The walk stops at the first churn where the address was not a bond provider, since nothing
     * older can belong to this position. Churns that paid nothing, such as those while the node sat
     * on standby, are left out.
     */
    suspend fun getRewardHistory(
        chain: Chain,
        nodeAddress: String,
        bondAddress: String,
    ): List<BondChurnReward>

    companion object {
        const val MAX_CHURNS = 20
    }
}

internal class BondRewardHistoryUseCaseImpl
@Inject
constructor(
    private val thorchainBondRepository: ThorchainBondRepository,
    private val mayachainBondRepository: MayachainBondRepository,
) : BondRewardHistoryUseCase {

    // A node at a past height never changes, so its payouts are kept for the life of the process.
    private val providerRewards = ConcurrentHashMap<String, Map<String, BigInteger>>()

    override suspend fun getLastReward(
        chain: Chain,
        nodeAddress: String,
        bondAddress: String,
    ): BondChurnReward? {
        val churn = fetchChurns(chain).firstOrNull() ?: return null
        return rewardAt(chain, nodeAddress, bondAddress, churn)
    }

    override suspend fun getRewardHistory(
        chain: Chain,
        nodeAddress: String,
        bondAddress: String,
    ): List<BondChurnReward> {
        val churns = fetchChurns(chain).take(BondRewardHistoryUseCase.MAX_CHURNS)
        val rewards = mutableListOf<BondChurnReward>()
        for (batch in churns.chunked(PARALLEL_REQUESTS)) {
            // Supervised and awaited in order, so a read older than the churn that ends the walk
            // can fail without failing the history it no longer belongs to.
            val reachedEnd = supervisorScope {
                val pending =
                    batch.map { churn ->
                        async { rewardAt(chain, nodeAddress, bondAddress, churn) }
                    }
                for (deferred in pending) {
                    val reward = deferred.await()
                    if (reward == null) {
                        pending.forEach { it.cancel() }
                        return@supervisorScope true
                    }
                    if (reward.amount > BigInteger.ZERO) rewards += reward
                }
                false
            }
            if (reachedEnd) break
        }
        return rewards
    }

    private suspend fun rewardAt(
        chain: Chain,
        nodeAddress: String,
        bondAddress: String,
        churn: ParsedChurn,
    ): BondChurnReward? {
        val amount =
            providerRewardsBefore(chain, nodeAddress, churn.height)[bondAddress] ?: return null
        return BondChurnReward(churnHeight = churn.height, date = churn.date, amount = amount)
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
