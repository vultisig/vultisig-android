package com.vultisig.wallet.data.api.models.quotes

data class ThorChainSwapQuoteRequest(
    val address: String,
    val fromAsset: String,
    val toAsset: String,
    val amount: String,
    val interval: String,
    val referralCode: String,
    val bpsDiscount: Int,
    val streamingQuantity: Int? = null,
    /**
     * Minimum-output tolerance in basis points. When positive, the node bakes a real `LIM` into the
     * returned swap memo (`expected_amount_out × (1 − toleranceBps/10_000)`). When null or 0 the
     * `tolerance_bps` param is omitted, so the memo carries no limit, i.e. the swap accepts any
     * output (unbounded slippage). See [DEFAULT_THORCHAIN_TOLERANCE_BPS].
     */
    val toleranceBps: Int? = null,
)
