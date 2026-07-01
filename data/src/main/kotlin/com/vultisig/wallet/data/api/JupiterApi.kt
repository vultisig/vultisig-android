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

        // When Jupiter floored the fee to 0 we resolve no `feeAccount`. Round-tripping the quote's
        // `platformFee` back into `/swap` without a matching `feeAccount` makes Jupiter reject the
        // fee-bearing swap, which would drop the now-preferred Jupiter route to a worse provider,
        // so
        // strip it. With no fee requested `platformFee` is already null, so this is a no-op there.
        val quoteResponseForSwap = if (feeAccount == null) body.copy(platformFee = null) else body

        val quoteSwapRequestBody = buildJsonObject {
            put("quoteResponse", json.encodeToJsonElement(quoteResponseForSwap))
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

        // Jupiter never initializes the `feeAccount` ATA, so prepend an idempotent create for it
        // the
        // first time per fee mint (the payer funds the ~0.002 SOL rent; it is skipped once the ATA
        // exists, see `needsCreate`). The co-signer signs this exact transaction from the keysign
        // payload, so byte-parity holds.
        val swapTxData =
            if (feeAccount != null && feeAccount.needsCreate)
                feeAtaService
                    .prependCreateFeeAta(quoteSwapData.data, feeAccount, fromAddress)
                    // Jupiter sized `SetComputeUnitLimit` from its `dynamicComputeUnitLimit`
                    // simulation, which ran before this create-ATA existed, so the baked limit does
                    // not budget for the ~30k CU a first-time ATA creation costs. Raise the limit
                    // by
                    // that headroom so the first swap per fee mint can't revert on an exceeded
                    // compute budget.
                    .let { withFeeAta ->
                        bumpComputeUnitLimit(withFeeAta, CREATE_ATA_COMPUTE_UNITS)
                    }
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

    /**
     * Raise the transaction's `SetComputeUnitLimit` by [extraUnits]. Returns [txData] unchanged
     * when it carries no limit instruction (nothing to rebase). Fails closed if the limit exists
     * but can't be re-encoded: returning the original would broadcast a tx whose budget doesn't
     * cover the just-prepended ATA create, guaranteeing an on-chain revert — better to drop Jupiter
     * and fall back to another provider.
     */
    private fun bumpComputeUnitLimit(txData: String, extraUnits: BigInteger): String {
        val currentLimit =
            SolanaTransaction.getComputeUnitLimit(txData)?.toBigIntegerOrNull() ?: return txData
        return SolanaTransaction.setComputeUnitLimit(txData, (currentLimit + extraUnits).toString())
            ?: error("[Jupiter] Failed to re-encode SetComputeUnitLimit after prepending fee ATA")
    }

    internal companion object {
        val MIN_FEE_PRICE_SWAP = "150000".toBigInteger()

        // CU headroom for one first-time SPL create-idempotent-ATA instruction (~25k observed),
        // added on top of Jupiter's pre-prepend compute-limit estimate so the create can't overrun.
        val CREATE_ATA_COMPUTE_UNITS = "30000".toBigInteger()
        val MAX_PRIORITY_FEE_LAMPORTS = 6000000
        val PRIORITY_LEVEL = "high"

        /** Quote slippage (0.5%) used when the user leaves slippage on Auto; matches 1inch. */
        private const val DEFAULT_SLIPPAGE_BPS = 50

        val JUPITER_URL = "https://api.vultisig.com/jup"
    }
}
