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
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.round

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
    ) : LiFiSwapQuoteDeserialized
}

internal class LiFiChainApiImpl @Inject constructor(
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
        val isSolanaChainInvolved = fromChain.toLong() == Chain.Solana.oneInchChainId() ||
                toChain.toLong() == Chain.Solana.oneInchChainId()

        val bpsDiscountFee =
            round(bpsDiscount.toDouble()) / 10000.0
        val updatedFeeIntegrator =
            (round(maxOf(INTEGRATOR_FEE.toDouble() - bpsDiscountFee, 0.0) * 10000) / 10000.0).toString()

            val response = httpClient
                .get("https://li.quest/v1/quote") {
                    parameter("fromChain", fromChain)
                    parameter("toChain", toChain)
                    parameter("fromToken", fromToken)
                    parameter("toToken", toToken)
                    parameter("fromAmount", fromAmount)
                    parameter("fromAddress", fromAddress)
                    parameter("toAddress", toAddress)
                    if (!isSolanaChainInvolved) {
                        parameter(
                            "integrator",
                            INTEGRATOR_ACCOUNT
                        )
                        parameter("fee", updatedFeeIntegrator)
                    }
                }
        return try {
            json.decodeFromString(
                liFiSwapQuoteResponseSerializer,
                response.body<String>()
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            LiFiSwapQuoteDeserialized.Error(
                LiFiSwapQuoteError(
                    HttpStatusCode.fromValue(response.status.value).description
                )
            )
        }
    }

    companion object {
        private const val INTEGRATOR_ACCOUNT = "vultisig-android"
        private const val INTEGRATOR_FEE = "0.005"
    }
}