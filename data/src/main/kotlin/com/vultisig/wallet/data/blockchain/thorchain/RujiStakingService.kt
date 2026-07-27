package com.vultisig.wallet.data.blockchain.thorchain

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.blockchain.model.StakingDetails.Companion.generateId
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber

class RujiStakingService
@Inject
constructor(
    private val thorChainApi: ThorChainApi,
    private val stakingDetailsRepository: StakingDetailsRepository,
) {

    /**
     * Emits the vault's RUJI staking positions: the bonded one (carrying the APR and the claimable
     * USDC revenue) and the auto-compounding one, receipted by sRUJI. They are independent — an
     * account may hold either, both or neither — so both are always emitted and a holder of one is
     * never reported as empty because the other is.
     */
    fun getStakingDetails(address: String, vaultId: String): Flow<List<StakingDetails>> =
        flow {
                val cachedDetails =
                    stakingDetailsRepository.getStakingDetails(vaultId).filter {
                        it.coin.id in RUJI_POSITION_COIN_IDS
                    }
                if (cachedDetails.isNotEmpty()) {
                    Timber.d(
                        "RujiStakingService: Emitting ${cachedDetails.size} cached RUJI staking positions for vault $vaultId"
                    )
                    emit(cachedDetails)
                }

                val freshDetails =
                    try {
                        getStakingDetailsFromNetwork(address)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Timber.e(
                            e,
                            "RujiStakingService: Error fetching RUJI staking details for vault $vaultId",
                        )

                        if (cachedDetails.isNotEmpty()) {
                            Timber.d("RujiStakingService: Using cached RUJI positions due to error")
                            emit(cachedDetails)
                            return@flow
                        }

                        throw e
                    }

                // Emit new fresh positions
                Timber.d(
                    "RujiStakingService: Emitting fresh RUJI staking positions for vault $vaultId"
                )
                emit(freshDetails)

                // Update DB cache
                Timber.d("RujiStakingService: Saving fresh RUJI positions for vault $vaultId")
                stakingDetailsRepository.saveAllStakingDetails(vaultId, freshDetails)
            }
            .flowOn(Dispatchers.IO)

    suspend fun getStakingDetailsFromNetwork(address: String): List<StakingDetails> {
        return try {
            val rujiStakeInfo = thorChainApi.getRujiStakeBalance(address)

            listOf(
                StakingDetails(
                    id = Coins.ThorChain.RUJI.generateId(),
                    coin = Coins.ThorChain.RUJI,
                    stakeAmount = rujiStakeInfo.stakeAmount,
                    apr = null,
                    estimatedRewards = null, // Not available for Ruji
                    nextPayoutDate = null, // Not available for Ruji
                    rewards = rujiStakeInfo.rewardsAmount.toBigDecimal(),
                    rewardsCoin = RUJI_REWARDS_COIN,
                ),
                // The auto-compounding position reinvests its revenue into its own amount, so it
                // carries no separately-claimable rewards and no APR of its own.
                StakingDetails(
                    id = Coins.ThorChain.sRUJI.generateId(),
                    coin = Coins.ThorChain.sRUJI,
                    stakeAmount = rujiStakeInfo.autoCompoundAmount,
                    apr = null,
                    estimatedRewards = null,
                    nextPayoutDate = null,
                    rewards = null,
                    rewardsCoin = null,
                ),
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "RujiStakingService: Failed to fetch RUJI staking details from network")
            throw e
        }
    }

    companion object {
        private val RUJI_POSITION_COIN_IDS =
            setOf(Coins.ThorChain.RUJI.id, Coins.ThorChain.sRUJI.id)

        val RUJI_REWARDS_COIN =
            Coin(
                chain = Chain.ThorChain,
                ticker = "USDC",
                logo = "usdc",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "usd-coin",
                contractAddress = "eth-usdc-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
                isNativeToken = false,
            )
    }
}
