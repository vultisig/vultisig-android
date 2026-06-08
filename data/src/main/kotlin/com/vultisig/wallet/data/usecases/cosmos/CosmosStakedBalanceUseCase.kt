package com.vultisig.wallet.data.usecases.cosmos

import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingConfig
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingService
import com.vultisig.wallet.data.models.Chain
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Total staked native amount — delegated balance plus claimable bond-denom rewards — in base units,
 * for a Cosmos-SDK staking address (LUNA / LUNC).
 *
 * Used to fold the staked allocation into the DeFi portfolio row and total, which otherwise only
 * count the (≈0) liquid balance and therefore hide an active delegation as $0.00 (issue #4763).
 *
 * Every read degrades to [BigInteger.ZERO] — a non-staking chain, an unknown bond denom, or a
 * transient LCD failure just omits the staked value rather than failing the whole DeFi load.
 *
 * The last live total is kept in an in-memory, process-lifetime snapshot per address so the DeFi
 * tab's cached emit can render the staked allocation immediately on reopen ([cached]) instead of
 * flashing $0.00 until the background refresh lands. This mirrors [TronDeFiSnapshotCache] (and
 * vultisig-ios), and a transient failure keeps the previous snapshot rather than zeroing it.
 */
interface CosmosStakedBalanceUseCase {
    /** Live read of the staked total; updates the snapshot. Degrades to the last-known value. */
    suspend operator fun invoke(chain: Chain, address: String): BigInteger

    /** Last-known snapshot total with no network; [BigInteger.ZERO] until the first live read. */
    fun cached(chain: Chain, address: String): BigInteger
}

@Singleton
internal class CosmosStakedBalanceUseCaseImpl
@Inject
constructor(private val cosmosStakingService: CosmosStakingService) : CosmosStakedBalanceUseCase {

    private val snapshots = ConcurrentHashMap<String, BigInteger>()

    override suspend fun invoke(chain: Chain, address: String): BigInteger {
        if (!CosmosStakingConfig.isStakingSupported(chain)) return BigInteger.ZERO
        val bondDenom =
            runCatching { CosmosStakingConfig.entryFor(chain).bondDenom }.getOrNull()
                ?: return BigInteger.ZERO

        val delegationsResult =
            runCatching { cosmosStakingService.fetchDelegations(chain, address) }
                .onFailure {
                    Timber.w(it, "DeFi staked balance: delegations failed for %s", chain.raw)
                }
        // Delegations are the dominant component, so a failed read keeps the last-known snapshot
        // rather than reporting a degraded zero that would hide an active delegation on an LCD
        // blip.
        val delegations =
            delegationsResult.getOrElse {
                return cached(chain, address)
            }

        val delegated =
            delegations.fold(BigInteger.ZERO) { acc, delegation ->
                acc + (delegation.balance.amount.toBigIntegerOrNull() ?: BigInteger.ZERO)
            }

        // Rewards arrive as cosmos.Dec strings (fractional base units), so floor to whole base
        // units. Count only the bond denom — Terra Classic LCDs sometimes return legacy
        // stability-tax denoms that must not inflate the native staked total.
        val rewards =
            runCatching { cosmosStakingService.fetchDelegatorRewards(chain, address) }
                .onFailure { Timber.w(it, "DeFi staked balance: rewards failed for %s", chain.raw) }
                .getOrNull()
                ?.rewards
                ?.flatMap { it.reward }
                ?.filter { it.denom == bondDenom }
                ?.fold(BigInteger.ZERO) { acc, coin ->
                    acc + (coin.amount.toBigDecimalOrNull()?.toBigInteger() ?: BigInteger.ZERO)
                } ?: BigInteger.ZERO

        val total = delegated + rewards
        snapshots[snapshotKey(chain, address)] = total
        return total
    }

    override fun cached(chain: Chain, address: String): BigInteger =
        snapshots[snapshotKey(chain, address)] ?: BigInteger.ZERO

    private fun snapshotKey(chain: Chain, address: String): String = "${chain.id}|$address"
}
