package com.vultisig.wallet.data.blockchain.cosmos.staking

import com.vultisig.wallet.data.blockchain.model.DeFiBalance
import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.blockchain.model.StakingDetails.Companion.generateId
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import java.math.BigInteger
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

/**
 * DeFi-balance provider for LUNA / LUNC staking. The user's DeFi position on Terra / TerraClassic
 * is the sum of their delegation balances (base units) — mirrors iOS
 * `DefiBalanceService.cosmosStakingTotalBalance`, where the chain's DeFi balance is the staked
 * total.
 *
 * Unlike the THORChain / Maya / Tron [com.vultisig.wallet.data.blockchain.DeFiService]
 * implementations this is **chain-aware**: Terra and TerraClassic share the same bech32 address, so
 * the chain must be passed explicitly to pick the right LCD + native coin.
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
            val total =
                cosmosStakingService.fetchDelegations(chain, address).fold(BigInteger.ZERO) {
                    acc,
                    delegation ->
                    acc + (delegation.balance.amount.toBigIntegerOrNull() ?: BigInteger.ZERO)
                }
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
            val existing = stakingDetailsRepository.getStakingDetailsByCoindId(vaultId, coin.id)
            when {
                existing == null -> stakingDetailsRepository.saveStakingDetails(vaultId, details)
                existing.stakeAmount != total ->
                    stakingDetailsRepository.updateStakingDetails(vaultId, details)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "CosmosStakingDeFiBalanceService: failed to persist staked balance")
        }
    }

    private fun nativeCoin(chain: Chain): Coin? =
        when (chain) {
            Chain.Terra -> Coins.Terra.LUNA
            Chain.TerraClassic -> Coins.TerraClassic.LUNC
            else -> null
        }
}
