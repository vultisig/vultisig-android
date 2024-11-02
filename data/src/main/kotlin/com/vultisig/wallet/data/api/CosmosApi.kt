package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.cosmos.CosmosBalance
import com.vultisig.wallet.data.api.models.cosmos.CosmosBalanceResponse
import com.vultisig.wallet.data.api.models.cosmos.CosmosTHORChainAccountResponse
import com.vultisig.wallet.data.api.models.cosmos.CosmosTransactionBroadcastResponse
import com.vultisig.wallet.data.api.models.cosmos.THORChainAccountValue
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.utils.CosmosTHORChainResponseSerializer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

interface CosmosApi {
    suspend fun getBalance(address: String): List<CosmosBalance>
    suspend fun getAccountNumber(address: String): THORChainAccountValue
    suspend fun broadcastTransaction(tx: String): String?
}

interface CosmosApiFactory {
    fun createCosmosApi(chain: Chain): CosmosApi
}

internal class CosmosApiFactoryImp @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    private val cosmosTHORChainResponseSerializer: CosmosTHORChainResponseSerializer,
) : CosmosApiFactory {
    override fun createCosmosApi(chain: Chain): CosmosApi {
        return when (chain) {
            Chain.GaiaChain -> CosmosApiImp(
                httpClient,
                "https://cosmos-rest.publicnode.com",
                json,
                cosmosTHORChainResponseSerializer,
            )

            Chain.Kujira -> CosmosApiImp(
                httpClient,
                "https://kujira-rest.publicnode.com",
                json,
                cosmosTHORChainResponseSerializer,
            )

            Chain.Dydx -> CosmosApiImp(
                httpClient,
                "https://dydx-rest.publicnode.com",
                json,
                cosmosTHORChainResponseSerializer,
            )

            Chain.Osmosis -> CosmosApiImp(
                httpClient,
                "https://osmosis-rest.publicnode.com",
                json,
                cosmosTHORChainResponseSerializer,
            )

            else -> throw IllegalArgumentException("Unsupported chain $chain")
        }
    }
}

internal class CosmosApiImp(
    private val httpClient: HttpClient,
    private val rpcEndpoint: String,
    private val json: Json,
    private val cosmosTHORChainResponseSerializer: CosmosTHORChainResponseSerializer,
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
            cosmosTHORChainResponseSerializer,
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
}