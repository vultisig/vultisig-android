package com.vultisig.wallet.data.models

import com.google.gson.annotations.SerializedName

internal data class SplTokenAmountJson(
    @SerializedName("amount")
    val amount: String
)

internal data class SplInfoJson(
    @SerializedName("mint")
    val mint: String,
    @SerializedName("tokenAmount")
    val tokenAmount: SplTokenAmountJson
)

internal data class SplParsedJson(
    @SerializedName("info")
    val info: SplInfoJson
)

internal data class SplDataJson(
    @SerializedName("parsed")
    val parsed: SplParsedJson
)

internal data class SplAccountJson(
    @SerializedName("data")
    val data: SplDataJson
)

internal data class SplResponseJson(
    @SerializedName("account")
    val account: SplAccountJson
)

internal data class SplTokenJson(
    @SerializedName("decimals")
    val decimals: Int,
    @SerializedName("tokenList")
    val tokenList: SplTokenListJson,
    @SerializedName("mint")
    val mint: String,
)

internal data class SplTokenListJson(
    @SerializedName("symbol")
    val ticker: String,
    @SerializedName("image")
    val logo: String,
    @SerializedName("extensions")
    val extensions: SplExtensionsJson
)

internal data class SplExtensionsJson(
    @SerializedName("coingeckoId")
    val coingeckoId: String
)


internal data class SplAmountTokenAmountJson(
    @SerializedName("amount")
    val amount: String
)

internal data class SplAmountInfoJson(
    @SerializedName("tokenAmount")
    val tokenAmount: SplAmountTokenAmountJson
)

internal data class SplAmountParsedJson(
    @SerializedName("info")
    val info: SplAmountInfoJson
)

internal data class SplAmountDataJson(
    @SerializedName("parsed")
    val parsed: SplAmountParsedJson
)

internal data class SplAmountAccountJson(
    @SerializedName("data")
    val data: SplAmountDataJson
)

internal data class SplAmountValueJson(
    @SerializedName("account")
    val account: SplAmountAccountJson
)

internal data class SplAmountRpcResponseJson(
    @SerializedName("value")
    val value: List<SplAmountValueJson>
)