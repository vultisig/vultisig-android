package com.vultisig.wallet.data.api

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.vultisig.wallet.data.models.SplAmountRpcResponseJson
import com.vultisig.wallet.data.models.SplTokenJson
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
    suspend fun getSPLTokens(walletAddress: String): String?
    suspend fun getSPLTokensInfo(tokens: List<String>): List<SplTokenJson>
    suspend fun getSPLTokenBalance(walletAddress: String, coinAddress: String): String?
}

internal class SolanaApiImp @Inject constructor(
    private val gson: Gson,
    private val httpClient: HttpClient,
) : SolanaApi {
    private val rpcEndpoint = "https://api.mainnet-beta.solana.com"
    private val splTokensInfoEndpoint = "https://api.solana.fm/v1/tokens"
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
        try {
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
        } catch (e: Exception) {
            Timber.tag("SolanaApiImp").e("Error getting high priority fee: ${e.message}")
        }
        return "0"
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
            return result.get("result").asString
        } catch (e: Exception) {
            Timber.tag("SolanaApiImp").e("Error broadcasting transaction: ${e.message}")
            throw e
        }

    }

    override suspend fun getSPLTokensInfo(tokens: List<String>): List<SplTokenJson> {
        try {
            val requestBody = SPLTokenRequestJson(
                tokens = tokens
            )
            val response = httpClient.post(splTokensInfoEndpoint) {
                setBody(gson.toJson(requestBody))
            }
            val responseRawString = response.bodyAsText()
            val result = gson.fromJson(responseRawString, JsonObject::class.java)
            if (result.has("error")) {
                Timber.tag("SolanaApiImp").d("Error getting spl tokens: $responseRawString")
                return emptyList()
            }
            return result.asMap().map {
                gson.fromJson(it.value.toString(), SplTokenJson::class.java)
            }
        } catch (e: Exception) {
            Timber.tag("SolanaApiImp").e("Error getting spl tokens: ${e.message}")
            return emptyList()
        }
    }

    override suspend fun getSPLTokens(walletAddress: String): String? {
        try {
            val payload = RpcPayload(
                jsonrpc = "2.0",
                method = "getTokenAccountsByOwner",
                params = listOf(
                    walletAddress,
                    mapOf("programId" to PROGRAM_ID_SPL_REQUEST_PARAM),
                    mapOf("encoding" to ENCODING_SPL_REQUEST_PARAM)
                ),
                id = 1,
            )
            val response = httpClient.post(rpcEndpoint) {
                setBody(gson.toJson(payload))
            }
            val responseContent = response.bodyAsText()
            Timber.d(responseContent)
            val rpcResp: JsonObject = gson.fromJson(responseContent, JsonObject::class.java)

            if (rpcResp.has("error")) {
                Timber.d("get spl token addresses error: ${rpcResp.get("error")}")
                return null
            }
            val value: JsonArray = rpcResp.getAsJsonObject("result")
                .getAsJsonArray("value")
            return value.toString()
        } catch (e: Exception) {
            Timber.e(e)
            return null
        }
    }

    override suspend fun getSPLTokenBalance(walletAddress: String, coinAddress: String): String? {
        try {
            val payload = RpcPayload(
                jsonrpc = "2.0",
                method = "getTokenAccountsByOwner",
                params = listOf(
                    walletAddress,
                    mapOf("mint" to coinAddress),
                    mapOf("encoding" to ENCODING_SPL_REQUEST_PARAM)
                ),
                id = 1,
            )
            val response = httpClient.post(rpcEndpoint) {
                setBody(gson.toJson(payload))
            }
            val responseContent = response.bodyAsText()
            Timber.d(responseContent)
            val rpcResp: JsonObject = gson.fromJson(responseContent, JsonObject::class.java)

            if (rpcResp.has("error")) {
                Timber.d("get spl token amount error: ${rpcResp.get("error")}")
                return null
            }
            val value: SplAmountRpcResponseJson = gson.fromJson(
                rpcResp.getAsJsonObject("result").toString(),
                SplAmountRpcResponseJson::class.java
            )
            return value.value[0].account.data.parsed.info.tokenAmount.amount
        } catch (e: Exception) {
            Timber.e(e)
            return null
        }
    }


    companion object {
        private const val PROGRAM_ID_SPL_REQUEST_PARAM =
            "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        private const val ENCODING_SPL_REQUEST_PARAM = "jsonParsed"
    }

    private data class SPLTokenRequestJson(
        @SerializedName("tokens")
        val tokens: List<String>
    )

}