package com.vultisig.wallet.data.api.models

import com.google.gson.annotations.SerializedName

data class SplTokenAmountJson(
    @SerializedName("amount")
    val amount: String
)

data class SplInfoJson(
    @SerializedName("mint")
    val mint: String,
    @SerializedName("tokenAmount")
    val tokenAmount: SplTokenAmountJson
)

data class SplParsedJson(
    @SerializedName("info")
    val info: SplInfoJson
)

data class SplDataJson(
    @SerializedName("parsed")
    val parsed: SplParsedJson
)

data class SplAccountJson(
    @SerializedName("data")
    val data: SplDataJson
)

data class SplResponseJson(
    @SerializedName("account")
    val account: SplAccountJson
)

data class SplTokenJson(
    @SerializedName("decimals")
    val decimals: Int,
    @SerializedName("tokenList")
    val tokenList: SplTokenListJson,
    @SerializedName("mint")
    val mint: String,
)

data class SplTokenListJson(
    @SerializedName("symbol")
    val ticker: String,
    @SerializedName("image")
    val logo: String,
    @SerializedName("extensions")
    val extensions: SplExtensionsJson
)

data class SplExtensionsJson(
    @SerializedName("coingeckoId")
    val coingeckoId: String
)


data class SplAmountTokenAmountJson(
    @SerializedName("amount")
    val amount: String
)

data class SplAmountInfoJson(
    @SerializedName("tokenAmount")
    val tokenAmount: SplAmountTokenAmountJson
)

data class SplAmountParsedJson(
    @SerializedName("info")
    val info: SplAmountInfoJson
)

data class SplAmountDataJson(
    @SerializedName("parsed")
    val parsed: SplAmountParsedJson
)

data class SplAmountAccountJson(
    @SerializedName("data")
    val data: SplAmountDataJson
)

data class SplAmountValueJson(
    @SerializedName("account")
    val account: SplAmountAccountJson
)

data class SplAmountRpcResponseJson(
    @SerializedName("value")
    val value: List<SplAmountValueJson>
)