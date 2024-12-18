package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ThorAssetBalanceJson(
    @SerialName("result")
    val result: ThorAssetBalanceResultJson
)

@Serializable
data class ThorAssetBalanceResultJson(
    @SerialName("address")
    val address: String,
    @SerialName("tokenBalances")
    val tokenBalances: List<ThorAssetTokenBalanceJson>,
)

@Serializable
data class ThorAssetTokenBalanceJson(
    @SerialName("contractAddress")
    val contractAddress: String,
    @SerialName("tokenBalance")
    val balance: String
)