package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.Serializable

@Serializable
data class TcyModuleBalanceResponse(val coins: List<ModuleCoin> = emptyList()) {
    @Serializable data class ModuleCoin(val denom: String, val amount: String)
}
