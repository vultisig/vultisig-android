package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.TokenMetadataResolver
import java.math.BigInteger

/**
 * What a SwapKit transfer-route payload says about its provider fee, as read by a co-signer.
 *
 * The `swap_fee` group is written by the initiating device, which may run another platform's code,
 * so nothing in it is trusted past a parse: a stated fee that cannot be turned into a coin this
 * device can price renders no row rather than a guess, and never throws out of the verify screen.
 */
internal sealed interface SwapKitPayloadFee {
    /** The sender predates the field. The co-signer keeps its pre-field behaviour. */
    data object Absent : SwapKitPayloadFee

    /**
     * The sender stated a fee this device cannot render — zero, a non-integer amount, an unknown
     * chain, missing or out-of-range decimals, or a coin it cannot resolve. No row, no re-fetch.
     */
    data object NotRenderable : SwapKitPayloadFee

    /** A fee the initiator saw, in the coin it was charged in. */
    data class Stated(val coin: Coin, val amount: BigInteger) : SwapKitPayloadFee {
        val fee: TokenValue
            get() = TokenValue(amount, coin)
    }
}

/**
 * Resolves the payload's fee group against the coins the co-signer can price: the pair, the source
 * chain's native coin, then the built-in registry for the fee chain — which is where a Chainflip
 * route's Ethereum USDC fee lands on a BTC → ETH swap, a coin that is neither leg. The sender's
 * decimals are kept over the registry's so the amount is read the way it was written.
 */
internal fun swapKitPayloadFee(
    data: SwapKitSwapPayloadJson,
    srcToken: Coin,
    dstToken: Coin,
    nativeToken: Coin,
): SwapKitPayloadFee {
    if (data.swapFee.isEmpty()) return SwapKitPayloadFee.Absent
    val amount =
        data.swapFee.toBigIntegerOrNull()?.takeIf { it.signum() > 0 }
            ?: return SwapKitPayloadFee.NotRenderable
    val chain =
        data.swapFeeChain?.let(Chain::fromRawOrNull) ?: return SwapKitPayloadFee.NotRenderable
    // The scale feeds `10 ^ decimals` on every render, so the same ceiling contract metadata is
    // held to applies here: a negative value throws, an absurd one burns CPU.
    val decimals =
        data.swapFeeDecimals?.takeIf { it in 0..TokenMetadataResolver.MAX_DECIMALS }
            ?: return SwapKitPayloadFee.NotRenderable
    val tokenId = data.swapFeeTokenId.orEmpty()
    val coin =
        listOf(dstToken, srcToken, nativeToken).firstOrNull { it.isFeeCoin(chain, tokenId) }
            ?: Coins.coins[chain]?.firstOrNull { it.isFeeCoin(chain, tokenId) }
            ?: return SwapKitPayloadFee.NotRenderable
    return SwapKitPayloadFee.Stated(coin.copy(decimal = decimals), amount)
}

private fun Coin.isFeeCoin(chain: Chain, tokenId: String): Boolean =
    this.chain == chain && contractAddress.equals(tokenId, ignoreCase = true)
