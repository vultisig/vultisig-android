package com.vultisig.wallet.data.api.models.quotes

data class ThorChainSwapQuoteRequest(
    val address: String,
    val fromAsset: String,
    val toAsset: String,
    val amount: String,
    val interval: String,
    val referralCode: String,
    val bpsDiscount: Int,
)
