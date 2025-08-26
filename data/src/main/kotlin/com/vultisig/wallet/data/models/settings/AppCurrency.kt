package com.vultisig.wallet.data.models.settings


enum class AppCurrency(
    val ticker: String,
    val fullName: String,
) {
    USD(
        ticker = "USD",
        fullName = "United States Dollar ($)"
    ),

    AUD(
        ticker = "AUD",
        fullName = "Australian Dollar (A$)"
    ),

    EUR(
        ticker = "EUR",
        fullName = "Euro (€)"
    ),

    CHF(
        ticker = "CHF",
        fullName = "Swiss Franc (CHF)" // not in screenshot, valid abbreviation kept
    ),

    GBP(
        ticker = "GBP",
        fullName = "British Pound (£)"
    ),

    JPY(
        ticker = "JPY",
        fullName = "Japanese Yen (¥)"
    ),

    CNY(
        ticker = "CNY",
        fullName = "Chinese Yuan (¥)"
    ),

    CAD(
        ticker = "CAD",
        fullName = "Canadian Dollar ($)"
    ),

    SGD(
        ticker = "SGD",
        fullName = "Singapore Dollar (S$)"
    ),

    SEK(
        ticker = "SEK",
        fullName = "Swedish Krona (kr)"
    ),

    RUB(
        ticker = "RUB",
        fullName = "Russian Ruble (₽)"
    );

    companion object {

        fun fromTicker(ticker: String): AppCurrency? =
            entries.find { it.ticker == ticker }

    }
}