package com.vultisig.wallet.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.vultisig.wallet.common.Numeric
import com.vultisig.wallet.common.stripHexPrefix
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

internal data class RpcPayload(
    @SerializedName("method")
    val method: String,
    @SerializedName("params")
    val params: List<Any>,

    @SerializedName("jsonrpc")
    val jsonrpc: String = "2.0",
    @SerializedName("id")
    val id: Int = 1,
)

internal data class RpcResponse(
    @SerializedName("id")
    val id: Int,
    @SerializedName("result")
    val result: String?,
    @SerializedName("error")
    val error: RpcError?,
)

internal data class RpcResponseJson(
    @SerializedName("id")
    val id: Int,
    @SerializedName("result")
    val result: JsonObject?,
    @SerializedName("error")
    val error: RpcError?,
)

data class RpcError(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
)

internal data class ZkGasFee(
    val gasLimit: BigInteger,
    val gasPerPubdataLimit: BigInteger,
    val maxFeePerGas: BigInteger,
    val maxPriorityFeePerGas: BigInteger,
)

internal interface EvmApi {
    suspend fun getBalance(coin: Coin): BigInteger
    suspend fun getAllowance(contractAddress: String, owner: String, spender: String): BigInteger
    suspend fun sendTransaction(signedTransaction: String): String
    suspend fun getMaxPriorityFeePerGas(): BigInteger
    suspend fun getNonce(address: String): BigInteger
    suspend fun getGasPrice(): BigInteger
    suspend fun findCustomToken(contractAddress: String): List<CustomTokenResponse>
    suspend fun resolveENS(namehash: String): String
    suspend fun zkEstimateFee(srcAddress: String, dstAddress: String, data: String): ZkGasFee
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
            Chain.Ethereum -> EvmApiImp(gson, httpClient, "https://ethereum-rpc.publicnode.com")
            Chain.BscChain -> EvmApiImp(gson, httpClient, "https://bsc-rpc.publicnode.com")
            Chain.Avalanche -> EvmApiImp(
                gson,
                httpClient,
                "https://avalanche-c-chain-rpc.publicnode.com"
            )

            Chain.Polygon -> EvmApiImp(gson, httpClient, "https://polygon-bor-rpc.publicnode.com")
            Chain.Optimism -> EvmApiImp(gson, httpClient, "https://optimism-rpc.publicnode.com")
            Chain.CronosChain -> EvmApiImp(
                gson,
                httpClient,
                "https://cronos-evm-rpc.publicnode.com"
            )

            Chain.Blast -> EvmApiImp(gson, httpClient, "https://rpc.ankr.com/blast")
            Chain.Base -> EvmApiImp(gson, httpClient, "https://base-rpc.publicnode.com")
            Chain.Arbitrum -> EvmApiImp(gson, httpClient, "https://arbitrum-one-rpc.publicnode.com")
            Chain.ZkSync -> EvmApiImp(gson, httpClient, "https://mainnet.era.zksync.io")
            else -> throw IllegalArgumentException("Unsupported chain $chain")
        }
    }
}

internal class EvmApiImp(
    private val gson: Gson,
    private val http: HttpClient,
    private val rpcUrl: String,
) : EvmApi {

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
        val rpcResp = fetch<RpcResponse>(
            "eth_call",
            listOf(
                mapOf(
                    "to" to contractAddress,
                    "data" to "0x70a08231000000000000000000000000${address.removePrefix("0x")}"
                ),
                "latest"
            )
        )
        if (rpcResp.error != null) {
            Timber.d("get erc20 balance,contract: $contractAddress,address: $address error: ${rpcResp.error.message}")
            return BigInteger.ZERO
        }
        return rpcResp.result.convertToBigIntegerOrZero()
    }

    private suspend fun getETHBalance(address: String): BigInteger {
        val rpcResp = fetch<RpcResponse>(
            "eth_getBalance",
            listOf(address, "latest")
        )
        if (rpcResp.error != null) {
            Timber.d("get balance ,address: $address error: ${rpcResp.error.message}")
            return BigInteger.ZERO
        }
        return rpcResp.result.convertToBigIntegerOrZero()
    }

    override suspend fun getNonce(address: String): BigInteger {
        val rpcResp = fetch<RpcResponse>(
            "eth_getTransactionCount",
            listOf(address, "latest")
        )
        if (rpcResp.error != null) {
            Timber.d("get nonce ,address: $address error: ${rpcResp.error.message}")
            return BigInteger.ZERO
        }

        return BigInteger(rpcResp.result?.removePrefix("0x") ?: "0", 16)
    }

    override suspend fun getGasPrice(): BigInteger {
        val rpcResp = fetch<RpcResponse>(
            "eth_gasPrice",
            emptyList()
        )
        if (rpcResp.error != null) {
            Timber.d("get gas price error: ${rpcResp.error.message}")
            return BigInteger.ZERO
        }
        return rpcResp.result.convertToBigIntegerOrZero()
    }

    override suspend fun getMaxPriorityFeePerGas(): BigInteger {
        val rpcResp = fetch<RpcResponse>(
            "eth_maxPriorityFeePerGas",
            emptyList()
        )
        if (rpcResp.error != null) {
            Timber.d("get max priority fee per gas , error: ${rpcResp.error.message}")
            return BigInteger.ZERO
        }

        return rpcResp.result.convertToBigIntegerOrZero()
    }

    override suspend fun getAllowance(
        contractAddress: String,
        owner: String,
        spender: String,
    ): BigInteger {
        val paddedOwner = owner.removePrefix("0x").padStart(64, '0')
        val paddedSpender = spender.removePrefix("0x").padStart(64, '0')
        val rpcResp = fetch<RpcResponse>(
            "eth_call",
            listOf(
                mapOf(
                    "to" to contractAddress,
                    "data" to "0xdd62ed3e$paddedOwner$paddedSpender"
                ),
                "latest"
            )
        )
        if (rpcResp.error != null) {
            Timber.d("get allowance,contract address: $contractAddress,owner: $owner,spender: $spender, error: ${rpcResp.error.message}")
            return BigInteger.ZERO
        }
        return rpcResp.result.convertToBigIntegerOrZero()
    }

    override suspend fun sendTransaction(signedTransaction: String): String {
        val payload = RpcPayload(
            method = "eth_sendRawTransaction",
            params = listOf("0x$signedTransaction"),
        )
        Timber.d("send transaction: $signedTransaction")
        val response = http.post(rpcUrl) {
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
            val response = http.post(rpcUrl) {
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

    override suspend fun resolveENS(namehash: String): String {

        val resolverAddress = fetchEns(
            mapOf(
                "to" to ENS_REGISTRY_ADDRESS,
                "data" to "$FETCH_RESOLVER_PREFIX${namehash.stripHexPrefix()}"
            )
        )
        return fetchEns(
            mapOf(
                "to" to resolverAddress,
                "data" to "$FETCH_ADDRESS_PREFIX${namehash.stripHexPrefix()}"
            )
        )
    }

    override suspend fun zkEstimateFee(
        srcAddress: String,
        dstAddress: String,
        data: String
    ): ZkGasFee {
        val response = fetch<RpcResponseJson>(
            "zks_estimateFee", listOf(
                mapOf(
                    "from" to srcAddress,
                    "to" to dstAddress,
                    "data" to data
                )
            )
        )
        return if (response.error != null) {
            Timber.d(
                "zk estimate fee, srcAddress: $srcAddress,dstAddress: $dstAddress," +
                        "data: $data error: ${response.error.message}"
            )
            ZkGasFee(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO)
        } else {
            val resultJson = response.result ?: return ZkGasFee(
                BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO
            )

            ZkGasFee(
                gasLimit = resultJson.get("gas_limit").asString
                    .convertToBigIntegerOrZero(),
                gasPerPubdataLimit = resultJson.get("gas_per_pubdata_limit").asString
                    .convertToBigIntegerOrZero(),
                maxFeePerGas = resultJson.get("max_fee_per_gas").asString
                    .convertToBigIntegerOrZero(),
                maxPriorityFeePerGas = resultJson.get("max_priority_fee_per_gas").asString
                    .convertToBigIntegerOrZero(),
            )
        }
    }

    private suspend inline fun <reified T> fetch(
        method: String,
        params: List<Any>,
        id: Int = 1
    ): T {
        val response = http.post(rpcUrl) {
            setBody(
                gson.toJson(
                    RpcPayload(
                        method = method,
                        params = params,
                        id = id
                    )
                )
            )
        }
        val responseContent = response.bodyAsText()
        return gson.fromJson(responseContent, T::class.java)
    }

    private fun generateCustomTokenPayload(
        contractAddress: String
    ): Pair<RpcPayload, RpcPayload> {
        val payload1 = RpcPayload(
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

    private suspend fun fetchEns(params: Map<String,String>): String {
        val rpcResp = fetch<RpcResponse>(
            method = "eth_call",
            params = listOf(
                params,
                "latest"
            )
        )
        val data = rpcResp.result?.stripHexPrefix()?.let { Numeric.hexStringToByteArray(it) }
        return Numeric.toHexString(data?.copyOfRange(data.size - 20, data.size))
    }


    private fun String?.convertToBigIntegerOrZero(): BigInteger =
        BigInteger(this?.removePrefix("0x") ?: "0", 16)

    companion object {
        private const val CUSTOM_TOKEN_RESPONSE_TICKER_ID = 2
        private const val CUSTOM_TOKEN_RESPONSE_DECIMAL_ID_ = 3
        private const val CUSTOM_TOKEN_REQUEST_TICKER_DATA = "0x95d89b41"
        private const val CUSTOM_TOKEN_REQUEST_DECIMAL_DATA = "0x313ce567"
        private const val FETCH_RESOLVER_PREFIX = "0x0178b8bf"
        private const val FETCH_ADDRESS_PREFIX = "0x3b3b57de"
        private const val ENS_REGISTRY_ADDRESS = "0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e"
    }
}

