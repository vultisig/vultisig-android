package com.vultisig.wallet.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.vultisig.wallet.common.Numeric
import com.vultisig.wallet.common.toKeccak256
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import timber.log.Timber
import java.math.BigInteger
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
        if (coin.isNativeToken) {
            return getETHBalance(coin.address)
        }
        return getERC20Balance(coin.address, coin.contractAddress)
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
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "eth_call",
            params = listOf(
                mapOf(
                    "to" to contractAddress,
                    "data" to "0xdd62ed3e000000000000000000000000${owner.removePrefix("0x")}000000000000000000000000${
                        spender.removePrefix(
                            "0x"
                        )
                    }"
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
                message.contains("transaction already exists")
            ) {
                // even the server returns an error , but this still consider as success
                return Numeric.hexStringToByteArray(signedTransaction).toKeccak256()
            } else {
                throw Exception(responseBody)
            }
        }
        return jsonObject.get("result").asString
    }
}