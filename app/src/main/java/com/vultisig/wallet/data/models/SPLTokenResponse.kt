package com.vultisig.wallet.data.models

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
internal data class SPLTokenAmount(
    @SerializedName("amount")
    val amount: String
)

@Keep
internal data class SPLInfo(
    @SerializedName("mint")
    val mint: String,
    @SerializedName("tokenAmount")
    val tokenAmount: SPLTokenAmount
)

@Keep
internal data class SPLParsed(
    @SerializedName("info")
    val info: SPLInfo
)

@Keep
internal data class SPLData(
    @SerializedName("parsed")
    val parsed: SPLParsed
)

@Keep
internal data class SPLAccount(
    @SerializedName("data")
    val data: SPLData
)

@Keep
internal data class SPLJsonResponse(
    @SerializedName("account")
    val account: SPLAccount
)

@Keep
internal data class SPLListTokenResponse(
    @SerializedName("decimals")
    val decimals: Int,
    @SerializedName("tokenList")
    val tokenList: SPLTokenList
)

@Keep
internal data class SPLTokenList(
    @SerializedName("symbol")
    val ticker: String,
    @SerializedName("image")
    val logo: String,
    @SerializedName("extensions")
    val extensions: SPLExtensions
)

@Keep
internal data class SPLExtensions(
    @SerializedName("coingeckoId")
    val coingeckoId: String
)