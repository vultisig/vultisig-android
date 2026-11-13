package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.FyndClientFeeRequestJson
import com.vultisig.wallet.data.api.models.quotes.FyndEncodingOptionsJson
import com.vultisig.wallet.data.api.models.quotes.FyndOrderJson
import com.vultisig.wallet.data.api.models.quotes.FyndQuoteOptionsJson
import com.vultisig.wallet.data.api.models.quotes.FyndQuoteRequestJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.api.swapAggregators.EvmSwapTxGuard
import com.vultisig.wallet.data.api.swapAggregators.FyndApi
import com.vultisig.wallet.data.api.swapAggregators.FyndConfig
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.repositories.FeatureFlagRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Whether Fynd may be quoted: the remote `fynd-swap` flag (default off), cached so quoting doesn't
 * refetch it per keystroke, and a fee receiver whenever quotes go through the proxy. While Fynd is
 * unusable it is left out of the candidates entirely; a failing candidate would put its "no route"
 * above a sibling's transient error.
 */
@Singleton
internal class FyndFeatureGate
@Inject
constructor(private val featureFlags: FeatureFlagRepository, private val config: FyndConfig) {
    private val mutex = Mutex()
    @Volatile private var cached: Boolean? = null
    private var fetchedAtMs = 0L

    /** Last known answer, without I/O; false until [refresh] has run once. */
    fun isUsable(): Boolean =
        (config.forceEnabled || cached == true) && (config.isDirect || config.feeReceiver != null)

    suspend fun refresh(): Boolean {
        if (!config.forceEnabled) {
            mutex.withLock {
                val now = System.currentTimeMillis()
                if (cached == null || now - fetchedAtMs >= CACHE_MS) {
                    cached = featureFlags.getFeatureFlags().isFyndSwapEnabled
                    fetchedAtMs = now
                }
            }
        }
        return isUsable()
    }

    private companion object {
        const val CACHE_MS = 10 * 60 * 1000L
    }
}

internal class FyndQuoteSource
@Inject
constructor(
    private val api: FyndApi,
    private val config: FyndConfig,
    private val gate: FyndFeatureGate,
    private val clock: Clock,
) : SwapQuoteSource {

    override suspend fun fetch(request: SwapQuoteRequest): SwapQuoteResult =
        swapApiCall("Fynd") {
            if (!gate.refresh()) throw SwapException.SwapRouteNotAvailable("Fynd is disabled")
            val src = request.srcToken
            val dst = request.dstToken
            if (src.chain != dst.chain) {
                throw SwapException.SwapIsNotSupported("Fynd does not support cross-chain swaps")
            }
            val router =
                EvmSwapTxGuard.fyndRouter(src.chain)
                    ?: throw SwapException.SwapIsNotSupported("Fynd does not support ${src.chain}")

            val fee = feeFor(request.affiliateBps)
            val slippageBps = request.slippageBps ?: DEFAULT_SLIPPAGE_BPS
            val amountIn = request.tokenValue.value
            val response =
                api.quote(
                    src.chain,
                    FyndQuoteRequestJson(
                        orders =
                            listOf(
                                FyndOrderJson(
                                    tokenIn = src.fyndToken(),
                                    tokenOut = dst.fyndToken(),
                                    amount = amountIn.toString(),
                                    sender = src.address,
                                    receiver = src.address,
                                )
                            ),
                        options =
                            FyndQuoteOptionsJson(
                                timeoutMs = TIMEOUT_MS,
                                minResponses = 1,
                                encodingOptions =
                                    FyndEncodingOptionsJson(
                                        slippage = fyndSlippage(slippageBps),
                                        clientFeeParams =
                                            fee?.let { FyndClientFeeRequestJson(it.grossBps, it.receiver) },
                                    ),
                            ),
                    ),
                )
            val order =
                response.orders.singleOrNull()
                    ?: throw SwapException.UnkownSwapError("Fynd returned ${response.orders.size} orders")
            val quote =
                FyndQuoteValidator.validate(
                    order,
                    FyndQuoteExpectation(
                        router = router,
                        amountIn = amountIn,
                        tokenIn = src.fyndToken(),
                        tokenOut = dst.fyndToken(),
                        receiver = src.address,
                        sellsNative = src.isNativeToken,
                        slippageBps = slippageBps,
                        fee = fee,
                        nowEpochSeconds = clock.now().epochSeconds,
                    ),
                )
            SwapQuoteResult.Evm(quote.toEvmQuote(src, dst))
        }

    /**
     * The fee to request, or null for none. A debug build quoting Fynd directly has no signer, and
     * an unsigned fee reverts on-chain, so it quotes fee-free. Through the proxy a fee is required:
     * with no configured receiver the quote is refused rather than silently dropped to zero.
     */
    private fun feeFor(netBps: Int): FyndExpectedFee? {
        if (netBps <= 0 || config.isDirect) return null
        val receiver =
            config.feeReceiver
                ?: throw SwapException.SwapRouteNotAvailable("Fynd fee receiver not configured")
        return FyndExpectedFee(fyndGrossClientFeeBps(netBps), receiver)
    }

    private fun ValidatedFyndQuote.toEvmQuote(src: Coin, dst: Coin): EVMSwapQuoteJson =
        EVMSwapQuoteJson(
            dstAmount = amountAfterFees.toString(),
            tx =
                OneInchSwapTxJson(
                    from = src.address,
                    to = to,
                    // Fynd's estimate covers the route only; double it plus call overhead, like
                    // Kyber's ×2 on these chains, so the signed limit doesn't run out.
                    gas =
                        if (gasEstimate > 0) gasEstimate * 2 + CALL_OVERHEAD_GAS
                        else EvmHelper.DEFAULT_ETH_SWAP_GAS_UNIT,
                    data = data,
                    value = value.toString(),
                    gasPrice = gasPrice,
                    swapFee = (routerFee + clientFee).toString(),
                    swapFeeTokenContract = dst.contractAddress.ifEmpty { NATIVE_TOKEN_SENTINEL },
                ),
        )

    private fun Coin.fyndToken(): String = if (isNativeToken) ZERO_ADDRESS else contractAddress

    private companion object {
        const val DEFAULT_SLIPPAGE_BPS = 50
        const val TIMEOUT_MS = 5_000
        const val CALL_OVERHEAD_GAS = 60_000L
        const val ZERO_ADDRESS = "0x0000000000000000000000000000000000000000"
        const val NATIVE_TOKEN_SENTINEL = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
    }
}
