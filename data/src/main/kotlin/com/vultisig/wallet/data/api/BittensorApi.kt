package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.RpcPayload
import com.vultisig.wallet.data.api.models.cosmos.PolkadotBroadcastTransactionJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetBlockHashJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetBlockHeaderJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetNonceJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetRunTimeVersionJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotQueryInfoResponseJson
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

interface BittensorApi {
    suspend fun getBalance(address: String): BigInteger
    suspend fun getNonce(address: String): BigInteger
    suspend fun getBlockHash(isGenesis: Boolean = false): String
    suspend fun getGenesisBlockHash(): String
    suspend fun getRuntimeVersion(): Pair<BigInteger, BigInteger>
    suspend fun getBlockHeader(): BigInteger
    suspend fun broadcastTransaction(tx: String): String?
    suspend fun getPartialFee(tx: String): BigInteger
}

@Serializable
data class TaostatsAccountResponse(
    val pagination: TaostatsPagination? = null,
    val data: List<TaostatsAccountData>? = null,
)

@Serializable
data class TaostatsPagination(
    @SerialName("total_items") val totalItems: Int = 0,
)

@Serializable
data class TaostatsAccountData(
    @SerialName("balance_free") val balanceFree: String = "0",
)

internal class BittensorApiImp @Inject constructor(private val httpClient: HttpClient) :
    BittensorApi {

    override suspend fun getBalance(address: String): BigInteger {
        try {
            val response = httpClient.get("$TAOSTATS_API_URL/account/latest/v1?address=$address&network=finney") {
                header("Authorization", TAOSTATS_API_KEY)
            }
            val taostatsResp = response.body<TaostatsAccountResponse>()
            val data = taostatsResp.data
            if (data.isNullOrEmpty()) return BigInteger.ZERO
            return data[0].balanceFree.toBigIntegerOrNull() ?: BigInteger.ZERO
        } catch (e: Exception) {
            Timber.e("Error fetching Bittensor balance: ${e.message}")
            return BigInteger.ZERO
        }
    }

    override suspend fun getNonce(address: String): BigInteger {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "system_accountNextIndex",
            params = buildJsonArray { add(address) },
            id = 1,
        )
        val response = httpClient.post(BITTENSOR_RPC_URL) { setBody(payload) }
        return response.body<PolkadotGetNonceJson>().result
    }

    override suspend fun getBlockHash(isGenesis: Boolean): String {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "chain_getBlockHash",
            params = buildJsonArray { if (isGenesis) add(0) },
            id = 1,
        )
        val response = httpClient.post(BITTENSOR_RPC_URL) { setBody(payload) }
        return response.body<PolkadotGetBlockHashJson>().result
    }

    override suspend fun getGenesisBlockHash(): String = getBlockHash(true)

    override suspend fun getRuntimeVersion(): Pair<BigInteger, BigInteger> {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "state_getRuntimeVersion",
            params = buildJsonArray {},
            id = 1,
        )
        val response = httpClient.post(BITTENSOR_RPC_URL) { setBody(payload) }
        val rpcResp = response.body<PolkadotGetRunTimeVersionJson>()
        return Pair(rpcResp.result.specVersion, rpcResp.result.transactionVersion)
    }

    override suspend fun getBlockHeader(): BigInteger {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "chain_getHeader",
            params = buildJsonArray {},
            id = 1,
        )
        val response = httpClient.post(BITTENSOR_RPC_URL) { setBody(payload) }
        val responseContent = response.body<PolkadotGetBlockHeaderJson>()
        return BigInteger(responseContent.result.number.drop(2), 16)
    }

    override suspend fun broadcastTransaction(tx: String): String? {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "author_submitExtrinsic",
            params = buildJsonArray { add(if (tx.startsWith("0x")) tx else "0x$tx") },
            id = 1,
        )
        val response = httpClient.post(BITTENSOR_RPC_URL) { setBody(payload) }
        val responseContent = response.body<PolkadotBroadcastTransactionJson>()
        if (responseContent.error != null) {
            // Suppress "Already Imported" — second device in multi-device signing
            if (responseContent.error.message?.contains("Already Imported") == true) {
                return null
            }
            throw Exception(
                "Bittensor broadcast failed: ${responseContent.error.data ?: responseContent.error.message}"
            )
        }
        return responseContent.result
    }

    override suspend fun getPartialFee(tx: String): BigInteger {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "payment_queryInfo",
            params = buildJsonArray { add(if (tx.startsWith("0x")) tx else "0x$tx") },
            id = 1,
        )
        val response = httpClient.post(BITTENSOR_RPC_URL) { setBody(payload) }
        return response
            .bodyOrThrow<PolkadotQueryInfoResponseJson>()
            .result
            ?.partialFee
            ?.toBigIntegerOrNull() ?: throw Exception("Can't obtain Bittensor partial fee")
    }

    private companion object {
        private const val BITTENSOR_RPC_URL = "https://bittensor-finney.api.onfinality.io/public"
        private const val TAOSTATS_API_URL = "https://api.taostats.io/api"
        private const val TAOSTATS_API_KEY = "tao-43f7c8f7-0a77-49aa-9f66-17fc112b3c10:3bc1d30d"
    }
}
