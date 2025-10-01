package com.vultisig.wallet.data.api.swapAggregators

import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.KyberSwapBuildRequest
import com.vultisig.wallet.data.api.models.KyberSwapRouteResponse
import com.vultisig.wallet.data.api.models.quotes.KyberSwapErrorResponse
import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.utils.KyberSwapQuoteResponseJsonSerializer
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.http.path
import kotlinx.serialization.json.Json
import javax.inject.Inject

interface KyberApi {

    suspend fun getSwapQuote(
        chain: Chain,
        srcTokenContractAddress: String,
        dstTokenContractAddress: String,
        amount: String,
        srcAddress: String,
        isAffiliate: Boolean,
    ): KyberSwapQuoteDeserialized

    suspend fun getKyberSwapQuote(
        chain: Chain,
        routeSummary: KyberSwapRouteResponse.RouteSummary,
        from: String,
        enableGasEstimation: Boolean,
        isAffiliate: Boolean,
    ): KyberSwapQuoteJson
}

class KyberApiImpl @Inject constructor(
    private val httpClient: HttpClient,
    private val kyberSwapQuoteResponseJsonSerializer: KyberSwapQuoteResponseJsonSerializer,
    private val json: Json,
) : KyberApi {
    private val aggregatorApiBaseUrl = "https://aggregator-api.kyberswap.com"


    override suspend fun getSwapQuote(
        chain: Chain,
        srcTokenContractAddress: String,
        dstTokenContractAddress: String,
        amount: String,
        srcAddress: String,
        isAffiliate: Boolean,
    ): KyberSwapQuoteDeserialized {
        try {
            val sourceAddress =
                srcTokenContractAddress.ifEmpty { NULL_ADDRESS }
            val destinationAddress =
                dstTokenContractAddress.ifEmpty { NULL_ADDRESS }

            val response = httpClient.get(aggregatorApiBaseUrl) {
                url {
                    path(
                        chain.raw.lowercase(),
                        "api/v1/routes",
                    )
                    parameters.apply {
                        append(
                            "tokenIn",
                            sourceAddress
                        )
                        append(
                            "tokenOut",
                            destinationAddress
                        )
                        append(
                            "amountIn",
                            amount
                        )
                        append(
                            "saveGas",
                            "false"
                        )
                        append(
                            "gasInclude",
                            "true"
                        )
                        append(
                            "slippageTolerance",
                            SLIPPAGE_TOLERANCE.toString()
                        )
                        append(
                            "isAffiliate",
                            isAffiliate.toString()
                        )
                        parameter(
                            "sourceIdentifier",
                            if (isAffiliate) CLIENT_ID else null
                        )
                        parameter(
                            "referrerAddress",
                            if (isAffiliate) REFERRER_ADDRESS else null
                        )
                    }

                    headers {
                        accept(ContentType.Application.Json)
                        append(
                            "x-client-id",
                            CLIENT_ID
                        )
                    }
                }

            }

            if (!response.status.isSuccess()) {
                val errorResponse = runCatching {
                    json.decodeFromString<KyberSwapErrorResponse>(response.body<String>())
                }.getOrNull()
                return KyberSwapQuoteDeserialized.Error(
                    errorResponse ?: KyberSwapErrorResponse(
                        message = HttpStatusCode.fromValue(response.status.value).description
                    )
                )
            }

            return json.decodeFromString(
                kyberSwapQuoteResponseJsonSerializer,
                response.body<String>()
            )
        } catch (e: Exception) {
            return KyberSwapQuoteDeserialized.Error(
                KyberSwapErrorResponse(
                    message = e.message ?: "Unknown error"
                )
            )
        }
    }

    override suspend fun getKyberSwapQuote(
        chain: Chain,
        routeSummary: KyberSwapRouteResponse.RouteSummary,
        from: String,
        enableGasEstimation: Boolean,
        isAffiliate: Boolean,
    ): KyberSwapQuoteJson {

        try {
            val request = KyberSwapBuildRequest(
                routeSummary = routeSummary,
                sender = from,
                recipient = from,
                slippageTolerance = SLIPPAGE_TOLERANCE,
                deadline = (System.currentTimeMillis() / 1000L + 1200).toInt(),
                enableGasEstimation = enableGasEstimation,
                source = CLIENT_ID,
                referral = if (isAffiliate) REFERRER_ADDRESS else null,
                ignoreCappedSlippage = false
            )

            val response = httpClient.post(aggregatorApiBaseUrl) {
                url {
                    path(
                        chain.raw.lowercase(),
                        "api/v1/route/build",
                    )

                    headers {
                        accept(ContentType.Application.Json)
                        append(
                            "x-client-id",
                            CLIENT_ID
                        )
                    }
                }
                setBody(json.encodeToString(request))
            }
            if (response.bodyAsText().contains("TransferHelper") && response.bodyAsText()
                    .contains("execution reverted")
            ) {
                return getKyberSwapQuote(
                    chain = chain,
                    routeSummary = routeSummary,
                    from = from,
                    enableGasEstimation = false,
                    isAffiliate = isAffiliate
                )
            }
            if (!response.status.isSuccess()) {
                val errorResponse = runCatching {
                    json.decodeFromString<KyberSwapErrorResponse>(response.bodyAsText())
                }.getOrNull() ?: KyberSwapErrorResponse(
                    message = HttpStatusCode.fromValue(response.status.value).description
                )
                throw SwapException.handleSwapException(errorResponse.message)
            }
            return response.bodyOrThrow<KyberSwapQuoteJson>()
        } catch (e: Exception) {
            throw SwapException.handleSwapException(e.message.toString())
        }
    }


    companion object {
        private const val REFERRER_ADDRESS = "0x8E247a480449c84a5fDD25974A8501f3EFa4ABb9"
        private const val CLIENT_ID = "vultisig-android"
        private const val NULL_ADDRESS = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
        private const val SLIPPAGE_TOLERANCE = 2.5
    }
}