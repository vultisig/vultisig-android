package com.vultisig.wallet.data.models.settings


enum class AppCurrency(
    val ticker: String,
    val fullName: String,
) {
    USD(
        ticker = "USD",
        fullName = "US Dollar ($)"
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
        fullName = "Japanese Yen (JP¥)"
    ),

    CNY(
        ticker = "CNY",
        fullName = "Chinese Yuan (CN¥)"
    ),

    CAD(
        ticker = "CAD",
        fullName = "Canadian Dollar (CA$)"
    ),

    SGD(
        ticker = "SGD",
        fullName = "Singapore Dollar (SGD)"
    ),

    SEK(
        ticker = "SEK",
        fullName = "Swedish Krona (SEK)"
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