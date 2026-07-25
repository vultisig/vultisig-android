package com.vultisig.wallet.data.api.models.quotes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Minimal view of a THORChain `/quote/swap` response for the limit-swap reference price. Only
 * `expected_amount_out` (1e8 fixed point) is read; every other quote field is irrelevant to seeding
 * the form's price and is ignored.
 */
@Serializable
data class LimitSwapReferenceQuoteJson(
    @SerialName("expected_amount_out") val expectedAmountOut: String? = null,
    @SerialName("error") val error: String? = null,
)
