package com.vultisig.wallet.data.blockchain.cosmos.staking

import com.vultisig.wallet.data.blockchain.model.DeFiBalance
import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.blockchain.model.StakingDetails.Companion.generateId
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * DeFi-balance provider for Cosmos-SDK staking (LUNA / LUNC / QBTC). The user's DeFi position on
 * the chain is the staked total — delegated balance plus claimable bond-denom rewards (base units)
 * — which is the single source of truth for both the DeFi row and the portfolio total
 * (issue #4763). It feeds the native coin's DeFi balance so an active delegation no longer shows as
 * $0.00.
 *
 * Unlike the THORChain / Maya / Tron [com.vultisig.wallet.data.blockchain.DeFiService]
 * implementations this is **chain-aware**: every staking chain has its own LCD + native coin (and
 * Terra / TerraClassic even share a bech32 address), so the chain must be passed explicitly.
 *
 * A network failure returns an empty list rather than throwing — the DeFi balance fan-out runs each
 * chain inside an `async`, so a thrown exception here would cancel the whole load and blank every
 * DeFi chain (not just Terra).
 */
class CosmosStakingDeFiBalanceService(
    private val cosmosStakingService: CosmosStakingService,
    private val stakingDetailsRepository: StakingDetailsRepository,
) {

    suspend fun getRemoteDeFiBalance(
        chain: Chain,
        address: String,
        vaultId: String,
    ): List<DeFiBalance> {
        val coin = nativeCoin(chain) ?: return emptyList()
        return try {
            val delegated =
                cosmosStakingService.fetchDelegations(chain, address).fold(BigInteger.ZERO) {
                    acc,
                    delegation ->
                    acc + (delegation.balance.amount.toBigIntegerOrNull() ?: BigInteger.ZERO)
                }
            val total = delegated + claimableRewards(chain, address)
            persistStakedBalance(vaultId, coin, total)
            balanceOf(chain, coin, total)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(
                e,
                "CosmosStakingDeFiBalanceService: failed to fetch %s delegations",
                chain.raw,
            )
            emptyList()
        }
    }

    /**
     * Claimable bond-denom rewards in base units, folded into the staked total so the DeFi row
     * reflects delegated + rewards (issue #4763). Rewards arrive as `cosmos.Dec` strings
     * (fractional base units) so floor to whole base units; count only the bond denom, since Terra
     * Classic LCDs sometimes return legacy stability-tax denoms that must not inflate the native
     * staked total. A failed rewards read degrades to zero so the delegated total is still
     * reported.
     */
    private suspend fun claimableRewards(chain: Chain, address: String): BigInteger =
        try {
            val bondDenom = CosmosStakingConfig.entryFor(chain).bondDenom
            cosmosStakingService
                .fetchDelegatorRewards(chain, address)
                .rewards
                .flatMap { it.reward }
                .filter { it.denom == bondDenom }
                .fold(BigInteger.ZERO) { acc, coin ->
                    acc + (coin.amount.toBigDecimalOrNull()?.toBigInteger() ?: BigInteger.ZERO)
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "CosmosStakingDeFiBalanceService: rewards failed for %s", chain.raw)
            BigInteger.ZERO
        }

    /**
     * Persists a staked total already fetched elsewhere (the staking-positions screen's live
     * fan-out) so the cached DeFi-page read reflects it on the next open without a manual pull
     * (#4815). [totalBaseUnits] must be the same delegated + claimable-rewards sum this service
     * computes in [getRemoteDeFiBalance], so the two write paths stay consistent. No-op for a
     * non-staking chain.
     */
    suspend fun persistStakedTotal(chain: Chain, vaultId: String, totalBaseUnits: BigInteger) {
        val coin = nativeCoin(chain) ?: return
        persistStakedBalance(vaultId, coin, totalBaseUnits)
    }

    suspend fun getCacheDeFiBalance(chain: Chain, vaultId: String): List<DeFiBalance> {
        val coin = nativeCoin(chain) ?: return emptyList()
        val cached = stakingDetailsRepository.getStakingDetailsByCoindId(vaultId, coin.id)
        return balanceOf(chain, coin, cached?.stakeAmount ?: BigInteger.ZERO)
    }

    private fun balanceOf(chain: Chain, coin: Coin, total: BigInteger): List<DeFiBalance> =
        if (total <= BigInteger.ZERO) emptyList()
        else listOf(DeFiBalance(chain = chain, balances = listOf(DeFiBalance.Balance(coin, total))))

    private suspend fun persistStakedBalance(vaultId: String, coin: Coin, total: BigInteger) {
        try {
            val details =
                StakingDetails(
                    id = coin.generateId(),
                    coin = coin,
                    stakeAmount = total,
                    apr = null,
                    estimatedRewards = null,
                    nextPayoutDate = null,
                    rewards = null,
                    rewardsCoin = null,
                )
            // Serialize the read-modify-write on this row: both write paths reach here — the DeFi
            // page's getRemoteDeFiBalance and the staking screen's persistStakedTotal — and without
            // a lock a concurrent read-decide-write could interleave and persist a stale total.
            persistLockFor(vaultId, coin.id).withLock {
                val existing = stakingDetailsRepository.getStakingDetailsByCoindId(vaultId, coin.id)
                when {
                    existing == null ->
                        stakingDetailsRepository.saveStakingDetails(vaultId, details)
                    existing.stakeAmount != total ->
                        stakingDetailsRepository.updateStakingDetails(vaultId, details)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "CosmosStakingDeFiBalanceService: failed to persist staked balance")
        }
    }

    private val persistLocks = ConcurrentHashMap<String, Mutex>()

    private fun persistLockFor(vaultId: String, coinId: String): Mutex =
        persistLocks.computeIfAbsent("$vaultId:$coinId") { Mutex() }

    private fun nativeCoin(chain: Chain): Coin? =
        when (chain) {
            Chain.Terra -> Coins.Terra.LUNA
            Chain.TerraClassic -> Coins.TerraClassic.LUNC
            Chain.Qbtc -> Coins.Qbtc.QBTC
            else -> null
        }
}
