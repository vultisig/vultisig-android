package com.vultisig.wallet.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.vultisig.wallet.common.Numeric
import com.vultisig.wallet.common.toKeccak256
import com.vultisig.wallet.data.models.CustomTokenResponse
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import timber.log.Timber
import java.math.BigInteger
import java.net.SocketTimeoutException
import javax.inject.Inject

data class RpcPayload(
    @SerializedName("jsonrpc")
    val jsonrpc: String,
    @SerializedName("method")
    val method: String,
    @SerializedName("params")
    val params: List<Any>,
    @SerializedName("id")
    val id: Int,
)

data class RpcResponse(
    @SerializedName("jsonrpc")
    val jsonrpc: String,
    @SerializedName("id")
    val id: Int,
    @SerializedName("result")
    val result: String?,
    @SerializedName("error")
    val error: RpcError?,
)

data class RpcError(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
)

internal interface EvmApi {
    suspend fun getBalance(coin: Coin): BigInteger
    suspend fun getAllowance(contractAddress: String, owner: String, spender: String): BigInteger
    suspend fun sendTransaction(signedTransaction: String): String
    suspend fun getMaxPriorityFeePerGas(): BigInteger
    suspend fun getNonce(address: String): BigInteger
    suspend fun getGasPrice(): BigInteger
    suspend fun findCustomToken(contractAddress: String): List<CustomTokenResponse>
}

internal interface EvmApiFactory {
    fun createEvmApi(chain: Chain): EvmApi
}

internal class EvmApiFactoryImp @Inject constructor(
    private val gson: Gson,
    private val httpClient: HttpClient,
) : EvmApiFactory {
    override fun createEvmApi(chain: Chain): EvmApi {
        return when (chain) {
            Chain.ethereum -> EvmApiImp(gson, httpClient, "https://ethereum-rpc.publicnode.com")
            Chain.bscChain -> EvmApiImp(gson, httpClient, "https://bsc-rpc.publicnode.com")
            Chain.avalanche -> EvmApiImp(
                gson,
                httpClient,
                "https://avalanche-c-chain-rpc.publicnode.com"
            )

            Chain.polygon -> EvmApiImp(gson, httpClient, "https://polygon-bor-rpc.publicnode.com")
            Chain.optimism -> EvmApiImp(gson, httpClient, "https://optimism-rpc.publicnode.com")
            Chain.cronosChain -> EvmApiImp(
                gson,
                httpClient,
                "https://cronos-evm-rpc.publicnode.com"
            )

            Chain.blast -> EvmApiImp(gson, httpClient, "https://rpc.ankr.com/blast")
            Chain.base -> EvmApiImp(gson, httpClient, "https://base-rpc.publicnode.com")
            Chain.arbitrum -> EvmApiImp(gson, httpClient, "https://arbitrum-one-rpc.publicnode.com")
            else -> throw IllegalArgumentException("Unsupported chain ${chain}")
        }
    }
}

internal class EvmApiImp(
    private val gson: Gson,
    private val httpClient: HttpClient,
    private val rpcEndpoint: String,
) : EvmApi {
    private fun getRPCEndpoint(): String = rpcEndpoint
    override suspend fun getBalance(coin: Coin): BigInteger {
        return try {
            if (coin.isNativeToken)
                getETHBalance(coin.address)
            else
                getERC20Balance(coin.address, coin.contractAddress)
        } catch (e: SocketTimeoutException) {
            Timber.d("request time out, message: ${e.message}")
            BigInteger.ZERO
        }
    }

    private suspend fun getERC20Balance(address: String, contractAddress: String): BigInteger {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "eth_call",
            params = listOf(
                mapOf(
                    "to" to contractAddress,
                    "data" to "0x70a08231000000000000000000000000${address.removePrefix("0x")}"
                ),
                "latest"
            ),
            id = 1,
        )
        val response = httpClient.post(getRPCEndpoint()) {
            header("Content-Type", "application/json")
            setBody(gson.toJson(payload))
        }
        val responseContent = response.bodyAsText()
        Timber.d(responseContent)
        val rpcResp = gson.fromJson(responseContent, RpcResponse::class.java)
        if (rpcResp.error != null) {
            Timber.d("get erc20 balance,contract: $contractAddress,address: $address error: ${rpcResp.error.message}")
            return BigInteger.ZERO
        }
        return BigInteger(rpcResp.result?.removePrefix("0x") ?: "0", 16)
    }

    private suspend fun getETHBalance(address: String): BigInteger {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "eth_getBalance",
            params = listOf(address, "latest"),
            id = 1,
        )
        val response = httpClient.post(getRPCEndpoint()) {
            header("Content-Type", "application/json")
            setBody(gson.toJson(payload))
        }
        val responseContent = response.bodyAsText()
        Timber.d(responseContent)
        val rpcResp = gson.fromJson(responseContent, RpcResponse::class.java)
        if (rpcResp.error != null) {
            Timber.d("get balance ,address: $address error: ${rpcResp.error.message}")
            return BigInteger.ZERO
        }
        return BigInteger(rpcResp.result?.removePrefix("0x") ?: "0", 16)
    }

    override suspend fun getNonce(address: String): BigInteger {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "eth_getTransactionCount",
            params = listOf(address, "latest"),
            id = 1,
        )
        val response = httpClient.post(getRPCEndpoint()) {
            header("Content-Type", "application/json")
            setBody(gson.toJson(payload))
        }
        val responseContent = response.bodyAsText()
        Timber.d(responseContent)
        val rpcResp = gson.fromJson(responseContent, RpcResponse::class.java)
        if (rpcResp.error != null) {
            Timber.d("get nonce ,address: $address error: ${rpcResp.error.message}")
            return BigInteger.ZERO
        }

        return BigInteger(rpcResp.result?.removePrefix("0x") ?: "0", 16)
    }

    override suspend fun getGasPrice(): BigInteger {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "eth_gasPrice",
            params = emptyList(),
            id = 1,
        )
        val response = httpClient.post(getRPCEndpoint()) {
            header("Content-Type", "application/json")
            setBody(gson.toJson(payload))
        }
        val responseContent = response.bodyAsText()
        val rpcResp = gson.fromJson(responseContent, RpcResponse::class.java)
        if (rpcResp.error != null) {
            Timber.d("get gas price error: ${rpcResp.error.message}")
            return BigInteger.ZERO
        }
        return BigInteger(rpcResp.result?.removePrefix("0x") ?: "0", 16)
    }

    override suspend fun getMaxPriorityFeePerGas(): BigInteger {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "eth_maxPriorityFeePerGas",
            params = emptyList(),
            id = 1,
        )
        val response = httpClient.post(getRPCEndpoint()) {
            header("Content-Type", "application/json")
            setBody(gson.toJson(payload))
        }
        val responseContent = response.bodyAsText()
        val rpcResp = gson.fromJson(responseContent, RpcResponse::class.java)
        if (rpcResp.error != null) {
            Timber.d("get max priority fee per gas , error: ${rpcResp.error.message}")
            return BigInteger.ZERO
        }

        return BigInteger(rpcResp.result?.removePrefix("0x") ?: "0", 16)
    }

    override suspend fun getAllowance(
        contractAddress: String,
        owner: String,
        spender: String,
    ): BigInteger {
        val paddedOwner = owner.removePrefix("0x").padStart(64, '0')
        val paddedSpender = spender.removePrefix("0x").padStart(64, '0')
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "eth_call",
            params = listOf(
                mapOf(
                    "to" to contractAddress,
                    "data" to "0xdd62ed3e$paddedOwner$paddedSpender"
                ),
                "latest"
            ),
            id = 1,
        )
        val response = httpClient.post(getRPCEndpoint()) {
            header("Content-Type", "application/json")
            setBody(gson.toJson(payload))
        }
        val responseContent = response.bodyAsText()
        val rpcResp = gson.fromJson(responseContent, RpcResponse::class.java)
        if (rpcResp.error != null) {
            Timber.d("get allowance,contract address: $contractAddress,owner: $owner,spender: $spender, error: ${rpcResp.error.message}")
            return BigInteger.ZERO
        }
        return BigInteger(rpcResp.result?.removePrefix("0x") ?: "0", 16)
    }

    override suspend fun sendTransaction(signedTransaction: String): String {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "eth_sendRawTransaction",
            params = listOf("0x$signedTransaction"),
            id = 1,
        )
        Timber.d("send transaction: $signedTransaction")
        val response = httpClient.post(getRPCEndpoint()) {
            header("Content-Type", "application/json")
            setBody(gson.toJson(payload))
        }
        val responseBody = response.bodyAsText()
        Timber.d("broadcast response: $responseBody")
        val jsonObject = gson.fromJson(responseBody, JsonObject::class.java)
        if (jsonObject.has("error")) {
            val message = jsonObject.getAsJsonObject("error").get("message").asString
            if (message.contains("known") ||
                message.contains("already known") ||
                message.contains("Transaction is temporarily banned") ||
                message.contains("nonce too low: next nonce") ||
                message.contains("transaction already exists") ||
                message.contains("nonce too low: address") || // this message happens on layer 2
                message.contains("tx already in mempool")
            ) {
                // even the server returns an error , but this still consider as success
                return Numeric.hexStringToByteArray(signedTransaction).toKeccak256()
            } else {
                throw Exception(responseBody)
            }
        }
        return jsonObject.get("result").asString
    }

    override suspend fun findCustomToken(contractAddress: String): List<CustomTokenResponse> {
        val (payload1, payload2) = generateCustomTokenPayload(contractAddress)
        return try {
            val response = httpClient.post(getRPCEndpoint()) {
                header(
                    "Content-Type",
                    "application/json"
                )
                setBody(
                    gson.toJson(
                        listOf(
                            payload1,
                            payload2
                        )
                    )
                )
            }
            val responseContent = response.bodyAsText()
            val responseList = gson.fromJson<List<RpcResponse>?>(
                responseContent,
                object : TypeToken<List<RpcResponse>>() {}.type
            )
            responseList.map {
                CustomTokenResponse(
                    id = it.id,
                    result = it.result,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun generateCustomTokenPayload(
        contractAddress: String
    ): Pair<RpcPayload, RpcPayload> {
        val payload1 = RpcPayload(
            jsonrpc = "2.0",
            method = "eth_call",
            params = listOf(
                mapOf(
                    "to" to contractAddress,
                    "data" to CUSTOM_TOKEN_REQUEST_TICKER_DATA
                ),
                "latest"
            ),
            id = CUSTOM_TOKEN_RESPONSE_TICKER_ID,
        )
        val payload2 = RpcPayload(
            jsonrpc = "2.0",
            method = "eth_call",
            params = listOf(
                mapOf(
                    "to" to contractAddress,
                    "data" to CUSTOM_TOKEN_REQUEST_DECIMAL_DATA
                ),
                "latest"
            ),
            id = CUSTOM_TOKEN_RESPONSE_DECIMAL_ID_,
        )
        return Pair(
            payload1,
            payload2
        )
    }
    companion object {
        private const val CUSTOM_TOKEN_RESPONSE_TICKER_ID = 2
        private const val CUSTOM_TOKEN_RESPONSE_DECIMAL_ID_= 3
        private const val CUSTOM_TOKEN_REQUEST_TICKER_DATA = "0x95d89b41"
        private const val CUSTOM_TOKEN_REQUEST_DECIMAL_DATA = "0x313ce567"
    }
}

