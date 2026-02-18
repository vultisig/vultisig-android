package com.vultisig.wallet.data.api

import RippleBroadcastSuccessResponseJson
import com.vultisig.wallet.data.api.JupiterApiImpl.Companion.JUPITER_URL
import com.vultisig.wallet.data.api.models.BroadcastTransactionRespJson
import com.vultisig.wallet.data.api.models.EvmRpcResponseJson
import com.vultisig.wallet.data.api.models.JupiterTokenResponseJson
import com.vultisig.wallet.data.api.models.RecentBlockHashResponseJson
import com.vultisig.wallet.data.api.models.RpcPayload
import com.vultisig.wallet.data.api.models.SPLTokenRequestJson
import com.vultisig.wallet.data.api.models.SolanaBalanceJson
import com.vultisig.wallet.data.api.models.SolanaFeeForMessageResponse
import com.vultisig.wallet.data.api.models.SolanaFeeObjectJson
import com.vultisig.wallet.data.api.models.SolanaFeeObjectRespJson
import com.vultisig.wallet.data.api.models.SolanaMinimumBalanceForRentExemptionJson
import com.vultisig.wallet.data.api.models.SolanaSignatureStatusesResult
import com.vultisig.wallet.data.api.models.SplAmountRpcResponseJson
import com.vultisig.wallet.data.api.models.SplResponseAccountJson
import com.vultisig.wallet.data.api.models.SplResponseJson
import com.vultisig.wallet.data.api.models.SplTokenInfo
import com.vultisig.wallet.data.api.models.SplTokenJson
import com.vultisig.wallet.data.api.utils.postRpc
import com.vultisig.wallet.data.models.SplTokenDeserialized
import com.vultisig.wallet.data.utils.SplTokenResponseJsonSerializer
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

interface SolanaApi {
    suspend fun getBalance(address: String): BigInteger
    suspend fun getMinimumBalanceForRentExemption(): BigInteger
    suspend fun getRecentBlockHash(): String
    suspend fun getHighPriorityFee(account: String): String
    suspend fun broadcastTransaction(tx: String): String?
    suspend fun getSPLTokens(walletAddress: String): List<SplResponseAccountJson>?
    suspend fun getSPLTokensInfo(tokens: List<String>): List<SplTokenJson>
    suspend fun getSPLTokensInfo2(tokens: List<String>): List<SplTokenInfo>
    suspend fun getJupiterTokens(): List<JupiterTokenResponseJson>
    suspend fun getSPLTokenBalance(walletAddress: String, coinAddress: String): String?
    suspend fun getTokenAssociatedAccountByOwner(
        walletAddress: String,
        mintAddress: String
    ): Pair<String?, Boolean>

    suspend fun getFeeForMessage(message: String): BigInteger

    suspend fun checkStatus(txHash: String): EvmRpcResponseJson<SolanaSignatureStatusesResult>?
}

internal class SolanaApiImp @Inject constructor(
    private val json: Json,
    private val httpClient: HttpClient,
    private val splTokenSerializer: SplTokenResponseJsonSerializer,
) : SolanaApi {

    private val rpcEndpoint = "https://api.vultisig.com/solana/"
    private val splTokensInfoEndpoint = "https://api.solana.fm/v1/tokens"
    private val splTokensInfoEndpoint2 = "$JUPITER_URL/tokens/v2/search"
    private val jupiterTokensUrl = "$JUPITER_URL/tokens/v2/tag"
    override suspend fun getBalance(address: String): BigInteger {
        return try {
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
                BigInteger.ZERO
            }
            rpcResp.result?.value ?: error("getBalance error")
        } catch (e: Exception) {
            BigInteger.ZERO
        }
    }

    override suspend fun getMinimumBalanceForRentExemption(): BigInteger = try {
        httpClient.postRpc<SolanaMinimumBalanceForRentExemptionJson>(
            rpcEndpoint,
            "getMinimumBalanceForRentExemption",
            params = buildJsonArray {
                add(DATA_LENGTH_MINIMUM_BALANCE_FOR_RENT_EXEMPTION)
            },
        ).result
    } catch (e: Exception) {
        Timber.e("Error getting minimum balance for rent exemption: ${e.message}")
        BigInteger.ZERO
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

    @Deprecated("Perform proper calculation in fee service once decouple from helper")
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
            result.error?.let { error ->
                Timber.tag("SolanaApiImp").d("Error broadcasting transaction: $responseRawString")
                error(error["message"].toString())
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

    override suspend fun getSPLTokensInfo2(tokens: List<String>) = coroutineScope {
        tokens.map { token ->
            async {
                try {
                    httpClient.get(splTokensInfoEndpoint2) {
                        parameter("query", token)
                    }.body<List<SplTokenInfo>>().firstOrNull()
                } catch (e: Exception) {
                    Timber.tag("SolanaApiImp")
                        .e("Error getting spl token for $token message : ${e.message}")
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    override suspend fun getJupiterTokens(): List<JupiterTokenResponseJson> =
        httpClient.get(jupiterTokensUrl) {
            parameter(
                "query",
                "verified"
            )
        }.body()

    override suspend fun getSPLTokens(walletAddress: String): List<SplResponseAccountJson>? =
        coroutineScope {
            try {
                val payload = getSplRpcPayload(walletAddress, PROGRAM_ID_SPL_REQUEST_PARAM)
                val payloadToken2022 = getSplRpcPayload(walletAddress, TOKEN_PROGRAM_ID_2022)

                val response = async {
                    httpClient.post(rpcEndpoint) {
                        setBody(payload)
                    }
                }

                val responseToken2022 = async {
                    httpClient.post(rpcEndpoint) {
                        setBody(payloadToken2022)
                    }
                }

                listOf(response, responseToken2022)
                    .awaitAll()
                    .mapNotNull { it.body<SplResponseJson>().result?.accounts }
                    .flatten()

            } catch (e: Exception) {
                Timber.e(e)
                null
            }
        }


    private fun getSplRpcPayload(address: String, programId: String) = RpcPayload(
        jsonrpc = "2.0",
        method = "getTokenAccountsByOwner",
        params = buildJsonArray {
            add(address)
            addJsonObject {
                put(
                    "programId",
                    programId
                )
            }
            addJsonObject {
                put(
                    "encoding",
                    ENCODING_SPL_REQUEST_PARAM
                )
            }
        },
        id = 1,
    )


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
            if (value.value.isEmpty()) {
                return null
            }
            return value.value[0].account.data.parsed.info.tokenAmount.amount
        } catch (e: Exception) {
            Timber.e(e)
            return null
        }
    }

    override suspend fun getTokenAssociatedAccountByOwner(
        walletAddress: String,
        mintAddress: String,
    ): Pair<String?, Boolean> {
        try {
            val response = httpClient.postRpc<SplAmountRpcResponseJson>(
                url = rpcEndpoint,
                method = "getTokenAccountsByOwner",
                params = buildJsonArray {
                    add(walletAddress)
                    addJsonObject {
                        put("mint", mintAddress)
                    }
                    addJsonObject {
                        put("encoding", ENCODING_SPL_REQUEST_PARAM)
                    }
                }
            )
            if (response.error != null) {
                Timber.d("getTokenAssociatedAccountByOwner error: ${response.error}")
                return Pair(null, false)
            }
            val value = response.value ?: error("getTokenAssociatedAccountByOwner error")
            return Pair(
                value.value[0].pubKey,
                value.value[0].account.owner == TOKEN_PROGRAM_ID_2022
            )
        } catch (e: Exception) {
            Timber.e(e)
            return Pair(null, false)
        }
    }

    override suspend fun getFeeForMessage(message: String): BigInteger {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "getFeeForMessage",
            params = buildJsonArray {
                add(message)
                addJsonObject {
                    put("commitment", "confirmed")
                }
            },
            id = 1,
        )

        val response = httpClient.post(rpcEndpoint) {
            setBody(payload)
        }

        val rpcResp = response.bodyOrThrow<SolanaFeeForMessageResponse>()

        return rpcResp.result?.value ?: error("Error fetching getFeeForMessage")
    }

    override suspend fun checkStatus(txHash: String): EvmRpcResponseJson<SolanaSignatureStatusesResult>? {
        try {
            val params = buildJsonArray {
                addJsonArray {
                    add(JsonPrimitive(txHash))
                }
                buildJsonObject {
                    put(
                        "searchTransactionHistory",
                        true
                    )
                }
            }

            val response: EvmRpcResponseJson<SolanaSignatureStatusesResult> = httpClient.postRpc(
                url = rpcEndpoint,
                method = "getSignatureStatuses",
                params = params,
            )

            return response
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if ("403" in msg || msg.contains(
                    "Forbidden",
                    ignoreCase = true
                ) || msg.contains(
                    "forbidden",
                    ignoreCase = true
                )
            ) {
                Timber.tag("SolanaApiImp").d("Forbidden (403) when checking tx status: $txHash")
                return null
            }
            throw e
        }
    }

    companion object {
        private const val PROGRAM_ID_SPL_REQUEST_PARAM =
            "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        private const val TOKEN_PROGRAM_ID_2022 =
            "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"
        private const val ENCODING_SPL_REQUEST_PARAM = "jsonParsed"
        private const val DATA_LENGTH_MINIMUM_BALANCE_FOR_RENT_EXEMPTION = 165
    }
}