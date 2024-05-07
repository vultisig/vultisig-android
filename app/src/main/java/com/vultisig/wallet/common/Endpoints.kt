package com.vultisig.wallet.common

object Endpoints {
    val VULTISIG_API_PROXY = "https://api.voltix.org"
    val VULTISIG_RELAY = "https://api.voltix.org/router"

    fun fetchCryptoPrices(coin: String, fiat: String): String =
        "${VULTISIG_API_PROXY}/coingeicko/api/v3/simple/price?ids=$coin&vs_currencies=$fiat"

    fun fetchAccountBalanceTHORChain9r(address: String): String =
        "https://thornode.ninerealms.com/cosmos/bank/v1beta1/balances/$address"

    fun fetchAccountNumberTHORChain9r(address: String): String =
        "https://thornode.ninerealms.com/auth/accounts/$address"

    fun fetchSwaoQuoteThorchain9r(
        address: String,
        fromAsset: String,
        toAsset: String,
        amount: String,
        interval: String,
    ): String =
        "https://thornode.ninerealms.com/thorchain/quote/swap?from_asset=$fromAsset&to_asset=$toAsset&amount=$amount&destination=$address&streaming_interval=$interval"

}