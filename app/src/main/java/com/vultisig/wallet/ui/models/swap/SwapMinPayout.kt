package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.repositories.swap.convertToTokenValue
import com.vultisig.wallet.data.swap.ThorchainMemoLimit

/**
 * The minimum destination amount the transaction about to be signed actually enforces, or null when
 * it enforces none (#5711).
 *
 * Only the native protocols assert a floor this app can see: THORChain / MayaChain bake it into the
 * memo as `LIM`, and that memo is signed verbatim — see [ThorchainMemoLimit]. Every aggregator
 * route signs opaque calldata or a provider-built transaction instead, so whatever floor their
 * router enforces is not exposed to us and this returns null.
 *
 * Null is the answer for an "Auto" slippage swap too, which is the common case: the app then sends
 * no `tolerance_bps` and the node returns a memo with an empty limit, so the swap accepts any
 * output. The screens must render no minimum at all there — the expected output is not a floor, and
 * labelling it one promises a guarantee the signature does not back.
 */
internal fun signedMinimumOutput(payload: SwapPayload, memo: String?, dstToken: Coin): TokenValue? {
    if (payload !is SwapPayload.ThorChain && payload !is SwapPayload.MayaChain) return null
    val limit = memo?.let(ThorchainMemoLimit::assertedLimit) ?: return null
    // The memo's LIM is in THORChain's 1e8 fixed point regardless of the destination chain.
    return dstToken.convertToTokenValue(limit.toString())
}
