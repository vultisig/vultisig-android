package com.vultisig.wallet.data.blockchain.tron

import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.blockchain.DeFiService
import com.vultisig.wallet.data.blockchain.model.DeFiBalance
import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.blockchain.model.StakingDetails.Companion.generateId
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import java.math.BigInteger
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

class TronDeFiBalanceService(
    private val tronApi: TronApi,
    private val stakingDetailsRepository: StakingDetailsRepository,
) : DeFiService {

    override suspend fun getRemoteDeFiBalance(address: String, vaultId: String): List<DeFiBalance> {
        return try {
            val account = tronApi.getAccount(address)
            val totalLocked = account.defiLockedTotalSun.toBigInteger()

            Timber.d(
                "TronDeFiBalanceService: frozen bandwidth=%d, energy=%d, unfreezing=%d",
                account.frozenBandwidthSun,
                account.frozenEnergySun,
                account.unfreezingTotalSun,
            )

            persistLockedBalance(vaultId, totalLocked)
            balanceOf(totalLocked)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "TronDeFiBalanceService: Failed to fetch locked TRX balance")
            // Keep the last-known position rather than erasing it: BalanceRepository caches this
            // result, so an empty list would hide the Tron row until the next invalidation.
            getCacheDeFiBalance(address, vaultId)
        }
    }

    override suspend fun getCacheDeFiBalance(address: String, vaultId: String): List<DeFiBalance> =
        try {
            val cached =
                stakingDetailsRepository.getStakingDetailsByCoindId(vaultId, Coins.Tron.TRX.id)
            balanceOf(cached?.stakeAmount ?: BigInteger.ZERO)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "TronDeFiBalanceService: Failed to read the cached TRX position")
            emptyList()
        }

    private fun balanceOf(totalLocked: BigInteger): List<DeFiBalance> =
        if (totalLocked <= BigInteger.ZERO) emptyList()
        else
            listOf(
                DeFiBalance(
                    chain = Chain.Tron,
                    balances =
                        listOf(DeFiBalance.Balance(coin = Coins.Tron.TRX, amount = totalLocked)),
                )
            )

    private suspend fun persistLockedBalance(vaultId: String, totalLocked: BigInteger) {
        try {
            val details =
                StakingDetails(
                    id = Coins.Tron.TRX.generateId(),
                    coin = Coins.Tron.TRX,
                    stakeAmount = totalLocked,
                    apr = null,
                    estimatedRewards = null,
                    nextPayoutDate = null,
                    rewards = null,
                    rewardsCoin = null,
                )
            val existing =
                stakingDetailsRepository.getStakingDetailsByCoindId(vaultId, Coins.Tron.TRX.id)
            when {
                existing == null -> stakingDetailsRepository.saveStakingDetails(vaultId, details)
                existing.stakeAmount != totalLocked ->
                    stakingDetailsRepository.updateStakingDetails(vaultId, details)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "TronDeFiBalanceService: Failed to persist locked TRX balance")
        }
    }
}
