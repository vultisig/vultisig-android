package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ThorBalancesResponseJson(
    @SerialName("balances")
    val balances: List<ThorAssetBalanceJson>
)

@Serializable
internal data class ThorAssetBalanceJson(
    @SerialName("asset")
    val asset: ThorAssetJson,
)

@Serializable
internal data class ThorAssetJson(
    @SerialName("chain")
    val chain: String,
    @SerialName("ticker")
    val ticker: String,
    @SerialName("symbol")
    val symbol: String,
    @SerialName("icon")
    val icon: String? = null,
    @SerialName("name")
    val name: String,
    @SerialName("decimals")
    val decimals: Int,
    @SerialName("contractAddress")
    val contractAddress: String,
)