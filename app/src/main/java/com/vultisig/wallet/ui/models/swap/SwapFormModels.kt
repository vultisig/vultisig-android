package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenValue

/**
 * Snapshot of the swap form taken before a flip gesture, so the prior amounts and token selections
 * can be restored when the user flips back.
 */
internal data class PreFlipState(
    val srcAmount: String,
    val srcTokenId: String,
    val dstTokenId: String,
    val flippedAmount: String,
)

/**
 * Fully validated inputs required to build a swap transaction, produced by
 * [SwapInputCollector.collect] and consumed by [SwapTransactionBuilder.build].
 */
internal data class ValidatedSwapInputs(
    val vaultId: String,
    val srcToken: Coin,
    val dstToken: Coin,
    val srcAddress: String,
    val srcTokenValue: TokenValue,
    val quote: SwapQuote,
    val gasFee: TokenValue,
    val gasFeeFiatValue: FiatValue,
    val estimatedNetworkFeeTokenValue: TokenValue?,
    val estimatedNetworkFeeFiatValue: FiatValue?,
)
