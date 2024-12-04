package com.vultisig.wallet.data.api.models.cosmos

import com.vultisig.wallet.data.utils.equalsIgnoreCase
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
        ticker: String,
    ): Boolean {
        val ticker = ticker.lowercase()
        return denom.isValidDenom(ticker)
                || denom.isValidKujiraFactoryDenom(ticker)

        //todo handle for ibc tokens like ibc/640E1C3E28FD45F611971DF891AE3DC90C825DF759DF8FAA8F33F7F72B35AD56
    }

    private fun String.isValidDenom(tickerLowerCase: String) =
        equalsIgnoreCase("u$tickerLowerCase") || equalsIgnoreCase("a$tickerLowerCase")

    private fun String.isValidKujiraFactoryDenom(ticker: String) =
        contains("factory/") &&
                (split("/")[2].equalsIgnoreCase("u$ticker") ||
                        split("/")[2].equalsIgnoreCase("a$ticker"))

}