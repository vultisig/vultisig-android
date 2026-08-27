package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.repositories.swap.convertToTokenValue
import com.vultisig.wallet.data.swap.ThorchainMemoLimit
import com.vultisig.wallet.data.swap.limit.MemoAssetMatch
import com.vultisig.wallet.data.swap.limit.compareToMemoAsset

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
 *
 * Null too when the memo's own target asset is confidently not [dstToken]. The memo and the
 * payload's destination coin reach a cosigner as two independently decoded halves of the same
 * request, and the LIM is denominated in the asset the memo names: pairing them without checking
 * would put a floor read in one asset under another asset's ticker.
 */
internal fun signedMinimumOutput(payload: SwapPayload, memo: String?, dstToken: Coin): TokenValue? {
    if (payload !is SwapPayload.ThorChain && payload !is SwapPayload.MayaChain) return null
    if (memo == null) return null
    val limit = ThorchainMemoLimit.assertedLimit(memo) ?: return null
    val targetAsset = ThorchainMemoLimit.targetAsset(memo)
    if (
        targetAsset != null && dstToken.compareToMemoAsset(targetAsset) == MemoAssetMatch.DIFFERENT
    ) {
        return null
    }
    // The LIM is in the protocol's own fixed point — THORChain's 1e8 whatever the destination
    // chain, Maya's 1e10 for CACAO — which is exactly what convertToTokenValue rescales from.
    return dstToken.convertToTokenValue(limit.toString())
}
