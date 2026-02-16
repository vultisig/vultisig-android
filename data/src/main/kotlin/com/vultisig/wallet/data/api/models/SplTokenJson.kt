package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SplTokenAmountJson(
    @SerialName("amount")
    val amount: String,
)

@Serializable
data class SplInfoJson(
    @SerialName("mint")
    val mint: String,
    @SerialName("tokenAmount")
    val tokenAmount: SplTokenAmountJson,
)

@Serializable
data class SplParsedJson(
    @SerialName("info")
    val info: SplInfoJson,
)

@Serializable
data class SplDataJson(
    @SerialName("parsed")
    val parsed: SplParsedJson,
)

@Serializable
data class SplAccountJson(
    @SerialName("data")
    val data: SplDataJson,
)

@Serializable
data class SplResponseJson(
    @SerialName("error")
    val error: SplTokenErrorResponseJson?,
    @SerialName("result")
    val result: SplResponseValueJson?,
)


@Serializable
data class SplResponseValueJson(
    @SerialName("value")
    val accounts: List<SplResponseAccountJson>,
)


@Serializable
data class SplResponseAccountJson(
    @SerialName("account")
    val account: SplAccountJson,
)

@Serializable
data class SplTokenResponseJson(
    @SerialName("error")
    val error: SplTokenErrorResponseJson
)

@Serializable
data class SplTokenErrorResponseJson(
    @SerialName("message")
    val message: String
)

@Serializable
data class SplTokenJson(
    @SerialName("decimals")
    val decimals: Int,
    @SerialName("tokenList")
    val tokenList: SplTokenListJson,
    @SerialName("mint")
    val mint: String,
    @SerialName("usdPrice")
    val usdPrice: Double?,
)

@Serializable
data class SplTokenInfo(
    @SerialName("id")
    val address: String,
    @SerialName("name")
    val name: String,
    @SerialName("symbol")
    val symbol: String,
    @SerialName("decimals")
    val decimals: Int,
    @SerialName("icon")
    val logoURI: String?,
    @SerialName("extensions")
    val extensions: SplExtensionsJson?,
    @SerialName("usdPrice")
    val usdPrice: Double?,
)


@Serializable
data class SplTokenListJson(
    @SerialName("symbol")
    val ticker: String,
    @SerialName("image")
    val logo: String?,
    @SerialName("extensions")
    val extensions: SplExtensionsJson?,
)

@Serializable
data class SplExtensionsJson(
    @SerialName("coingeckoId")
    val coingeckoId: String?,
)


@Serializable
data class SplAmountTokenAmountJson(
    @SerialName("amount")
    val amount: String,
)

@Serializable
data class SplAmountInfoJson(
    @SerialName("tokenAmount")
    val tokenAmount: SplAmountTokenAmountJson,
)

@Serializable
data class SplAmountParsedJson(
    @SerialName("info")
    val info: SplAmountInfoJson,
)

@Serializable
data class SplAmountDataJson(
    @SerialName("parsed")
    val parsed: SplAmountParsedJson,
)

@Serializable
data class SplAmountAccountJson(
    @SerialName("data")
    val data: SplAmountDataJson,
    @SerialName("owner")
    val owner: String,
)

@Serializable
data class SplAmountValueJson(
    @SerialName("account")
    val account: SplAmountAccountJson,
    @SerialName("pubkey")
    val pubKey: String,
)

@Serializable
data class SplAmountRpcResponseJson(
    @SerialName("error")
    val error: SplTokenErrorResponseJson?,
    @SerialName("result")
    val value: SplAmountRpcResponseResultJson?,
)

@Serializable
data class SplAmountRpcResponseResultJson(
    @SerialName("value")
    val value: List<SplAmountValueJson>,
)

@Serializable
data class SPLTokenRequestJson(
    @SerialName("tokens")
    val tokens: List<String>,
)

@Serializable
data class JupiterTokenResponseJson(
    @SerialName("id")
    val contractAddress: String,
    @SerialName("symbol")
    val ticker: String,
    @SerialName("decimals")
    val decimals: Int,
    @SerialName("icon")
    val logo: String?,
    @SerialName("extensions")
    val extensions: JupiterTokenCoinGeckoIdJson?,
    @SerialName("usdPrice")
    val usdPrice: Double? = null,
)

@Serializable
data class JupiterTokenCoinGeckoIdJson(
    @SerialName("coingeckoId")
    val coingeckoId: String?,
)
