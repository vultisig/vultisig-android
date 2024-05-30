package com.vultisig.wallet.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

data class SolanaFeeObject(
    val prioritizationFee: BigInteger,
    val slot: BigInteger,
)

internal interface SolanaApi {
    suspend fun getBalance(address: String): String
    suspend fun getRecentBlockHash(): String
    suspend fun getHighPriorityFee(account: String): String
    suspend fun broadcastTransaction(tx: String): String?
}

internal class SolanaApiImp @Inject constructor(
    private val gson: Gson,
    private val httpClient: HttpClient,
) : SolanaApi {
    private val rpcEndpoint = "https://api.mainnet-beta.solana.com"
    override suspend fun getBalance(address: String): String {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "getBalance",
            params = listOf(address),
            id = 1,
        )
        val response = httpClient.post(rpcEndpoint) {
            header("Content-Type", "application/json")
            setBody(gson.toJson(payload))
        }
        val responseContent = response.bodyAsText()
        Timber.tag("solanaApiImp").d(responseContent)
        val rpcResp = gson.fromJson(responseContent, JsonObject::class.java)

        if (rpcResp.has("error")) {
            Timber.tag("solanaApiImp")
                .d("get balance ,address: $address error: ${rpcResp.get("error")}")
            return "0"
        }
        return rpcResp.get("result")?.asJsonObject?.get("value").toString()
    }

    override suspend fun getRecentBlockHash(): String {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "getLatestBlockhash",
            params = listOf("commitment" to "finalized"),
            id = 1,
        )
        val response = httpClient.post(rpcEndpoint) {
            header("Content-Type", "application/json")
            setBody(gson.toJson(payload))
        }
        val responseContent = response.bodyAsText()
        Timber.tag("solanaApiImp").d(responseContent)
        val rpcResp = gson.fromJson(responseContent, JsonObject::class.java)

        if (rpcResp.has("error")) {
            Timber.tag("solanaApiImp")
                .d("get recent blockhash  error: ${rpcResp.get("error")}")
            return ""
        }
        return rpcResp
            .getAsJsonObject("result")
            .getAsJsonObject("value")
            .get("blockhash").asString
    }

    override suspend fun getHighPriorityFee(account: String): String {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "getRecentPrioritizationFees",
            params = listOf(listOf(account)),
            id = 1,
        )
        val response = httpClient.post(rpcEndpoint) {
            header("Content-Type", "application/json")
            setBody(gson.toJson(payload))
        }
        val responseContent = response.bodyAsText()
        Timber.d(responseContent)
        val rpcResp = gson.fromJson(responseContent, JsonObject::class.java)

        if (rpcResp.has("error")) {
            Timber.d("get high priority fee  error: ${rpcResp.get("error")}")
            return ""
        }
        val listType = object : TypeToken<List<SolanaFeeObject>>() {}.type
        val fees: List<SolanaFeeObject> = gson.fromJson(
            rpcResp["result"],
            listType
        )
        return fees.maxOf { it.prioritizationFee }.toString()
    }

    override suspend fun broadcastTransaction(tx: String): String? {
        try {
            val requestBody = RpcPayload(
                jsonrpc = "2.0",
                method = "sendTransaction",
                params = listOf(tx),
                id = 1,
            )
            val response = httpClient.post(rpcEndpoint) {
                header("Content-Type", "application/json")
                setBody(gson.toJson(requestBody))
            }
            val responseRawString = response.bodyAsText()
            val result = gson.fromJson(responseRawString, JsonObject::class.java)
            if (result.has("error")) {
                Timber.tag("SolanaApiImp").d("Error broadcasting transaction: $responseRawString")
                return null
            }
            return result.get("result").toString()
        } catch (e: Exception) {
            Timber.tag("SolanaApiImp").e("Error broadcasting transaction: ${e.message}")
            throw e
        }

    }
}