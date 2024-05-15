package com.vultisig.wallet.data.models


internal enum class AppCurrency(
    val ticker: String,
) {
    USD(ticker = "USD"),

    AUD(ticker = "AUD");

    companion object {

        fun fromTicker(ticker: String): AppCurrency? =
            entries.find { it.ticker == ticker }

    }
}