package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.quotes.QuoteSwapTotalDataJson
import com.vultisig.wallet.data.api.models.quotes.QuoteSwapTransactionJson
import com.vultisig.wallet.data.api.models.quotes.SwapRouteResponseJson
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import java.math.BigInteger
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import timber.log.Timber
import wallet.core.jni.SolanaTransaction

interface JupiterApi {
    suspend fun getSwapQuote(
        fromAmount: String,
        fromToken: String,
        toToken: String,
        fromAddress: String,
        slippageBps: Int?,
        affiliateBps: Int?,
    ): QuoteSwapTotalDataJson
}

internal class JupiterApiImpl
@Inject
constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    private val feeAtaService: JupiterFeeAtaService,
) : JupiterApi {
    override suspend fun getSwapQuote(
        fromAmount: String,
        fromToken: String,
        toToken: String,
        fromAddress: String,
        slippageBps: Int?,
        affiliateBps: Int?,
    ): QuoteSwapTotalDataJson {
        val requestedFeeBps = (affiliateBps ?: 0).takeIf { it > 0 }
        // ExactIn `platformFee.amount` is output-mint units. Collect in the output mint too
        // (including wSOL) so the displayed amount and the fee ATA share a denomination.
        val feeMint = toToken
        val slippage = slippageBps ?: DEFAULT_SLIPPAGE_BPS

        // Probe the fee ATA before quoting. A missing account must never take a fee-bearing
        // /quote — outAmount would be net of a fee we cannot collect, so the UI would lie.
        val resolvedFeeAccount = requestedFeeBps?.let { resolveFeeAccountOrNull(feeMint) }
        val body =
            fetchRouteQuote(
                fromToken = fromToken,
                toToken = toToken,
                fromAmount = fromAmount,
                slippageBps = slippage,
                platformFeeBps = requestedFeeBps.takeIf { resolvedFeeAccount != null },
            )
        val feeAccount = resolvedFeeAccount.takeIf { quotedFeeAmount(body) > BigInteger.ZERO }

        val outAmount = body.outAmount
        val routePlan = body.routePlan
        val quoteResponseForSwap = if (feeAccount == null) body.copy(platformFee = null) else body

        val quoteSwapRequestBody = buildJsonObject {
            put("quoteResponse", json.encodeToJsonElement(quoteResponseForSwap))
            put("userPublicKey", fromAddress)
            put("dynamicComputeUnitLimit", true)
            if (feeAccount != null) put("feeAccount", feeAccount)
            put(
                "prioritizationFeeLamports",
                buildJsonObject {
                    put(
                        "priorityLevelWithMaxLamports",
                        buildJsonObject {
                            put("maxLamports", MAX_PRIORITY_FEE_LAMPORTS)
                            put("priorityLevel", PRIORITY_LEVEL)
                        },
                    )
                },
            )
        }
        val swapResponse =
            httpClient.post("$JUPITER_URL/swap/v1/swap") { setBody(quoteSwapRequestBody) }
        if (swapResponse.status == HttpStatusCode.TooManyRequests) {
            throw SwapException.RateLimitExceeded("[Jupiter] Too many requests")
        }
        val quoteSwapData = swapResponse.bodyOrThrow<QuoteSwapTransactionJson>()

        // Jupiter pre-simulates the swap tx and reports a non-null `simulationError` when it will
        // fail on-chain (slippage / min-out / liquidity at execution). Don't build or offer a tx
        // Jupiter already knows is doomed — drop the Jupiter route so the picker re-quotes or falls
        // back to another provider, instead of taking the user through the full keysign only for
        // the broadcast to be rejected at preflight.
        quoteSwapData.simulationError?.let { simError ->
            throw SwapException.SwapRouteNotAvailable(
                "[Jupiter] swap simulation failed: ${simError.error ?: simError.errorCode ?: "unknown"}"
            )
        }

        val swapTxData = quoteSwapData.data

        val feePrice = (SolanaTransaction.getComputeUnitPrice(swapTxData) ?: "0").toBigInteger()

        val updatedSwapTx =
            if (feePrice < MIN_FEE_PRICE_SWAP) {
                SolanaTransaction.setComputeUnitPrice(swapTxData, MIN_FEE_PRICE_SWAP.toString())
            } else {
                swapTxData
            }

        val platformFeeAmount = body.platformFee?.amount.takeIf { feeAccount != null }
        return QuoteSwapTotalDataJson(
            swapTransaction = quoteSwapData.copy(data = updatedSwapTx),
            dstAmount = outAmount,
            routePlan = routePlan,
            platformFeeAmount = platformFeeAmount,
            platformFeeMint = feeMint.takeIf { platformFeeAmount != null },
        )
    }

    private suspend fun fetchRouteQuote(
        fromToken: String,
        toToken: String,
        fromAmount: String,
        slippageBps: Int,
        platformFeeBps: Int?,
    ): SwapRouteResponseJson {
        val response =
            httpClient.get("$JUPITER_URL/swap/v1/quote") {
                parameter("inputMint", fromToken)
                parameter("outputMint", toToken)
                parameter("amount", fromAmount)
                parameter("slippageBps", slippageBps)
                if (platformFeeBps != null && platformFeeBps > 0) {
                    parameter("platformFeeBps", platformFeeBps)
                }
            }
        if (response.status == HttpStatusCode.TooManyRequests) {
            throw SwapException.RateLimitExceeded("[Jupiter] Too many requests")
        }
        return response.bodyOrThrow()
    }

    private suspend fun resolveFeeAccountOrNull(feeMint: String): String? =
        try {
            feeAtaService.resolveFeeAccount(feeMint)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Exception) {
            Timber.w(
                t,
                "Jupiter fee ATA probe failed for mint %s; quoting without affiliate fee",
                feeMint,
            )
            null
        }

    private fun quotedFeeAmount(body: SwapRouteResponseJson): BigInteger =
        body.platformFee?.amount?.toBigIntegerOrNull() ?: BigInteger.ZERO

    internal companion object {
        val MIN_FEE_PRICE_SWAP = "150000".toBigInteger()

        val MAX_PRIORITY_FEE_LAMPORTS = 6000000
        val PRIORITY_LEVEL = "high"

        /** Quote slippage (0.5%) used when the user leaves slippage on Auto; matches 1inch. */
        private const val DEFAULT_SLIPPAGE_BPS = 50

        val JUPITER_URL = "https://api.vultisig.com/jup"
    }
}
