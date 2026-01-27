package com.vultisig.wallet.data.blockchain.thorchain

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.blockchain.model.StakingDetails.Companion.generateId
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

class DefaultStakingPositionService @Inject constructor(
    private val thorChainApi: ThorChainApi,
    private val stakingDetailsRepository: StakingDetailsRepository,
) {

    val supportedStakingCoins = listOf(
        Coins.ThorChain.sTCY,
        Coins.ThorChain.yRUNE,
        Coins.ThorChain.yTCY,
    )

    fun getStakingDetails(address: String, vaultId: String): Flow<List<StakingDetails>> = flow {
        try {
            val cachedDetails = stakingDetailsRepository.getStakingDetails(vaultId).filter {
                supportedStakingCoins.contains(it.coin)
            }
            if (cachedDetails.isNotEmpty()) {
                Timber.d("DefaultStakingPositionService: Emitting ${cachedDetails.size} cached staking positions for vault $vaultId")
                emit(cachedDetails)
            }

            val freshDetails = getStakingDetailsFromNetwork(address)

            Timber.d("DefaultStakingPositionService: Emitting ${freshDetails.size} fresh staking positions for vault $vaultId")

            stakingDetailsRepository.saveAllStakingDetails(vaultId, freshDetails)
            
            emit(freshDetails)
        } catch (e: Exception) {
            Timber.e(e, "DefaultStakingPositionService: Error fetching staking details for vault $vaultId")
            val cachedDetails = stakingDetailsRepository.getStakingDetails(vaultId)
            if (cachedDetails.isNotEmpty()) {
                Timber.d("DefaultStakingPositionService: Network error, using ${cachedDetails.size} cached positions")
                emit(cachedDetails)
            } else {
                throw e
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getStakingDetailsFromNetwork(address: String): List<StakingDetails> {
        val balances = try {
            thorChainApi.getBalance(address)
        } catch (e: Exception) {
            Timber.e(e, "DefaultStakingPositionService: Failed to fetch balances from ThorChain API")
            throw e
        }
        
        val stakingDetailsList = mutableListOf<StakingDetails>()
        
        for (coin in supportedStakingCoins) {
            val balance = balances.find { cosmosBalance ->
                cosmosBalance.denom == coin.contractAddress
            }

            val stakeAmount = try {
                balance?.amount?.toBigInteger() ?: BigInteger.ZERO
            } catch (e: NumberFormatException) {
                Timber.e(e, "DefaultStakingPositionService: Failed to parse balance amount: $balance")
                BigInteger.ZERO
            }

            Timber.d("DefaultStakingPositionService: Found ${coin.ticker} balance: $stakeAmount")

            val stakingDetails = StakingDetails(
                id = coin.generateId(),
                coin = coin,
                stakeAmount = stakeAmount,
                apr = null, // Fetch APR if available
                estimatedRewards = null,
                nextPayoutDate = null,
                rewards = null,
                rewardsCoin = null,
            )
            stakingDetailsList.add(stakingDetails)
        }
        
        return stakingDetailsList
    }
}