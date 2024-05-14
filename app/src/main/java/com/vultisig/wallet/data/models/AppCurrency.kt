package com.vultisig.wallet.data.models


internal enum class AppCurrency(
    val ticker: String,
) {
    USD (ticker = "USD"),

    AUD(ticker = "AUD");

    companion object{
        fun String.fromTicker(): AppCurrency = when (this) {
            "USD"-> USD
            "AUD"-> AUD
            else -> error("ticker name is not valid")
        }
    }
}