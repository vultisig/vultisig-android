package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.THORChainSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.THORChainSwapQuoteError
import com.vultisig.wallet.data.api.models.cosmos.CosmosBalance
import com.vultisig.wallet.data.api.models.cosmos.CosmosBalanceResponse
import com.vultisig.wallet.data.api.models.cosmos.CosmosTransactionBroadcastResponse
import com.vultisig.wallet.data.api.models.cosmos.NativeTxFeeRune
import com.vultisig.wallet.data.api.models.cosmos.THORChainAccountResultJson
import com.vultisig.wallet.data.api.models.cosmos.THORChainAccountValue
import com.vultisig.wallet.data.chains.helpers.THORChainSwaps
import com.vultisig.wallet.data.common.Endpoints
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

interface ThorChainApi {

    suspend fun getBalance(
        address: String,
    ): List<CosmosBalance>

    suspend fun getAccountNumber(
        address: String,
    ): THORChainAccountValue

    suspend fun getSwapQuotes(
        address: String,
        fromAsset: String,
        toAsset: String,
        amount: String,
        interval: String,
        isAffiliate: Boolean,
    ): THORChainSwapQuoteDeserialized

    suspend fun broadcastTransaction(tx: String): String?
    suspend fun getTHORChainNativeTransactionFee(): BigInteger

    suspend fun getNetworkChainId(): String

    suspend fun resolveName(
        name: String,
        chain: String
    ): String?

    suspend fun getTransactionDetail(tx: String): ThorChainTransactionJson
}

internal class ThorChainApiImpl @Inject constructor(
    private val httpClient: HttpClient,
    private val thorChainSwapQuoteResponseJsonSerializer: ThorChainSwapQuoteResponseJsonSerializer,
    private val json: Json,
) : ThorChainApi {

    private val xClientID = "X-Client-ID"
    private val xClientIDValue = "vultisig"

    override suspend fun getBalance(address: String): List<CosmosBalance> {
        val response = httpClient
            .get("https://thornode.ninerealms.com/cosmos/bank/v1beta1/balances/$address") {
                header(xClientID, xClientIDValue)
            }
        val resp = response.body<CosmosBalanceResponse>()
        return resp.balances ?: emptyList()
    }

    override suspend fun getSwapQuotes(
        address: String,
        fromAsset: String,
        toAsset: String,
        amount: String,
        interval: String,
        isAffiliate: Boolean,
    ): THORChainSwapQuoteDeserialized {
        try {
            val response = httpClient
                .get("https://thornode.ninerealms.com/thorchain/quote/swap") {
                    parameter("from_asset", fromAsset)
                    parameter("to_asset", toAsset)
                    parameter("amount", amount)
                    parameter("destination", address)
                    parameter("streaming_interval", interval)
                    if (isAffiliate) {
                        parameter("affiliate", THORChainSwaps.AFFILIATE_FEE_ADDRESS)
                        parameter("affiliate_bps", THORChainSwaps.AFFILIATE_FEE_RATE)
                    }
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
            .get("https://thornode.ninerealms.com/auth/accounts/$address") {
                header(xClientID, xClientIDValue)
            }
        return response.body<THORChainAccountResultJson>().result?.value
            ?: error("Field value is not found in the response")
    }

    override suspend fun getTHORChainNativeTransactionFee(): BigInteger {
        try {
            val response = httpClient.get("https://thornode.ninerealms.com/thorchain/network") {
                header(xClientID, xClientIDValue)
            }
            val content = response.body<NativeTxFeeRune>()
            return content.value?.let { BigInteger(it) } ?: 0.toBigInteger()
        } catch (e: Exception) {
            Timber.tag("THORChainService")
                .e("Error getting THORChain native transaction fee: ${e.message}")
            throw e
        }
    }

    override suspend fun broadcastTransaction(tx: String): String? {
        try {
            val response = httpClient.post(Endpoints.THORCHAIN_BROADCAST_TX) {
                contentType(ContentType.Application.Json)
                header(xClientID, xClientIDValue)
                setBody(tx)
            }
            val responseRawString = response.bodyAsText()
            val result = response.body<CosmosTransactionBroadcastResponse>()

            val txResponse = result.txResponse
            if (txResponse?.code == 0 || txResponse?.code == 19) {
                return txResponse.txHash
            }
            throw Exception("Error broadcasting transaction: $responseRawString")
        } catch (e: Exception) {
            Timber.tag("THORChainService").e("Error broadcasting transaction: ${e.message}")
            throw e
        }
    }

    override suspend fun getNetworkChainId(): String =
        httpClient.get("https://rpc.ninerealms.com/status")
            .body<JsonObject>()["result"]
            ?.jsonObject
            ?.get("node_info")
            ?.jsonObject
            ?.get("network")
            ?.jsonPrimitive
            ?.content
            ?: error("Could't find network field in response for THORChain chain id")

    override suspend fun resolveName(
        name: String,
        chain: String,
    ): String? = httpClient
        .get("https://midgard.ninerealms.com/v2/thorname/lookup/$name")
        .body<ThorNameResponseJson>()
        .entries
        .find { it.chain == chain }
        ?.address

    override suspend fun getTransactionDetail(tx: String): ThorChainTransactionJson {
        val response = httpClient
            .get("https://thornode.ninerealms.com/cosmos/tx/v1beta1/txs/$tx")
        if (!response.status.isSuccess()) {
            //The  URL initially returns a 'not found' response but eventually
            // provides a successful response after some time
            if (response.status.equals(HttpStatusCode.NotFound))
                return ThorChainTransactionJson(
                    code = null,
                    codeSpace = null,
                    rawLog = response.bodyAsText()
                )
        } else
            return ThorChainTransactionJson(
                code = response.status.value,
                codeSpace = HttpStatusCode.fromValue(response.status.value).description,
                rawLog = response.bodyAsText()
            )
        return response.body()
    }
}

@Serializable
private data class ThorNameEntryJson(
    @SerialName("chain")
    val chain: String,
    @SerialName("address")
    val address: String,
)

@Serializable
private data class ThorNameResponseJson(
    @SerialName("entries")
    val entries: List<ThorNameEntryJson>,
)

@Serializable
data class ThorChainTransactionJson(
    @SerialName("code")
    val code: Int?,
    @SerialName("codespace")
    val codeSpace: String?,
    @SerialName("raw_log")
    val rawLog: String,
)