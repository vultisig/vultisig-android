package com.vultisig.wallet.data.models.settings


enum class AppCurrency(
    val ticker: String,
) {
    USD(ticker = "USD"),

    AUD(ticker = "AUD"),

    EUR(ticker = "EUR"),

    CHF(ticker = "CHF"),

    GBP(ticker = "GBP"),

    JPY(ticker = "JPY"),

    CNY(ticker = "CNY"),

    CAD(ticker = "CAD"),

    SGD(ticker = "SGD"),

    SEK(ticker = "SEK");

    companion object {

        fun fromTicker(ticker: String): AppCurrency? =
            entries.find { it.ticker == ticker }

    }
}