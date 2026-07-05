package com.vultisig.wallet.data.api.models.cosmos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Cw20TokenInfoResponseJson(@SerialName("data") val data: Cw20TokenInfoJson? = null)

/**
 * CW20 `{"token_info":{}}` smart-query reply. Fields are optional because the query is issued
 * against arbitrary user-pasted contract addresses — a non-CW20 contract may answer with an
 * unrelated shape.
 */
@Serializable
data class Cw20TokenInfoJson(
    @SerialName("name") val name: String? = null,
    @SerialName("symbol") val symbol: String? = null,
    @SerialName("decimals") val decimals: Int? = null,
)
