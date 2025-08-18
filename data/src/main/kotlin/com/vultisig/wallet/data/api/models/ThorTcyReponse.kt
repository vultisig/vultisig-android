package com.vultisig.wallet.data.api.models

import kotlinx.serialization.*

@Serializable
internal data class ThorTcyBalanceJson(
    @SerialName("denom")
    val denom: String,
    @SerialName("amount")
    val amount: String
)

@Serializable
internal data class ThorTcyBalancesResponseJson(
    @SerialName("balances")
    val balances: List<ThorTcyBalanceJson>,
)
