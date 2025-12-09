package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CacaoProviderResponse(
    @SerialName("cacao_address")
    val cacaoAddress: String,
    @SerialName("units")
    val units: String,
    @SerialName("value")
    val value: String,
    @SerialName("pnl")
    val pnl: String,
    @SerialName("deposit_amount")
    val depositAmount: String,
    @SerialName("withdraw_amount")
    val withdrawAmount: String,
    @SerialName("last_deposit_height")
    val lastDepositHeight: Long,
    @SerialName("last_withdraw_height")
    val lastWithdrawHeight: Long
)
