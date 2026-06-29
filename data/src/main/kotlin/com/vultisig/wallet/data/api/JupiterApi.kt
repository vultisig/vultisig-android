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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
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
        // Ask Jupiter to take the VULT-scaled affiliate fee (in the output mint) when a positive
        // bps
        // was requested. Whether we actually provision a fee account is decided below from the
        // quote's real fee amount, not just the request.
        val requestsPlatformFee = (affiliateBps ?: 0) > 0

        val quoteResponse =
            httpClient.get("$JUPITER_URL/swap/v1/quote") {
                parameter("inputMint", fromToken)
                parameter("outputMint", toToken)
                parameter("amount", fromAmount)
                parameter("slippageBps", slippageBps ?: DEFAULT_SLIPPAGE_BPS)
                if (requestsPlatformFee) parameter("platformFeeBps", affiliateBps)
            }
        if (quoteResponse.status == HttpStatusCode.TooManyRequests) {
            throw SwapException.RateLimitExceeded("[Jupiter] Too many requests")
        }
        val body = quoteResponse.bodyOrThrow<SwapRouteResponseJson>()
        val outAmount = body.outAmount
        val routePlan = body.routePlan

        // Gate the fee-account flow on the actually-quoted fee, not just the requested bps: Jupiter
        // can floor `platformFee.amount` to 0 (tiny amounts / fee-ineligible route) even when a fee
        // was asked for. Deriving a fee account and paying ATA rent for a zero fee would be wrong.
        val quotedFeeAmount = body.platformFee?.amount?.toBigIntegerOrNull() ?: BigInteger.ZERO
        val feeAccount =
            if (requestsPlatformFee && quotedFeeAmount > BigInteger.ZERO)
                feeAtaService.resolveFeeAccount(toToken)
            else null

        val quoteSwapRequestBody = buildJsonObject {
            put("quoteResponse", json.encodeToJsonElement(body))
            put("userPublicKey", fromAddress)
            put("dynamicComputeUnitLimit", true)
            if (feeAccount != null) put("feeAccount", feeAccount.feeAccount)
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

        // Jupiter never initializes the `feeAccount` ATA, so prepend an idempotent create for it.
        // The user pays the ~0.002 SOL rent the first time per fee mint; it is a no-op thereafter.
        // The co-signer signs this exact transaction from the keysign payload, so byte-parity
        // holds. Note: the displayed Solana network-fee estimate does not yet include this one-time
        // rent (the SDK adds `ataRentLamports`); that is a deferred fee-display refinement.
        val swapTxData =
            if (feeAccount != null)
                feeAtaService.prependCreateFeeAta(quoteSwapData.data, feeAccount, fromAddress)
            else quoteSwapData.data

        val feePrice = (SolanaTransaction.getComputeUnitPrice(swapTxData) ?: "0").toBigInteger()

        val updatedSwapTx =
            if (feePrice < MIN_FEE_PRICE_SWAP) {
                SolanaTransaction.setComputeUnitPrice(swapTxData, MIN_FEE_PRICE_SWAP.toString())
            } else {
                swapTxData
            }

        return QuoteSwapTotalDataJson(
            swapTransaction = quoteSwapData.copy(data = updatedSwapTx),
            dstAmount = outAmount,
            routePlan = routePlan,
        )
    }

    internal companion object {
        val MIN_FEE_PRICE_SWAP = "150000".toBigInteger()
        val MAX_PRIORITY_FEE_LAMPORTS = 6000000
        val PRIORITY_LEVEL = "high"

        /** Quote slippage (0.5%) used when the user leaves slippage on Auto; matches 1inch. */
        private const val DEFAULT_SLIPPAGE_BPS = 50

        val JUPITER_URL = "https://api.vultisig.com/jup"
    }
}
