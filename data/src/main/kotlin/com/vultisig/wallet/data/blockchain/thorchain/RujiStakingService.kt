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

        // Distribution did not happened yet, API returns 0%
        // Hence we'll hide APR while it is 0
        val apr = if (rujiStakeInfo.apr == 0.0) {
            null
        } else {
            rujiStakeInfo.apr
        }

        return StakingDetails(
            stakeAmount = rujiStakeInfo.stakeAmount,
            apr = apr,
            estimatedRewards = null, // Not available for Ruji
            nextPayoutDate = null, // Not available for Ruji
            rewards = rujiStakeInfo.rewardsAmount.toBigDecimal(),
            rewardsCoin = rewardsCoin,
        )
    }
}