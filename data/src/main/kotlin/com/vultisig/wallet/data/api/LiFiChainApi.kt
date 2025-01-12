package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.LiFiSwapQuoteError
import com.vultisig.wallet.data.api.models.LiFiSwapQuoteDeserialized
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.oneInchChainId
import com.vultisig.wallet.data.utils.LiFiSwapQuoteResponseSerializer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import javax.inject.Inject

interface LiFiChainApi {
    suspend fun getSwapQuote(
        fromChain: String,
        toChain: String,
        fromToken: String,
        toToken: String,
        fromAmount: String,
        fromAddress: String,
        toAddress: String,
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
    ): LiFiSwapQuoteDeserialized {
        val isSolanaChainInvolved = fromChain.toLong() == Chain.Solana.oneInchChainId() ||
                toChain.toLong() == Chain.Solana.oneInchChainId()
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
                        parameter("fee", INTEGRATOR_FEE)
                    }
                }
        return try {
            json.decodeFromString(
                liFiSwapQuoteResponseSerializer,
                response.body<String>()
            )
        } catch (e: Exception) {
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