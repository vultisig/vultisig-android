package com.vultisig.wallet.data.blockchain.thorchain

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.blockchain.model.StakingDetails.Companion.generateId
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

class RujiStakingService @Inject constructor(
    private val thorChainApi: ThorChainApi,
    private val stakingDetailsRepository: StakingDetailsRepository,
){

    fun getStakingDetails(address: String, vaultId: String): Flow<StakingDetails?> = flow {
        try {
            val cachedDetails = stakingDetailsRepository.getStakingDetails(vaultId, Coins.ThorChain.RUJI.id)
            if (cachedDetails != null) {
                Timber.d("RujiStakingService: Emitting cached RUJI staking position for vault $vaultId")
                emit(cachedDetails)
            }

            // Fetch fresh data from network
            val freshDetails = getStakingDetailsFromNetwork(address, vaultId)

            if (freshDetails != null) {
                Timber.d("RujiStakingService: Emitting fresh RUJI staking position for vault $vaultId")
                // Update cache
                stakingDetailsRepository.deleteStakingDetails(vaultId, Coins.ThorChain.RUJI.id)
                stakingDetailsRepository.saveStakingDetails(vaultId, freshDetails)

                emit(freshDetails)
            } else {
                Timber.d("RujiStakingService: No RUJI staking position found for vault $vaultId")

                // Clear cache if no position exists
                stakingDetailsRepository.deleteStakingDetails(vaultId, Coins.ThorChain.RUJI.id)

                emit(null)
            }

        } catch (e: Exception) {
            Timber.e(e, "RujiStakingService: Error fetching RUJI staking details for vault $vaultId")

            // If network fails, try to emit cached data
            val cachedDetails = stakingDetailsRepository.getStakingDetails(vaultId, Coins.ThorChain.RUJI.id)
            if (cachedDetails != null) {
                Timber.d("RujiStakingService: Network error, using cached RUJI position")
                emit(cachedDetails)
            } else {
                // No cache available, propagate the error
                throw e
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun getStakingDetailsFromNetwork(address: String, vaultId: String): StakingDetails? {
        return try {
            val rujiStakeInfo = thorChainApi.getRujiStakeBalance(address)

            if (rujiStakeInfo.stakeAmount <= BigInteger.ZERO) {
                Timber.d("RujiStakingService: No RUJI staked for address $address")
                return null
            }

            val rewardsCoin = Coin(
                chain = Chain.ThorChain,
                ticker = "USDC",
                logo = "usdc",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "usd-coin",
                contractAddress = rujiStakeInfo.rewardsTicker,
                isNativeToken = false
            )

            val apr = if (rujiStakeInfo.apr == 0.0) {
                null
            } else {
                rujiStakeInfo.apr
            }

            StakingDetails(
                id = Coins.ThorChain.RUJI.generateId(),
                coin = Coins.ThorChain.RUJI,
                stakeAmount = rujiStakeInfo.stakeAmount,
                apr = apr,
                estimatedRewards = null, // Not available for Ruji
                nextPayoutDate = null, // Not available for Ruji
                rewards = rujiStakeInfo.rewardsAmount.toBigDecimal(),
                rewardsCoin = rewardsCoin,
            )
        } catch (e: Exception) {
            Timber.e(e, "RujiStakingService: Failed to fetch RUJI staking details from network")
            throw e
        }
    }
}