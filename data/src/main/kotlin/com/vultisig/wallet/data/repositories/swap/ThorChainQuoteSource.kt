package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuote
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.ThorChainSwapQuoteRequest
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.SwapQuote.Companion.expiredAfter
import com.vultisig.wallet.data.models.swapAssetComparisonName
import com.vultisig.wallet.data.models.swapAssetName
import java.math.BigInteger
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.datetime.Clock
import timber.log.Timber

internal class ThorChainQuoteSource @Inject constructor(private val thorChainApi: ThorChainApi) :
    SwapQuoteSource {

    override suspend fun fetch(request: SwapQuoteRequest): SwapQuoteResult {
        val srcToken = request.srcToken
        val dstToken = request.dstToken
        if (srcToken.swapAssetComparisonName() == dstToken.swapAssetComparisonName()) {
            throw SwapException.SameAssets("Source and Target cannot be the same")
        }
        require(request.dstAddress.isNotBlank()) {
            "dstAddress is required for THORChain swap quotes"
        }

        val rapidRequest =
            ThorChainSwapQuoteRequest(
                address = request.dstAddress,
                fromAsset = srcToken.swapAssetName(),
                toAsset = dstToken.swapAssetName(),
                amount = srcToken.toThorTokenValue(request.tokenValue).toString(),
                interval = Interval.Rapid.wireValue,
                referralCode = request.referralCode,
                bpsDiscount = request.bpsDiscount,
            )

        val finalData = fetchWithStreamingFallback(rapidRequest)

        return SwapQuoteResult.Native(
            SwapQuote.ThorChain(
                expectedDstValue = dstToken.convertToTokenValue(finalData.expectedAmountOut),
                fees = dstToken.convertToTokenValue(finalData.fees.total),
                recommendedMinTokenValue =
                    srcToken.convertToTokenValue(finalData.recommendedMinAmountIn),
                data = finalData,
                expiredAt = Clock.System.now() + expiredAfter,
            )
        )
    }

    /**
     * Fetches a THORChain quote, falling back to a streaming swap when rapid is unavailable or its
     * slippage is unacceptable. When both rapid and streaming succeed, picks whichever yields more
     * output.
     */
    private suspend fun fetchWithStreamingFallback(
        rapidRequest: ThorChainSwapQuoteRequest
    ): THORChainSwapQuote {
        val rapid = fetchRapid(rapidRequest)
        if (rapid is RapidQuote.Success && !rapid.needsStreaming()) {
            return rapid.data
        }

        when (rapid) {
            is RapidQuote.Failed -> Timber.w("Rapid quote failed, trying streaming fallback")
            is RapidQuote.Success ->
                Timber.d(
                    "Slippage %d bps is above threshold, fetching streaming quote",
                    rapid.slippageBps,
                )
        }

        val streamingHint = (rapid as? RapidQuote.Success)?.data?.maxStreamingQuantity
        val streaming = fetchStreaming(rapidRequest, streamingHint)

        return pickBest(
            rapid = (rapid as? RapidQuote.Success)?.data,
            streaming = streaming,
            rapidError = (rapid as? RapidQuote.Failed)?.error,
        )
    }

    private suspend fun fetchRapid(request: ThorChainSwapQuoteRequest): RapidQuote =
        try {
            when (val response = thorChainApi.getSwapQuotes(request)) {
                is THORChainSwapQuoteDeserialized.Error ->
                    RapidQuote.Failed(SwapException.handleSwapException(response.error.message))

                is THORChainSwapQuoteDeserialized.Result -> {
                    val innerError = response.data.error
                    if (innerError != null) {
                        RapidQuote.Failed(SwapException.handleSwapException(innerError))
                    } else {
                        RapidQuote.Success(response.data)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RapidQuote.Failed(SwapException.handleSwapException(e.message ?: "Unknown error"))
        }

    private suspend fun fetchStreaming(
        rapidRequest: ThorChainSwapQuoteRequest,
        streamingQuantity: Int?,
    ): THORChainSwapQuote? =
        try {
            val response =
                thorChainApi.getSwapQuotes(
                    rapidRequest.copy(
                        interval = Interval.Streaming.wireValue,
                        streamingQuantity = streamingQuantity,
                    )
                )
            when (response) {
                is THORChainSwapQuoteDeserialized.Error -> null
                is THORChainSwapQuoteDeserialized.Result ->
                    response.data.takeIf { it.error == null }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Streaming quote fetch failed")
            null
        }

    private fun pickBest(
        rapid: THORChainSwapQuote?,
        streaming: THORChainSwapQuote?,
        rapidError: SwapException?,
    ): THORChainSwapQuote {
        if (streaming == null && rapid == null) {
            throw rapidError ?: error("rapid quote failed and streaming returned null")
        }
        if (streaming == null) return checkNotNull(rapid)
        if (rapid == null) return streaming
        val streamingOut = streaming.expectedAmountOut.toBigInteger()
        return if (streamingOut > rapid.expectedAmountOut.toBigInteger()) streaming else rapid
    }

    private sealed interface RapidQuote {
        data class Success(val data: THORChainSwapQuote) : RapidQuote {
            val slippageBps: Int = computeSlippageBps(data)

            fun needsStreaming(): Boolean =
                slippageBps > STREAMING_SLIPPAGE_THRESHOLD_BPS && data.maxStreamingQuantity > 0
        }

        data class Failed(val error: SwapException) : RapidQuote
    }

    private enum class Interval(val wireValue: String) {
        Rapid("0"),
        Streaming("1"),
    }

    private companion object {
        // 3% slippage cutoff above which we prefer a streaming swap over a rapid one.
        const val STREAMING_SLIPPAGE_THRESHOLD_BPS = 300

        private fun computeSlippageBps(quote: THORChainSwapQuote): Int {
            val feesTotal = quote.fees.total.toBigInteger()
            val expectedOut = quote.expectedAmountOut.toBigInteger()
            val grossOut = feesTotal + expectedOut
            return if (grossOut > BigInteger.ZERO) {
                feesTotal.multiply(BPS_DIVISOR).divide(grossOut).toInt()
            } else 0
        }
    }
}
