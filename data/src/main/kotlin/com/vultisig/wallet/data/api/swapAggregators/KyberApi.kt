package com.vultisig.wallet.data.api.swapAggregators

import androidx.compose.ui.text.toLowerCase
import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteResponse
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.gasForChain
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.utils.KyberSwapQuoteResponseJsonSerializer
import com.vultisig.wallet.data.utils.OneInchSwapQuoteResponseJsonSerializer
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.math.BigInteger
import io.ktor.http.path
import javax.inject.Inject
import kotlin.text.lowercase

interface KyberApi {

    suspend fun getSwapQuote(
        chain: Chain, srcTokenContractAddress: String, dstTokenContractAddress: String,
        amount: String, srcAddress: String, isAffiliate: Boolean
    ): KyberSwapQuoteDeserialized
}

class KyberApiImpl @Inject constructor(
    private val httpClient: HttpClient,
    private val kyberSwapQuoteResponseJsonSerializer: KyberSwapQuoteResponseJsonSerializer,
    private val json: Json,
) : KyberApi {
    private val aggregatorApiBaseUrl = "https://aggregator-api.kyberswap.com"
    private val tokenApiBaseUrl = "https://ks-setting.kyberswap.com"
    private val clientId = "vultisig-android"
    private val nullAddress = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
    //let url = "https://aggregator-api.kyberswap.com/\(chain)/api/v1/routes
    // ?tokenIn=\(tokenIn)&tokenOut=\(tokenOut)&amountIn=\(amountIn)&saveGas=\(saveGas)&gasInclude=\(gasInclude)&slippageTolerance=\(slippageTolerance)"


    override suspend fun getSwapQuote(
        chain: Chain, srcTokenContractAddress: String, dstTokenContractAddress: String,
        amount: String, srcAddress: String, isAffiliate: Boolean
    ): KyberSwapQuoteDeserialized {
        val sourceAddress =
            if (srcTokenContractAddress.isEmpty()) nullAddress else srcTokenContractAddress
        val destinationAddress =
            if (dstTokenContractAddress.isEmpty()) nullAddress else dstTokenContractAddress

        val response = httpClient.get(aggregatorApiBaseUrl) {
            url {
                path(
                    chain.raw.lowercase(),
                    "api/v1/routes",
                )
                parameters.append(
                    "tokenIn",
                    sourceAddress
                )
                parameters.append(
                    "tokenOut",
                    destinationAddress
                )
                parameters.append(
                    "amountIn",
                    amount
                )
                parameters.append(
                    "saveGas",
                    "false"
                )
                parameters.append(
                    "gasInclude",
                    "true"
                )
                parameters.append(
                    "slippageTolerance",
                    "100"
                )
                headers {
                    append(
                        HttpHeaders.Accept,
                        "application/json"
                    )
                    append(
                        HttpHeaders.ContentType,
                        "application/json"
                    )
                    append(
                        "x-client-id",
                        clientId
                    )
                }
            }
        }

        val responseString = response.body<String>()

        if (!response.status.isSuccess()) {
            return KyberSwapQuoteDeserialized.Error(
                error = HttpStatusCode.fromValue(response.status.value).description
            )
        }
        val errorResponse = runCatching {
            json.decodeFromString<KyberSwapErrorResponse>(responseString)
        }.getOrNull()

        if (errorResponse != null && errorResponse.code != 0) {
            throw KyberSwapError.ApiError(
                errorResponse.code,
                errorResponse.message,
                errorResponse.details
            )
        }

        val route = json.decodeFromString<KyberSwapRouteResponse>(responseString)
        return KyberSwapQuoteDeserialized.Result(
            buildTransactionWithFallback(
                chain,
                route,
                srcAddress
            )
        )
    }


    private suspend fun buildTransactionWithFallback(
        chain: Chain, routeResponse: KyberSwapRouteResponse, from: String
    ): KyberSwapQuoteResponse {
        return try {
            buildTransaction(
                chain,
                routeResponse,
                from,
                enableGasEstimation = true
            )
        } catch (e: KyberSwapError.TransactionWillRevert) {
            if (e.message?.contains("TransferHelper") == true) {
                buildTransaction(
                    chain,
                    routeResponse,
                    from,
                    enableGasEstimation = false
                )
            } else {
                error(e.message ?: "Unknown KyberSwap transaction revert error")
            }
        }
    }

    private suspend fun buildTransaction(
        chain: Chain, routeResponse: KyberSwapRouteResponse, from: String,
        enableGasEstimation: Boolean
    ): KyberSwapQuoteResponse {


        val buildPayload = KyberSwapBuildRequest(
            routeSummary = routeResponse.data.routeSummary,
            sender = from,
            recipient = from,
            slippageTolerance = 100,
            deadline = (System.currentTimeMillis() / 1000L + 1200).toInt(),
            enableGasEstimation = enableGasEstimation,
            source = "vultisig-android"
        )
        val responseString = httpClient.post(aggregatorApiBaseUrl) {
            url {
                path(
                    chain.raw.lowercase(),
                    "api/v1/route/build",
                )

                headers {
                    append(
                        HttpHeaders.Accept,
                        "application/json"
                    )
                    append(
                        HttpHeaders.ContentType,
                        "application/json"
                    )
                    append(
                        "x-client-id",
                        clientId
                    )
                }
            }
            setBody(json.encodeToString(buildPayload))
        }.bodyAsText()

        val errorResponse = runCatching {
            json.decodeFromString<KyberSwapErrorResponse>(responseString)
        }.getOrNull()
        if (errorResponse != null && errorResponse.code != 0) {
            when {
                errorResponse.message.contains("execution reverted") -> throw KyberSwapError.TransactionWillRevert(errorResponse.message)

                errorResponse.message.contains("insufficient allowance") -> throw KyberSwapError.InsufficientAllowance(errorResponse.message)

                errorResponse.message.contains("insufficient funds") -> throw KyberSwapError.InsufficientFunds(errorResponse.message)

                else -> throw KyberSwapError.ApiError(
                    errorResponse.code,
                    errorResponse.message,
                    errorResponse.details
                )
            }
        }

        var buildResponse = json.decodeFromString<KyberSwapQuoteResponse>(responseString)
        val gasPrice = routeResponse.data.routeSummary.gasPrice
        buildResponse = buildResponse.copy(
            data= buildResponse.data.copy(gasPrice = gasPrice)
        )

        val calculatedGas = buildResponse.gasForChain(chain)
        val finalGas =
            if (calculatedGas == 0L) EvmHelper.DEFAULT_ETH_SWAP_GAS_UNIT else calculatedGas
        val gasPriceValue = gasPrice.toBigIntegerOrNull() ?: BigInteger("20000000000")
        val minGasPrice = BigInteger("1000000000")
        val finalGasPrice = if (gasPriceValue < minGasPrice) minGasPrice else gasPriceValue
        // Fix: buildResponse.data is a KyberSwapQuoteData, not a numeric type. You likely want to update a fee or value field, not replace the whole data object with a BigInteger.
        // If you want to update a fee field inside data, do so like this (assuming 'fee' is a String or BigInteger):
        val newFee = finalGas.toBigInteger() * finalGasPrice

        buildResponse = buildResponse.copy(
            data = buildResponse.data.copy(fee = newFee)
        )

        return buildResponse
    }

    suspend fun fetchTokens(chain: Chain): List<KyberSwapToken> {
        val responseString = httpClient.get(tokenApiBaseUrl) {
            url {
                path(
                    "/api/v1/tokens",
                )
                parameters {
                    append(
                        "chainIds",
                        clientId
                    )
                    append(
                        "isWhitelisted",
                        "true"
                    )
                    append(
                        "pageSize",
                        "100"
                    )
                }
            }
        }.bodyAsText()

        val response = json.decodeFromString<KyberSwapTokensResponse>(responseString)
        return response.data.tokens
    }

    sealed class KyberSwapError(message: String) : Exception(message) {
        class ApiError(val code: Int, message: String, val details: List<String>?) :
            KyberSwapError(message)

        class TransactionWillRevert(message: String) : KyberSwapError(message)
        class InsufficientAllowance(message: String) : KyberSwapError(message)
        class InsufficientFunds(message: String) : KyberSwapError(message)
    }

    @Serializable
    data class KyberSwapRouteResponse(
        val code: Int, val message: String, val data: RouteData, val requestId: String
    ) {
        @Serializable
        data class RouteData(
            val routeSummary: RouteSummary, val routerAddress: String
        )

        @Serializable
        data class RouteSummary(
            val tokenIn: String, val amountIn: String, val amountInUsd: String,
            val tokenInMarketPriceAvailable: Boolean? = null, val tokenOut: String,
            val amountOut: String, val amountOutUsd: String,
            val tokenOutMarketPriceAvailable: Boolean? = null, val gas: String,
            val gasPrice: String, val gasUsd: String, val l1FeeUsd: String? = null,
            val additionalCostUsd: String? = null, val additionalCostMessage: String? = null,
            val extraFee: ExtraFee? = null, val route: List<List<RouteStep>>, val routeID: String,
            val checksum: String, val timestamp: Int
        ) {
            @Serializable
            data class ExtraFee(
                val feeAmount: String, val chargeFeeBy: String, val isInBps: Boolean,
                val feeReceiver: String
            )

            @Serializable
            data class RouteStep(
                val pool: String, val tokenIn: String, val tokenOut: String, val swapAmount: String,
                val amountOut: String, val exchange: String, val poolType: String,
                val poolExtra: JsonElement? = null, val extra: JsonElement? = null
            )
        }
    }

    @Serializable
    data class KyberSwapBuildRequest(
        val routeSummary: KyberSwapRouteResponse.RouteSummary, val sender: String,
        val recipient: String, val slippageTolerance: Int = 100, val deadline: Int,
        val enableGasEstimation: Boolean = true, val source: String? = "vultisig-android",
        val ignoreCappedSlippage: Boolean? = false
    )

    @Serializable
    data class KyberSwapErrorResponse(
        val code: Int, val message: String, val details: List<String>? = null,
        val requestId: String? = null
    )

    @Serializable
    data class KyberSwapToken(
        val address: String, val symbol: String, val name: String, val decimals: Int,
        val logoURI: String? = null
    ) {
        val logoUrl: String?
            get() = logoURI
    }

    fun KyberSwapToken.toCoinMeta(chain: Chain): Coin {
        return Coin(
            chain = chain,
            ticker = this.symbol,
            logo = this.logoURI ?: "",
            decimal = this.decimals,
            priceProviderID = "",
            // ?!  address = this.address
            contractAddress = this.address,
            isNativeToken = false,
            address = "",
            hexPublicKey = ""
        )
    }

    @Serializable
    data class KyberSwapTokensResponse(
        val data: TokensData
    ) {
        @Serializable
        data class TokensData(
            val tokens: List<KyberSwapToken>
        )
    }


}