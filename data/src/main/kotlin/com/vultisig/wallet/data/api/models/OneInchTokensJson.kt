package com.vultisig.wallet.data.api.models

import com.google.gson.annotations.SerializedName

data class OneInchTokensJson(
    @SerializedName("tokens")
    val tokens: Map<String, OneInchTokenJson>
)

data class OneInchTokenJson(
    @SerializedName("address")
    val address: String,
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("decimals")
    val decimals: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("logoURI")
    val logoURI: String?,
    @SerializedName("eip2612")
    val eip2612: Boolean? = null,
    @SerializedName("tags")
    val tags: List<String>? = null,
)