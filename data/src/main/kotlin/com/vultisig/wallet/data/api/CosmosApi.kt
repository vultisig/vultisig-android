package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.cosmos.CosmosBalance
import com.vultisig.wallet.data.api.models.cosmos.CosmosBalanceResponse
import com.vultisig.wallet.data.api.models.cosmos.CosmosIbcDenomTraceDenomTraceJson
import com.vultisig.wallet.data.api.models.cosmos.CosmosIbcDenomTraceJson
import com.vultisig.wallet.data.api.models.cosmos.CosmosTHORChainAccountResponse
import com.vultisig.wallet.data.api.models.cosmos.CosmosTransactionBroadcastResponse
import com.vultisig.wallet.data.api.models.cosmos.THORChainAccountValue
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.utils.CosmosThorChainResponseSerializer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.encodeBase64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import javax.inject.Inject

interface CosmosApi {
    suspend fun getBalance(address: String): List<CosmosBalance>
    suspend fun getAccountNumber(address: String): THORChainAccountValue
    suspend fun broadcastTransaction(tx: String): String?
    suspend fun getWasmTokenBalance(address: String, contractAddress: String): CosmosBalance
    suspend fun getIbcDenomTraces(contractAddress: String): CosmosIbcDenomTraceDenomTraceJson
    suspend fun getLatestBlock(): String
}

interface CosmosApiFactory {
    fun createCosmosApi(chain: Chain): CosmosApi
}

internal class CosmosApiFactoryImp @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    private val cosmosThorChainResponseSerializer: CosmosThorChainResponseSerializer,
) : CosmosApiFactory {
    override fun createCosmosApi(chain: Chain): CosmosApi {
        val apiUrl = when (chain) {
            Chain.GaiaChain -> "https://cosmos-rest.publicnode.com"
            Chain.Kujira -> "https://kujira-rest.publicnode.com"
            Chain.Dydx -> "https://dydx-rest.publicnode.com"
            Chain.Osmosis -> "https://osmosis-rest.publicnode.com"
            Chain.Terra -> "https://terra-lcd.publicnode.com"
            Chain.TerraClassic -> "https://terra-classic-lcd.publicnode.com"
            Chain.Noble -> "https://noble-api.polkachu.com"
            Chain.Akash -> "https://akash-rest.publicnode.com"
            else -> throw IllegalArgumentException("Unsupported chain $chain")
        }

        return CosmosApiImp(httpClient, apiUrl, json, cosmosThorChainResponseSerializer)
    }
}

internal class CosmosApiImp(
    private val httpClient: HttpClient,
    private val rpcEndpoint: String,
    private val json: Json,
    private val cosmosThorChainResponseSerializer: CosmosThorChainResponseSerializer,
) : CosmosApi {
    override suspend fun getBalance(address: String): List<CosmosBalance> {
        val response = httpClient
            .get("$rpcEndpoint/cosmos/bank/v1beta1/balances/$address")
        val resp = response.body<CosmosBalanceResponse>()
        return resp.balances ?: emptyList()
    }

    override suspend fun getAccountNumber(address: String): THORChainAccountValue {
        val response = httpClient
            .get("$rpcEndpoint/cosmos/auth/v1beta1/accounts/$address") {
            }
        val responseBody = response.bodyAsText()
        val decodedResponse = json.decodeFromString(
            cosmosThorChainResponseSerializer,
            responseBody
        )

        Timber.d("getAccountNumber: $responseBody")
        return when (decodedResponse) {
            is CosmosTHORChainAccountResponse.Error ->
                THORChainAccountValue(
                    accountNumber = "0",
                    sequence = "0",
                    address = null
                )

            is CosmosTHORChainAccountResponse.Success ->
                decodedResponse.response.account ?: error("Error getting account")
        }
    }

    override suspend fun broadcastTransaction(tx: String): String? {
        try {
            val response =
                httpClient.post("$rpcEndpoint/cosmos/tx/v1beta1/txs") {
                    contentType(ContentType.Application.Json)
                    setBody(tx)
                }
            val result = response.body<CosmosTransactionBroadcastResponse>()
            val txResponse = result.txResponse
            if (txResponse?.code == 0 || txResponse?.code == 19) {
                return txResponse.txHash
            }
            throw Exception("Error broadcasting transaction: ${response.bodyAsText()}")
        } catch (e: Exception) {
            Timber.tag("CosmosApiService").e("Error broadcasting transaction: ${e.message}")
            throw e
        }
    }

    override suspend fun getWasmTokenBalance(address: String, contractAddress: String): CosmosBalance {
        val payload = "{\"balance\":{\"address\":\"$address\"}}".encodeBase64()

        return CosmosBalance(
            denom = contractAddress,
            amount = httpClient
                .get("$rpcEndpoint/cosmwasm/wasm/v1/contract/$contractAddress/smart/$payload")
                .body<JsonObject>()["data"]
                ?.jsonObject
                ?.get("balance")
                ?.jsonPrimitive
                ?.content ?: "0"
        )
    }

    override suspend fun getIbcDenomTraces(contractAddress: String): CosmosIbcDenomTraceDenomTraceJson {
        val hash = contractAddress.removePrefix("ibc/")
        return httpClient.get("$rpcEndpoint/ibc/apps/transfer/v1/denom_traces/$hash")
            .body<CosmosIbcDenomTraceJson>()
            .denomTrace!!
    }

    override suspend fun getLatestBlock(): String {
        return httpClient.get("$rpcEndpoint/cosmos/base/tendermint/v1beta1/blocks/latest")
            .body<JsonObject>()
            .jsonObject["block"]
            ?.jsonObject
            ?.get("header")
            ?.jsonObject
            ?.get("height")
            ?.jsonPrimitive
            ?.content ?: "0"
    }
}