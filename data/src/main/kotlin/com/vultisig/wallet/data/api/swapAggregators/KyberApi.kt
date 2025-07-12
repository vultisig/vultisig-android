package com.vultisig.wallet.data.api.swapAggregators

import com.vultisig.wallet.data.api.models.KyberSwapBuildRequest
import com.vultisig.wallet.data.api.models.KyberSwapRouteResponse
import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import io.ktor.http.path
import javax.inject.Inject
import kotlin.text.lowercase

interface KyberApi {

    suspend fun getSwapQuote(
        chain: Chain,
        srcTokenContractAddress: String,
        dstTokenContractAddress: String,
        amount: String,
        srcAddress: String,
        isAffiliate: Boolean,
    ): KyberSwapRouteResponse

    suspend fun getKyberSwapQuote(
        chain: Chain,
        routeSummery: KyberSwapRouteResponse.RouteSummary,
        from: String,
        enableGasEstimation: Boolean,
        isAffiliate: Boolean,
    ): KyberSwapQuoteJson
}

class KyberApiImpl @Inject constructor(
    private val httpClient: HttpClient,
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
    ): KyberSwapRouteResponse {
        val sourceAddress =
            if (srcTokenContractAddress.isEmpty()) NULL_ADDRESS else srcTokenContractAddress
        val destinationAddress =
            if (dstTokenContractAddress.isEmpty()) NULL_ADDRESS else dstTokenContractAddress

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

        return response.bodyOrThrow()
    }

    override suspend fun getKyberSwapQuote(
        chain: Chain,
        routeSummery: KyberSwapRouteResponse.RouteSummary,
        from: String,
        enableGasEstimation: Boolean,
        isAffiliate: Boolean,
    ): KyberSwapQuoteJson {

        val request =   KyberSwapBuildRequest(
            routeSummary = routeSummery,
            sender = from,
            recipient = from,
            slippageTolerance = SLIPPAGE_TOLERANCE,
            deadline = (System.currentTimeMillis() / 1000L + 1200).toInt(),
            enableGasEstimation = enableGasEstimation,
            source = CLIENT_ID,
            referral = if (isAffiliate) REFERRER_ADDRESS else null,
            ignoreCappedSlippage = false
        )

        return httpClient.post(aggregatorApiBaseUrl) {
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
        }.bodyOrThrow<KyberSwapQuoteJson>()
    }


    companion object {
        private const val REFERRER_ADDRESS = "0xa4a4f610e89488eb4ecc6c63069f241a54485269"
        private const val CLIENT_ID = "vultisig-android"
        private const val NULL_ADDRESS = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
        private const val SLIPPAGE_TOLERANCE = 2.5
    }
}