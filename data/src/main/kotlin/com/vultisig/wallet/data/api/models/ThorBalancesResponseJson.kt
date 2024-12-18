package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VultisigBalanceJson(
    @SerialName("result")
    val result: VultisigBalanceResultJson
)

@Serializable
data class VultisigBalanceResultJson(
    @SerialName("address")
    val address: String,
    @SerialName("tokenBalances")
    val tokenBalances: List<VultisigTokenBalanceJson>,
)

@Serializable
data class VultisigTokenBalanceJson(
    @SerialName("contractAddress")
    val contractAddress: String,
    @SerialName("tokenBalance")
    val balance: String
)