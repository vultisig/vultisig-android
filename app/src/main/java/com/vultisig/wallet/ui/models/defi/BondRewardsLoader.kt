package com.vultisig.wallet.ui.models.defi

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.usecases.BondRewardHistoryUseCase
import com.vultisig.wallet.ui.screens.v2.defi.formatBondReward
import com.vultisig.wallet.ui.screens.v2.defi.formatChurnDate
import java.math.BigInteger
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

/**
 * Reads a bonded node's churn rewards for the THORChain and MayaChain DeFi screens and formats them
 * for the Last Reward cell and the Total Rewards Earned sheet.
 *
 * Each view model gets its own instance, which remembers the Last Reward per node so the bonded
 * list, re-emitted on every refresh, does not fall back to a loader for a value it already has.
 * That memory is not synchronised: call it from the view model's main-thread scope only.
 */
internal class BondRewardsLoader
@Inject
constructor(private val bondRewardHistoryUseCase: BondRewardHistoryUseCase) {

    private val lastRewards = mutableMapOf<String, LastRewardUiModel>()
    private val lastRewardsInFlight = mutableSetOf<String>()
    private var lastRewardsGeneration = 0

    fun knownLastReward(nodeAddress: String): LastRewardUiModel? = lastRewards[nodeAddress]

    /**
     * Makes the next [claimLastRewardLoads] read every node again, so a refresh picks up a churn
     * that happened while the screen was open. The screen keeps showing the old value meanwhile.
     *
     * Loads claimed before this call no longer block a new claim for their node, and their results
     * stay out of the memory: cancel them along with the refresh they belong to.
     */
    fun forgetLastRewards() {
        lastRewardsGeneration++
        lastRewards.clear()
        lastRewardsInFlight.clear()
    }

    /** The nodes among [nodeAddresses] with no Last Reward yet and no load running for it. */
    fun claimLastRewardLoads(nodeAddresses: List<String>): List<String> =
        nodeAddresses.filter { it !in lastRewards && lastRewardsInFlight.add(it) }

    suspend fun loadLastReward(
        chain: Chain,
        nodeAddress: String,
        bondAddress: String,
    ): LastRewardUiModel {
        val generation = lastRewardsGeneration
        fun remember(reward: LastRewardUiModel) {
            if (generation == lastRewardsGeneration) lastRewards[nodeAddress] = reward
        }
        return try {
            val reward =
                bondRewardHistoryUseCase
                    .getLastReward(
                        chain = chain,
                        nodeAddress = nodeAddress,
                        bondAddress = bondAddress,
                    )
                    ?.let { LastRewardUiModel.Paid(it.amount.formatReward(chain)) }
                    ?: LastRewardUiModel.Unavailable
            remember(reward)
            reward
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to load the last bond reward for %s", nodeAddress)
            LastRewardUiModel.Unavailable.also { remember(it) }
        } finally {
            if (generation == lastRewardsGeneration) lastRewardsInFlight.remove(nodeAddress)
        }
    }

    /** Fills [sheet] with the node's churn history, or flags it as failed. */
    suspend fun loadHistory(
        chain: Chain,
        sheet: BondRewardHistoryUiModel,
        bondAddress: String,
    ): BondRewardHistoryUiModel =
        try {
            val rewards =
                bondRewardHistoryUseCase.getRewardHistory(
                    chain = chain,
                    nodeAddress = sheet.nodeAddress,
                    bondAddress = bondAddress,
                )
            val total = rewards.fold(BigInteger.ZERO) { acc, reward -> acc + reward.amount }
            sheet.copy(
                isLoading = false,
                isError = false,
                totalEarned = total.formatReward(chain),
                rows =
                    rewards.map {
                        BondRewardRowUiModel(
                            amount = it.amount.formatReward(chain),
                            date = it.date.formatChurnDate(),
                        )
                    },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to load bond reward history for %s", sheet.nodeAddress)
            sheet.copy(isLoading = false, isError = true)
        }

    private fun BigInteger.formatReward(chain: Chain): String {
        val coin = if (chain == Chain.MayaChain) Coins.MayaChain.CACAO else Coins.ThorChain.RUNE
        return formatBondReward(decimals = coin.decimal, symbol = coin.ticker)
    }
}

/** These nodes with the Last Reward of [nodeAddress] replaced by [reward]. */
internal fun List<BondedNodeUiModel>.withLastReward(
    nodeAddress: String,
    reward: LastRewardUiModel,
): List<BondedNodeUiModel> = map {
    if (it.fullAddress == nodeAddress) it.copy(lastReward = reward) else it
}
