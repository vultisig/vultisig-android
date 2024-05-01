package com.voltix.wallet.common

object Endpoints {
    val VOLTIX_API_PROXY = "https://api.voltix.org"
    val VOLTIX_RELAY = "https://api.voltix.org/router"

    fun fetchCryptoPrices(coin: String, fiat: String): String =
        "/${VOLTIX_API_PROXY}/coingeicko/api/v3/simple/price?ids=$coin&vs_currencies=$fiat"
}