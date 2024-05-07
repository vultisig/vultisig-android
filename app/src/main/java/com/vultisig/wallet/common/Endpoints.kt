package com.vultisig.wallet.common

object Endpoints {
    val VULTISIG_API_PROXY = "https://api.voltix.org"
    val VULTISIG_RELAY = "https://api.voltix.org/router"

    fun fetchCryptoPrices(coin: String, fiat: String): String =
        "${VULTISIG_API_PROXY}/coingeicko/api/v3/simple/price?ids=$coin&vs_currencies=$fiat"
}