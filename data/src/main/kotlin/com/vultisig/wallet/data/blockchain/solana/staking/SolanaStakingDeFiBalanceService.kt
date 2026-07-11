package com.vultisig.wallet.data.blockchain.solana.staking

import com.vultisig.wallet.data.blockchain.DeFiService
import com.vultisig.wallet.data.blockchain.model.DeFiBalance
import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.blockchain.model.StakingDetails.Companion.generateId
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

/**
 * DeFi-balance provider for Solana native staking. Unlike Cosmos (one delegated balance per chain)
 * or TON (a single nominator-pool position), a Solana wallet holds N stake accounts, each delegated
 * to exactly one validator. The headline DeFi position is the sum of the delegated stake across
 * those accounts (matching iOS `SolanaStakeDefiViewModel.totalStaked`), with a stake-weighted APY
 * folded in from the [ValidatorMetadataProvider] when available.
 *
 * A network failure falls back to the last-known persisted balance rather than throwing — the DeFi
 * balance fan-out runs each chain inside an `async`, so a thrown exception here would cancel the
 * whole load. Because a stale position list would misreport what the user can deactivate/withdraw,
 * the stake accounts are read fresh every time (see [SolanaStakingService.fetchStakeAccounts]);
 * only the aggregate balance is persisted for the cached read.
 */
class SolanaStakingDeFiBalanceService(
    private val solanaStakingService: SolanaStakingService,
    private val validatorMetadataProvider: ValidatorMetadataProvider,
    private val stakingDetailsRepository: StakingDetailsRepository,
) : DeFiService {

    override suspend fun getRemoteDeFiBalance(address: String, vaultId: String): List<DeFiBalance> {
        return try {
            val accounts = solanaStakingService.fetchStakeAccounts(address)
            val staked =
                accounts.fold(BigInteger.ZERO) { acc, account -> acc + account.delegatedStake }
            val apr = weightedApr(accounts)
            persistStakedBalance(vaultId, staked, apr)
            balanceOf(staked)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "SolanaStakingDeFiBalanceService: failed to fetch stake accounts")
            // Keep the last-known stake rather than erasing it; an empty result would be cached by
            // BalanceRepository and hide the position until the next invalidation.
            getCacheDeFiBalance(address, vaultId)
        }
    }

    override suspend fun getCacheDeFiBalance(address: String, vaultId: String): List<DeFiBalance> {
        return try {
            val cached =
                stakingDetailsRepository.getStakingDetailsByCoindId(vaultId, Coins.Solana.SOL.id)
            balanceOf(cached?.stakeAmount ?: BigInteger.ZERO)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "SolanaStakingDeFiBalanceService: failed to read cached stake balance")
            emptyList()
        }
    }

    /**
     * Stake-weighted average APY (as a percentage, to match [StakingDetails.apr]) across the
     * delegated accounts whose validator has an APY from the metadata provider. Returns null when
     * no validator metadata is available — the provider degrades to on-chain-only, so a missing APY
     * must not blank the position. Metadata `apyEstimate` is a fraction (0.0572); scale to percent.
     */
    private suspend fun weightedApr(accounts: List<SolanaStakeAccount>): Double? {
        val delegated = accounts.filter { it.voter != null && it.delegatedStake.signum() > 0 }
        if (delegated.isEmpty()) return null
        val metadata =
            try {
                validatorMetadataProvider.metadata(delegated.mapNotNull { it.voter }.distinct())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "SolanaStakingDeFiBalanceService: metadata lookup failed")
                emptyMap()
            }

        var weightedSum = BigDecimal.ZERO
        var totalWeight = BigDecimal.ZERO
        for (account in delegated) {
            val apy = metadata[account.voter]?.apyEstimate ?: continue
            val weight = account.delegatedStake.toBigDecimal()
            weightedSum += apy.multiply(weight)
            totalWeight += weight
        }
        if (totalWeight.signum() == 0) return null
        return weightedSum
            .divide(totalWeight, 8, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .toDouble()
    }

    private fun balanceOf(staked: BigInteger): List<DeFiBalance> =
        if (staked <= BigInteger.ZERO) emptyList()
        else
            listOf(
                DeFiBalance(
                    chain = Chain.Solana,
                    balances = listOf(DeFiBalance.Balance(coin = Coins.Solana.SOL, amount = staked)),
                )
            )

    private suspend fun persistStakedBalance(vaultId: String, staked: BigInteger, apr: Double?) {
        try {
            val details =
                StakingDetails(
                    id = Coins.Solana.SOL.generateId(),
                    coin = Coins.Solana.SOL,
                    stakeAmount = staked,
                    apr = apr,
                    estimatedRewards = null,
                    nextPayoutDate = null,
                    rewards = null,
                    rewardsCoin = null,
                )
            val existing =
                stakingDetailsRepository.getStakingDetailsByCoindId(vaultId, Coins.Solana.SOL.id)
            when {
                existing == null -> stakingDetailsRepository.saveStakingDetails(vaultId, details)
                existing.stakeAmount != staked || existing.apr != apr ->
                    stakingDetailsRepository.updateStakingDetails(vaultId, details)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "SolanaStakingDeFiBalanceService: failed to persist staked SOL balance")
        }
    }
}
