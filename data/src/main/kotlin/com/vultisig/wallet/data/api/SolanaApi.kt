package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.BroadcastTransactionRespJson
import com.vultisig.wallet.data.api.models.RecentBlockHashResponseJson
import com.vultisig.wallet.data.api.models.RpcPayload
import com.vultisig.wallet.data.api.models.SPLTokenRequestJson
import com.vultisig.wallet.data.api.models.SolanaBalanceJson
import com.vultisig.wallet.data.api.models.SolanaFeeObjectJson
import com.vultisig.wallet.data.api.models.SolanaFeeObjectRespJson
import com.vultisig.wallet.data.api.models.SplAmountRpcResponseJson
import com.vultisig.wallet.data.api.models.SplResponseAccountJson
import com.vultisig.wallet.data.api.models.SplResponseJson
import com.vultisig.wallet.data.api.models.SplTokenJson
import com.vultisig.wallet.data.models.SplTokenDeserialized
import com.vultisig.wallet.data.utils.SplTokenResponseJsonSerializer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

interface SolanaApi {
    suspend fun getBalance(address: String): BigInteger
    suspend fun getRecentBlockHash(): String
    suspend fun getHighPriorityFee(account: String): String
    suspend fun broadcastTransaction(tx: String): String?
    suspend fun getSPLTokens(walletAddress: String): List<SplResponseAccountJson>?
    suspend fun getSPLTokensInfo(tokens: List<String>): List<SplTokenJson>
    suspend fun getSPLTokenBalance(walletAddress: String, coinAddress: String): String?
}

internal class SolanaApiImp @Inject constructor(
    private val json: Json,
    private val httpClient: HttpClient,
    private val splTokenSerializer: SplTokenResponseJsonSerializer,
) : SolanaApi {

    private val rpcEndpoint = "https://api.mainnet-beta.solana.com"
    private val splTokensInfoEndpoint = "https://api.solana.fm/v1/tokens"
    override suspend fun getBalance(address: String): BigInteger {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "getBalance",
            params = buildJsonArray {
                add(address)
            },
            id = 1,
        )
        val response = httpClient.post(rpcEndpoint) {
            setBody(payload)
        }
        val rpcResp = response.body<SolanaBalanceJson>()
        Timber.tag("solanaApiImp").d(response.toString())

        if (rpcResp.error != null) {
            Timber.tag("solanaApiImp")
                .d("get balance ,address: $address error: ${rpcResp.error}")
            return BigInteger.ZERO
        }
        return rpcResp.result?.value ?: error("getBalance error")
    }

    override suspend fun getRecentBlockHash(): String {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "getLatestBlockhash",
            params = buildJsonArray {
                addJsonObject {
                    put("commitment", "finalized")
                }
            },
            id = 1,
        )
        val response = httpClient.post(rpcEndpoint) {
            setBody(payload)
        }
        val responseContent = response.bodyAsText()
        Timber.tag("solanaApiImp").d(responseContent)
        val rpcResp = response.body<RecentBlockHashResponseJson>()
        if (rpcResp.error != null) {
            Timber.tag("solanaApiImp")
                .d("get recent blockhash  error: ${rpcResp.error}")
            return ""
        }
        return rpcResp.result?.value?.blockHash ?: error("getRecentBlockHash error")
    }

    override suspend fun getHighPriorityFee(account: String): String {
        try {
            val payload = RpcPayload(
                jsonrpc = "2.0",
                method = "getRecentPrioritizationFees",
                params = buildJsonArray {
                    addJsonArray {
                        add(account)
                    }
                },
                id = 1,
            )
            val response = httpClient.post(rpcEndpoint) {
                setBody(payload)
            }
            val responseContent = response.bodyAsText()
            Timber.d(responseContent)
            val rpcResp = response.body<SolanaFeeObjectRespJson>()

            if (rpcResp.error != null) {
                Timber.d("get high priority fee  error: ${rpcResp.error}")
                return ""
            }
            val fees: List<SolanaFeeObjectJson> =
                rpcResp.result ?: error("getHighPriorityFee error")
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
                params = buildJsonArray {
                    add(tx)
                },
                id = 1,
            )
            val response = httpClient.post(rpcEndpoint) {
                setBody(requestBody)
            }
            val responseRawString = response.bodyAsText()
            val result = response.body<BroadcastTransactionRespJson>()
            if (result.error != null) {
                Timber.tag("SolanaApiImp").d("Error broadcasting transaction: $responseRawString")
                return null
            }
            return result.result ?: error("broadcastTransaction error")
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
                setBody(requestBody)
            }
            val responseRawString = response.bodyAsText()
            when (val result = json.decodeFromString(splTokenSerializer, responseRawString)) {
                is SplTokenDeserialized.Error -> {
                    Timber.tag("SolanaApiImp").d(
                        "Error getting spl tokens: ${result.error.error.message}"
                    )
                    return emptyList()
                }
                is SplTokenDeserialized.Result -> return result.result.values.toList()
            }
        } catch (e: Exception) {
            Timber.tag("SolanaApiImp").e("Error getting spl tokens: ${e.message}")
            return emptyList()
        }
    }

    override suspend fun getSPLTokens(walletAddress: String): List<SplResponseAccountJson>? {
        try {
            val payload = RpcPayload(
                jsonrpc = "2.0",
                method = "getTokenAccountsByOwner",
                params = buildJsonArray {
                    add(walletAddress)
                    addJsonObject {
                        put("programId", PROGRAM_ID_SPL_REQUEST_PARAM)
                    }
                    addJsonObject {
                        put("encoding", ENCODING_SPL_REQUEST_PARAM)
                    }
                },
                id = 1,
            )
            val response = httpClient.post(rpcEndpoint) {
                setBody(payload)
            }
            val responseContent = response.bodyAsText()
            Timber.d(responseContent)
            val rpcResp = response.body<SplResponseJson>()

            if (rpcResp.error != null) {
                Timber.d("get spl token addresses error: ${rpcResp.error}")
                return null
            }
            return rpcResp.result?.accounts
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
                params = buildJsonArray {
                    add(walletAddress)
                    addJsonObject {
                        put("mint", coinAddress)
                    }
                    addJsonObject {
                        put("encoding", ENCODING_SPL_REQUEST_PARAM)
                    }
                },
                id = 1,
            )
            val response = httpClient.post(rpcEndpoint) {
                setBody(payload)
            }
            val responseContent = response.bodyAsText()
            Timber.d(responseContent)
            val rpcResp = response.body<SplAmountRpcResponseJson>()

            if (rpcResp.error != null) {
                Timber.d("get spl token amount error: ${rpcResp.error}")
                return null
            }
            val value = rpcResp.value ?: error("getSPLTokenBalance error")
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

}