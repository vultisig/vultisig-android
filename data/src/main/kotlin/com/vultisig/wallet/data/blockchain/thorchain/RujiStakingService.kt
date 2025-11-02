package com.vultisig.wallet.data.blockchain.thorchain

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import javax.inject.Inject

class RujiStakingService @Inject constructor(
    private val thorChainApi: ThorChainApi,
){
    suspend fun getStakingDetails(address: String): StakingDetails {
        val rujiStakeInfo = thorChainApi.getRujiStakeBalance(address)

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

        return StakingDetails(
            stakeAmount = rujiStakeInfo.stakeAmount,
            apr = rujiStakeInfo.apr,
            estimatedRewards = null, // Not available for Ruji
            nextPayoutDate = null, // Not available for Ruji
            rewards = rujiStakeInfo.rewardsAmount.toBigDecimal(),
            rewardsCoin = rewardsCoin,
        )
    }
}