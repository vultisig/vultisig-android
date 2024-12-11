package com.vultisig.wallet.data.api.models.cosmos

import com.vultisig.wallet.data.models.Coin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CosmosBalance(
    @SerialName("denom")
    val denom: String,
    @SerialName("amount")
    val amount: String,
) {
    fun hasValidDenom(
        coin: Coin,
    ) = denom.isValidDenom(coin.ticker)
            || denom.isValidKujiraFactoryDenom(coin.ticker)
            || denom.equals(
        coin.contractAddress,
        ignoreCase = true
    )
    
    private fun String.isValidDenom(ticker: String) =
        equals(
            ("u$ticker"),
            ignoreCase = true
        ) || equals(
            ("a$ticker"),
            ignoreCase = true
        )

    private fun String.isValidKujiraFactoryDenom(ticker: String): Boolean {
        val value = removePrefix("factory/").substringAfter('/')
        return value.equals(
            "u$ticker",
            ignoreCase = true
        ) || value.equals(
            "a$ticker",
            ignoreCase = true
        )
    }
}