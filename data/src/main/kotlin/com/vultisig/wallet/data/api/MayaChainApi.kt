package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuoteError
import com.vultisig.wallet.data.api.models.cosmos.CosmosBalance
import com.vultisig.wallet.data.api.models.cosmos.CosmosBalanceResponse
import com.vultisig.wallet.data.api.models.cosmos.CosmosTransactionBroadcastResponse
import com.vultisig.wallet.data.api.models.cosmos.MayaChainDepositCacaoResponse
import com.vultisig.wallet.data.api.models.cosmos.THORChainAccountResultJson
import com.vultisig.wallet.data.api.models.cosmos.THORChainAccountValue
import com.vultisig.wallet.data.chains.helpers.THORChainSwaps
import com.vultisig.wallet.data.chains.helpers.THORChainSwaps.Companion.MAYA_STREAMING_INTERVAL
import com.vultisig.wallet.data.utils.ThorChainSwapQuoteResponseJsonSerializer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.path
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

interface MayaChainApi {

    suspend fun getBalance(
        address: String,
    ): List<CosmosBalance>

    suspend fun getUnStakeCacaoBalance(
        address: String,
    ): String?

    suspend fun getAccountNumber(
        address: String,
    ): THORChainAccountValue

    suspend fun getSwapQuotes(
        address: String,
        fromAsset: String,
        toAsset: String,
        amount: String,
        isAffiliate: Boolean,
        bpsDiscount: Int,
    ): THORChainSwapQuoteDeserialized

    suspend fun broadcastTransaction(tx: String): String?
}

internal class MayaChainApiImp @Inject constructor(
    private val httpClient: HttpClient,
    private val thorChainSwapQuoteResponseJsonSerializer: ThorChainSwapQuoteResponseJsonSerializer,
    private val json: Json,
) : MayaChainApi {

    private val xClientID = "X-Client-ID"
    private val xClientIDValue = "vultisig"

    override suspend fun getBalance(address: String): List<CosmosBalance> {
        val response = httpClient
            .get("https://mayanode.mayachain.info/cosmos/bank/v1beta1/balances/$address") {
                header(xClientID, xClientIDValue)
            }
        val resp = response.body<CosmosBalanceResponse>()
        return resp.balances ?: emptyList()
    }

    override suspend fun getUnStakeCacaoBalance(address: String): String? {
        return try {
            val request = httpClient.get("https://midgard.mayachain.info") {
                url {
                    path(
                        "v2",
                        "cacaopool",
                        address
                    )
                }
            }.body<List<MayaChainDepositCacaoResponse>>()
            request.firstOrNull()?.cacaoDeposit
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch CACAO pool balance for address: $address")
            null
        }
    }

    override suspend fun getSwapQuotes(
        address: String,
        fromAsset: String,
        toAsset: String,
        amount: String,
        isAffiliate: Boolean,
        bpsDiscount: Int,
    ): THORChainSwapQuoteDeserialized {
        try {
            val affiliateFeeRate =
                maxOf(THORChainSwaps.AFFILIATE_FEE_RATE.toInt() - bpsDiscount, 0).toString()

            val response = httpClient
                .get("https://mayanode.mayachain.info/mayachain/quote/swap") {
                    parameter("from_asset", fromAsset)
                    parameter("to_asset", toAsset)
                    parameter("amount", amount)
                    parameter("destination", address)
                    parameter("streaming_interval", MAYA_STREAMING_INTERVAL)
                    parameter("affiliate", THORChainSwaps.AFFILIATE_FEE_ADDRESS)
                    parameter("affiliate_bps", if (isAffiliate) affiliateFeeRate else "0")
                    header(xClientID, xClientIDValue)
                }
            if (!response.status.isSuccess()) {
                return THORChainSwapQuoteDeserialized.Error(
                    THORChainSwapQuoteError(
                        HttpStatusCode.fromValue(response.status.value).description
                    )
                )
            }
            val responseRawString = response.body<String>()
            return json.decodeFromString(
                thorChainSwapQuoteResponseJsonSerializer,
                responseRawString
            )
        } catch (e: Exception) {
            return THORChainSwapQuoteDeserialized.Error(
                THORChainSwapQuoteError(
                    e.message ?: "Unknown error"
                )
            )
        }
    }

    override suspend fun getAccountNumber(address: String): THORChainAccountValue {
        val response = httpClient
            .get("https://mayanode.mayachain.info/auth/accounts/$address") {
                header(xClientID, xClientIDValue)
            }
        val responseBody = response.body<THORChainAccountResultJson>()
        Timber.d("getAccountNumber: $responseBody")
        return responseBody.result?.value ?: error("Field value is not found in the response")
    }

    override suspend fun broadcastTransaction(tx: String): String? {
        try {
            val response =
                httpClient.post("https://mayanode.mayachain.info/cosmos/tx/v1beta1/txs") {
                    contentType(ContentType.Application.Json)
                    header(xClientID, xClientIDValue)
                    setBody(tx)
                }
            val result = response.body<CosmosTransactionBroadcastResponse>()
            val txResponse = result.txResponse
            if (txResponse?.code == 0 || txResponse?.code == 19) {
                return txResponse.txHash
            }
            throw Exception("Error broadcasting transaction: ${response.bodyAsText()}")
        } catch (e: Exception) {
            Timber.tag("MayaChainService").e("Error broadcasting transaction: ${e.message}")
            throw e
        }
    }
}