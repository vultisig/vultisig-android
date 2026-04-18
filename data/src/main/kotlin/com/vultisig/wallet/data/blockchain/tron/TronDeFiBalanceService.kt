package com.vultisig.wallet.data.blockchain.tron

import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.blockchain.DeFiService
import com.vultisig.wallet.data.blockchain.model.DeFiBalance
import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.blockchain.model.StakingDetails.Companion.generateId
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import java.io.IOException
import java.math.BigInteger
import timber.log.Timber

class TronDeFiBalanceService(
    private val tronApi: TronApi,
    private val stakingDetailsRepository: StakingDetailsRepository,
) : DeFiService {

    override suspend fun getRemoteDeFiBalance(address: String, vaultId: String): List<DeFiBalance> {
        return try {
            val account = tronApi.getAccount(address)
            val frozenBandwidth = account.frozenBandwidthSun.toBigInteger()
            val frozenEnergy = account.frozenEnergySun.toBigInteger()
            val unfreezing = account.unfreezingTotalSun.toBigInteger()
            val totalFrozen = frozenBandwidth + frozenEnergy

            Timber.d(
                "TronDeFiBalanceService: frozen bandwidth=%s, energy=%s, unfreezing=%s",
                frozenBandwidth,
                frozenEnergy,
                unfreezing,
            )

            persistFrozenBalance(vaultId, totalFrozen)

            if (totalFrozen == BigInteger.ZERO) {
                emptyList()
            } else {
                listOf(
                    DeFiBalance(
                        chain = Chain.Tron,
                        balances =
                            listOf(DeFiBalance.Balance(coin = Coins.Tron.TRX, amount = totalFrozen)),
                    )
                )
            }
        } catch (e: IOException) {
            Timber.w(e, "TronDeFiBalanceService: Network error fetching frozen TRX balance")
            emptyList()
        }
    }

    override suspend fun getCacheDeFiBalance(address: String, vaultId: String): List<DeFiBalance> {
        val cached = stakingDetailsRepository.getStakingDetailsByCoindId(vaultId, Coins.Tron.TRX.id)
        val totalFrozen = cached?.stakeAmount ?: return emptyList()
        if (totalFrozen == BigInteger.ZERO) return emptyList()
        return listOf(
            DeFiBalance(
                chain = Chain.Tron,
                balances = listOf(DeFiBalance.Balance(coin = Coins.Tron.TRX, amount = totalFrozen)),
            )
        )
    }

    private suspend fun persistFrozenBalance(vaultId: String, totalFrozen: BigInteger) {
        try {
            val details =
                StakingDetails(
                    id = Coins.Tron.TRX.generateId(),
                    coin = Coins.Tron.TRX,
                    stakeAmount = totalFrozen,
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
                existing.stakeAmount != totalFrozen ->
                    stakingDetailsRepository.updateStakingDetails(vaultId, details)
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "TronDeFiBalanceService: Failed to persist frozen TRX balance")
        }
    }
}
