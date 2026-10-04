package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuote
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.swap.ThorchainMemoLimit
import dagger.MapKey
import java.math.BigInteger
import timber.log.Timber

/**
 * Single, polymorphic request used by every [SwapQuoteSource]. Each provider reads only what it
 * needs.
 */
data class SwapQuoteRequest(
    val srcToken: Coin,
    val dstToken: Coin,
    val tokenValue: TokenValue,
    val srcAddress: String = "",
    val dstAddress: String = "",
    val isAffiliate: Boolean = false,
    val bpsDiscount: Int = 0,
    val referralCode: String = "",
    val affiliateBps: Int = 0,
    /**
     * User-chosen slippage tolerance in basis points (100 = 1%), or null for "Auto" — in which case
     * each provider keeps its own default ([DEFAULT_THORCHAIN_TOLERANCE_BPS] for THORChain/Maya,
     * 0.5% for 1inch and Jupiter, 1% for Kyber, [LiFiSlippage]'s stable/volatile tiers for LI.FI,
     * and SwapKit's own server default). Honoured by every provider; each converts it to its native
     * unit (bps, percent, or fraction), and SwapKit omits it on Auto so NEAR Intents / Chainflip
     * can negotiate their own per-route tolerance.
     */
    val slippageBps: Int? = null,
)

/**
 * Two-shape return from a [SwapQuoteSource]: native protocols return a fully-realised [SwapQuote];
 * EVM aggregators return raw [EVMSwapQuoteJson] that the caller wraps with chain-specific gas math.
 */
sealed class SwapQuoteResult {
    data class Native(val quote: SwapQuote) : SwapQuoteResult()

    /**
     * EVM-shaped envelope. [subProvider] disambiguates SwapKit's downstream protocol (Chainflip /
     * NEAR / Garden); `null` for direct aggregators (1inch, Kyber, LiFi, Jupiter). [swapId] is the
     * SwapKit `/v3/swap` swap id, carried through so the resulting transaction can be gated on the
     * destination-leg `/track` settlement; `null` for direct aggregators.
     */
    data class Evm(
        val data: EVMSwapQuoteJson,
        val subProvider: String? = null,
        val swapId: String? = null,
        // Signed fractional price impact (e.g. 0.0133 == +1.33% cost) from SwapKit's route
        // (`meta.priceImpact`, else `totalSlippageBps`), threaded out-of-band since
        // EVMSwapQuoteJson
        // has no slot for it. Null for direct aggregators (1inch/Kyber/LiFi/Jupiter), which don't
        // report price impact.
        val priceImpact: java.math.BigDecimal? = null,
    ) : SwapQuoteResult()

    // A future SwapKit non-EVM route (BTC / TON / ADA / TRON / SUI / ZEC) rides Native, since
    // SwapQuote.SwapKit is itself a SwapQuote — no dedicated result variant is needed.

    fun expectNative(provider: SwapProvider): SwapQuote =
        when (this) {
            is Native -> quote
            is Evm ->
                throw SwapException.UnkownSwapError("Expected Native quote for $provider, got Evm")
        }

    fun expectEvm(provider: SwapProvider): EVMSwapQuoteJson =
        when (this) {
            is Evm -> data
            is Native ->
                throw SwapException.UnkownSwapError("Expected Evm quote for $provider, got Native")
        }
}

/**
 * "Auto" slippage default for THORChain/Maya quotes. 0 → `tolerance_bps` is omitted, so the node
 * sets no `LIM` and never rejects the quote. A nonzero default blocks legitimate swaps: the node
 * gates `tolerance_bps` on the single-swap (full-size) emit rather than the streamed output, so any
 * swap whose price impact exceeds it is refused (`emit asset X less than price limit Y`). Slippage
 * stays mitigated by the rapid→streaming upgrade and the node's own auto-streaming. A user-set
 * slippage overrides this and flows straight through. Mirrors iOS
 * `SwapService.defaultThorchainToleranceBps` (vultisig-ios#4640).
 */
internal const val DEFAULT_THORCHAIN_TOLERANCE_BPS = 0

/**
 * Floor an "Auto" THORChain/Maya swap is signed with, in basis points below the quoted emit. The
 * node is never asked for it (see [DEFAULT_THORCHAIN_TOLERANCE_BPS]); the quote source writes it
 * into the memo itself with [withAutoSlippageLimit]. Matches the SDK / extension default.
 */
internal const val AUTO_SLIPPAGE_LIMIT_BPS = 100

/** OP_RETURN payload limit for a UTXO-chain swap memo. */
private const val UTXO_MEMO_MAX_BYTES = 80

/**
 * This quote with a minimum output written into its memo, for an "Auto" slippage swap that the
 * node returned without one. Without it the signed memo accepts any output, however far the price
 * moves before the swap executes.
 *
 * The node checks `LIM` against the swap's emit — before the outbound fee and the affiliate cut —
 * so the floor is taken off `expected_amount_out` with both added back. Deriving it from the
 * quote's own expected output, rather than asking the node for `tolerance_bps`, keeps a
 * high-impact streaming quote from being refused.
 *
 * Returns the quote unchanged when its memo can't carry the limit: not a market swap memo, a
 * floor already set, or a UTXO memo the extra digits would push past the OP_RETURN limit.
 */
internal fun THORChainSwapQuote.withAutoSlippageLimit(srcChain: Chain): THORChainSwapQuote {
    val memo = memo ?: return this
    val emit =
        listOf(expectedAmountOut, fees.outbound, fees.affiliate)
            .map { it.toBigIntegerOrNull() ?: return this }
            .fold(BigInteger.ZERO, BigInteger::add)
    val limit =
        emit.multiply(BigInteger.valueOf(10_000L - AUTO_SLIPPAGE_LIMIT_BPS))
            .divide(BigInteger.valueOf(10_000L))
    if (limit.signum() <= 0) return this
    val limited = ThorchainMemoLimit.withLimit(memo, limit) ?: return this
    if (
        srcChain.standard == TokenStandard.UTXO &&
            limited.toByteArray(Charsets.UTF_8).size > UTXO_MEMO_MAX_BYTES
    ) {
        Timber.w("Auto-slippage limit would push the %s swap memo past OP_RETURN", srcChain)
        return this
    }
    return copy(memo = limited)
}

/** Common contract for every per-provider quote source. */
interface SwapQuoteSource {
    suspend fun fetch(request: SwapQuoteRequest): SwapQuoteResult
}

/** Hilt map key used by the @IntoMap registry of [SwapQuoteSource]s keyed by [SwapProvider]. */
@MapKey annotation class SwapProviderKey(val value: SwapProvider)
