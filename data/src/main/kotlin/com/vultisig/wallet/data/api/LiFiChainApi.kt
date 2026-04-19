package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.quotes.LiFiSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.LiFiSwapQuoteError
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.oneInchChainId
import com.vultisig.wallet.data.utils.LiFiSwapQuoteResponseSerializer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import java.math.BigInteger
import javax.inject.Inject
import kotlin.math.round
import kotlinx.serialization.json.Json

interface LiFiChainApi {
    suspend fun getSwapQuote(
        fromChain: String,
        toChain: String,
        fromToken: String,
        toToken: String,
        fromAmount: String,
        fromAddress: String,
        toAddress: String,
        bpsDiscount: Int,
    ): LiFiSwapQuoteDeserialized

    companion object {
        const val INTEGRATOR_FEE_BPS = 50
        const val BPS_DENOMINATOR_INT = 10_000
        private val BPS_DENOMINATOR = BigInteger.valueOf(BPS_DENOMINATOR_INT.toLong())
        const val INTEGRATOR_FEE_RATE = INTEGRATOR_FEE_BPS / BPS_DENOMINATOR_INT.toDouble()
        const val INTEGRATOR_ACCOUNT = "vultisig-android"

        /**
         * Computes the LI.FI integrator fee that will be deducted from a swap's destination amount,
         * in the destination token's raw wei. Mirrors iOS' `SwapQuote.inboundFeeDecimal(toCoin:)`.
         *
         * The raw "LIFI Fixed Fee" entry returned by LI.FI's `feeCosts` carries no chain id, so its
         * denomination cannot be inferred reliably for cross-chain swaps (#3300). Deriving the fee
         * from `dstAmount` sidesteps that ambiguity.
         */
        fun integratorFeeAmount(dstAmount: BigInteger, bpsDiscount: Int = 0): BigInteger {
            val effectiveBps = maxOf(0, INTEGRATOR_FEE_BPS - bpsDiscount)
            return dstAmount * effectiveBps.toBigInteger() / BPS_DENOMINATOR
        }
    }
}

internal class LiFiChainApiImpl
@Inject
constructor(
    private val httpClient: HttpClient,
    private val liFiSwapQuoteResponseSerializer: LiFiSwapQuoteResponseSerializer,
    private val json: Json,
) : LiFiChainApi {
    override suspend fun getSwapQuote(
        fromChain: String,
        toChain: String,
        fromToken: String,
        toToken: String,
        fromAmount: String,
        fromAddress: String,
        toAddress: String,
        bpsDiscount: Int,
    ): LiFiSwapQuoteDeserialized {
        val isSolanaChainInvolved =
            fromChain.toLong() == Chain.Solana.oneInchChainId() ||
                toChain.toLong() == Chain.Solana.oneInchChainId()

        val bpsDiscountFee = round(bpsDiscount.toDouble()) / LiFiChainApi.BPS_DENOMINATOR_INT
        val updatedFeeIntegrator =
            (round(
                    maxOf(LiFiChainApi.INTEGRATOR_FEE_RATE - bpsDiscountFee, 0.0) *
                        LiFiChainApi.BPS_DENOMINATOR_INT
                ) / LiFiChainApi.BPS_DENOMINATOR_INT)
                .toString()

        val response =
            httpClient.get("https://li.quest/v1/quote") {
                parameter("fromChain", fromChain)
                parameter("toChain", toChain)
                parameter("fromToken", fromToken)
                parameter("toToken", toToken)
                parameter("fromAmount", fromAmount)
                parameter("fromAddress", fromAddress)
                parameter("toAddress", toAddress)
                if (!isSolanaChainInvolved) {
                    parameter("integrator", LiFiChainApi.INTEGRATOR_ACCOUNT)
                    parameter("fee", updatedFeeIntegrator)
                }
            }
        return try {
            json.decodeFromString(liFiSwapQuoteResponseSerializer, response.body<String>())
        } catch (e: Exception) {
            LiFiSwapQuoteDeserialized.Error(
                LiFiSwapQuoteError(HttpStatusCode.fromValue(response.status.value).description)
            )
        }
    }
}
