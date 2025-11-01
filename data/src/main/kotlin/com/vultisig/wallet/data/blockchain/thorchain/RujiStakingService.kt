package com.vultisig.wallet.data.blockchain.thorchain

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.blockchain.model.StakingDetails

class RujiStakingService(
    val thorChainApi: ThorChainApi,
){
    suspend fun getStakingDetails(address: String): StakingDetails {
        val rujiStakeInfo = thorChainApi.getRujiStakeBalance(address)
    }
}