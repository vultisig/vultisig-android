package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.Serializable

/** Balance response for the TCY module, listing all held coin denominations. */
@Serializable
data class TcyModuleBalanceResponse(val coins: List<ModuleCoin> = emptyList()) {
    /** A single coin held by the TCY module with its denomination and amount. */
    @Serializable data class ModuleCoin(val denom: String, val amount: String)
}
