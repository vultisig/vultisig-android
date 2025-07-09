package com.vultisig.wallet.data.api.swapAggregators

import com.vultisig.wallet.data.api.models.KyberSwapBuildRequest
import com.vultisig.wallet.data.api.models.KyberSwapError
import com.vultisig.wallet.data.api.models.KyberSwapErrorResponse
import com.vultisig.wallet.data.api.models.KyberSwapRouteResponse
import com.vultisig.wallet.data.api.models.KyberSwapToken
import com.vultisig.wallet.data.api.models.KyberSwapTokensResponse
import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteResponse
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.utils.KyberSwapQuoteResponseJsonSerializer
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
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
    //let url = "https://aggregator-api.kyberswap.com/\(chain)/api/v1/routes
    // ?tokenIn=\(tokenIn)&tokenOut=\(tokenOut)&amountIn=\(amountIn)&saveGas=\(saveGas)&gasInclude=\(gasInclude)&slippageTolerance=\(slippageTolerance)"


    override suspend fun getSwapQuote(
        chain: Chain, srcTokenContractAddress: String, dstTokenContractAddress: String,
        amount: String, srcAddress: String, isAffiliate: Boolean
    ): KyberSwapQuoteDeserialized {
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
                    accept(ContentType.Application.Json)
                    append(
                        "x-client-id",
                        CLIENT_ID
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
                    accept(ContentType.Application.Json)
                    append(
                        "x-client-id",
                        CLIENT_ID
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

        val gasPrice = routeResponse.data.routeSummary.gasPrice
        var buildResponse = json.decodeFromString<KyberSwapQuoteResponse>(responseString)
            .apply {
                data = data.copy(gasPrice = gasPrice)
            }

        val calculatedGas = buildResponse.gasForChain(chain)
        val finalGas =
            if (calculatedGas == 0L) EvmHelper.DEFAULT_ETH_SWAP_GAS_UNIT else calculatedGas
        val gasPriceValue = gasPrice.toBigIntegerOrNull() ?: BigInteger.valueOf(GAS_PRICE_VALUE)
        val minGasPrice = BigInteger.valueOf(MIN_GAS_PRICE)
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
                        chain.id
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

    companion object {
        private const val CLIENT_ID = "vultisig-android"
        private const val NULL_ADDRESS = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
        private const val GAS_PRICE_VALUE = 20000000000L
        private const val MIN_GAS_PRICE = 1000000000L
    }
}