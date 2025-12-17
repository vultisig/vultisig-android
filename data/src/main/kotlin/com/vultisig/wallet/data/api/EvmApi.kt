package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.CustomTokenResponse
import com.vultisig.wallet.data.api.models.EvmBaseFeeJson
import com.vultisig.wallet.data.api.models.EvmFeeHistoryResponseJson
import com.vultisig.wallet.data.api.models.EvmRpcResponseJson
import com.vultisig.wallet.data.api.models.RpcPayload
import com.vultisig.wallet.data.api.models.RpcResponse
import com.vultisig.wallet.data.api.models.RpcResponseJson
import com.vultisig.wallet.data.api.models.SendTransactionJson
import com.vultisig.wallet.data.api.models.VultisigBalanceJson
import com.vultisig.wallet.data.api.models.ZkGasFee
import com.vultisig.wallet.data.api.utils.postRpc
import com.vultisig.wallet.data.chains.helpers.EthereumFunction
import com.vultisig.wallet.data.common.convertToBigIntegerOrZero
import com.vultisig.wallet.data.common.stripHexPrefix
import com.vultisig.wallet.data.common.toKeccak256
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.utils.Numeric
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import timber.log.Timber
import java.math.BigInteger
import java.net.SocketTimeoutException
import javax.inject.Inject

interface EvmApi {
    suspend fun getBalance(coin: Coin): BigInteger
    suspend fun getAllowance(contractAddress: String, owner: String, spender: String): BigInteger
    suspend fun sendTransaction(signedTransaction: String): String
    suspend fun getMaxPriorityFeePerGas(): BigInteger
    suspend fun getNonce(address: String): BigInteger
    suspend fun getGasPrice(): BigInteger
    suspend fun findCustomToken(contractAddress: String): List<CustomTokenResponse>
    suspend fun resolveENS(namehash: String): String
    suspend fun getBaseFee(): BigInteger
    suspend fun getFeeHistory(): List<BigInteger>
    suspend fun zkEstimateFee(srcAddress: String, dstAddress: String, data: String): ZkGasFee
    suspend fun estimateGasForEthTransaction(
        senderAddress: String,
        recipientAddress: String,
        value: BigInteger,
        memo: String?,
    ): BigInteger
    suspend fun estimateGasForERC20Transfer(
        senderAddress: String,
        contractAddress: String,
        recipientAddress: String,
        value: BigInteger,
    ): BigInteger
    suspend fun getBalances(
        address: String,
    ): VultisigBalanceJson

    suspend fun getERC20Balance(address: String, contractAddress: String): BigInteger
}

interface EvmApiFactory {
    fun createEvmApi(chain: Chain): EvmApi
}

class EvmApiFactoryImp @Inject constructor(
    private val httpClient: HttpClient,
) : EvmApiFactory {
    override fun createEvmApi(chain: Chain): EvmApi {
        return when (chain) {
            Chain.Ethereum -> EvmApiImp(
                httpClient,
                "https://api.vultisig.com/eth/"
            )

            Chain.BscChain -> EvmApiImp(
                httpClient,
                "https://api.vultisig.com/bnb/"
            )

            Chain.Avalanche -> EvmApiImp(
                httpClient,
                "https://api.vultisig.com/avax/"
            )

            Chain.Polygon -> EvmApiImp(
                httpClient,
                "https://api.vultisig.com/polygon/"
            )

            Chain.Optimism -> EvmApiImp(
                httpClient,
                "https://api.vultisig.com/opt/"
            )

            Chain.CronosChain -> EvmApiImp(
                httpClient,
                "https://cronos-evm-rpc.publicnode.com"
            )

            Chain.Blast -> EvmApiImp(
                httpClient,
                "https://api.vultisig.com/blast/"
            )

            Chain.Base -> EvmApiImp(
                httpClient,
                "https://api.vultisig.com/base/"
            )

            Chain.Arbitrum -> EvmApiImp(
                httpClient,
                "https://api.vultisig.com/arb/"
            )

            Chain.ZkSync -> EvmApiImp(
                httpClient,
                "https://api.vultisig.com/zksync/"
            )
            Chain.Mantle -> EvmApiImp(
                httpClient,
                "https://api.vultisig.com/mantle/"
            )
            Chain.Sei -> EvmApiImp(
                httpClient,
                "https://evm-rpc.sei-apis.com/"
            )
            Chain.Hyperliquid -> EvmApiImp(
                httpClient,
                "https://api.vultisig.com/hyperevm/"
            )

            else -> throw IllegalArgumentException("Unsupported chain $chain")
        }
    }
}

class EvmApiImp(
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

    override suspend fun getERC20Balance(address: String, contractAddress: String): BigInteger {
        val rpcResp = fetch<RpcResponse>(
            "eth_call",
            buildJsonArray {
                addJsonObject {
                    put("to", contractAddress)
                    put(
                        "data",
                        "0x70a08231000000000000000000000000${address.removePrefix("0x")}"
                    )
                }
                add("latest")
            }
        )
        if (rpcResp.error != null) {
            Timber.d("get erc20 balance,contract: $contractAddress,address: $address error: ${rpcResp.error.message}")
            return BigInteger.ZERO
        }
        return rpcResp.result?.let {
            try {
                EthereumFunction.balanceErc20Decoder(it)
            } catch (e: Exception) {
                Timber.d("get erc20 balance,contract: $contractAddress,address: $address error: $e")
                BigInteger.ZERO
            }
        } ?: run {
            Timber.d("get erc20 balance,contract: $contractAddress,address: $address error: response is null")
            BigInteger.ZERO
        }
    }

    private suspend fun getETHBalance(address: String): BigInteger {
        val rpcResp = fetch<RpcResponse>(
            "eth_getBalance",
            buildJsonArray {
                add(address)
                add("latest")
            }
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
            buildJsonArray {
                add(address)
                add("latest")
            }
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
            buildJsonArray { }
        )
        if (rpcResp.error != null) {
            Timber.d("get gas price error: ${rpcResp.error.message}")
            return BigInteger.ZERO
        }
        return rpcResp.result.convertToBigIntegerOrZero()
    }

    override suspend fun estimateGasForEthTransaction(
        senderAddress: String,
        recipientAddress: String,
        value: BigInteger,
        memo: String?,
    ): BigInteger {
        val memoDataHex = "0xffffffff".toByteArray()
            .joinToString(separator = "") { byte -> String.format("%02x", byte) }

        val rpcResp = fetch<RpcResponse>(
            "eth_estimateGas",
            buildJsonArray {
                addJsonObject {
                    put("from", senderAddress)
                    put("to", recipientAddress)
                    put("value", "0x${value.toString(16)}")
                    put("data", "0x$memoDataHex")
                }
            }
        )
        if (rpcResp.error != null) {
            Timber.d("get max priority fee per gas , error: ${rpcResp.error.message}")
            return BigInteger.ZERO
        }

        return rpcResp.result.convertToBigIntegerOrZero()
    }

    suspend fun estimateGasForCallDataTransfer(
        senderAddress: String,
        recipientAddress: String,
        value: String,
        callData: String,
    ): BigInteger {
        val nonce = getNonce(senderAddress)
        val gasPrice = getGasPrice()
        val rpcResp = fetch<RpcResponse>(
            "eth_estimateGas",
            buildJsonArray {
                addJsonObject {
                    put("from", senderAddress)
                    put("to", recipientAddress)
                    put("value", value)
                    put("data", callData)
                    put("nonce", "0x${nonce.toString(16)}")
                    put("gasPrice", "0x${gasPrice.toString(16)}")
                }
            }
        )
        if (rpcResp.error != null) {
            Timber.d("get gas limit, error: ${rpcResp.error.message}")
            return BigInteger.ZERO
        }

        return rpcResp.result.convertToBigIntegerOrZero()
    }

    override suspend fun estimateGasForERC20Transfer(
        senderAddress: String,
        contractAddress: String,
        recipientAddress: String,
        value: BigInteger,
    ): BigInteger {
        val data = constructERC20TransferData(recipientAddress, value)

        return estimateGasForCallDataTransfer(
            senderAddress = senderAddress,
            recipientAddress = contractAddress,
            value = "0x0",
            callData = data,
        )
    }

    private fun constructERC20TransferData(recipientAddress: String, value: BigInteger): String {
        val methodId = "a9059cbb"
        val strippedRecipientAddress = recipientAddress.stripHexPrefix()
        val paddedAddress = strippedRecipientAddress.padStart(64, '0')
        val valueHex = value.toString(16)
        val paddedValue = valueHex.padStart(64, '0')
        return "0x$methodId$paddedAddress$paddedValue"
    }

    override suspend fun getMaxPriorityFeePerGas(): BigInteger {
        val rpcResp = fetch<RpcResponse>(
            "eth_maxPriorityFeePerGas",
            buildJsonArray { }
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
            buildJsonArray {
                addJsonObject {
                    put("to", contractAddress)
                    put("data", "0xdd62ed3e$paddedOwner$paddedSpender")
                }
                add("latest")
            }
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
            params = buildJsonArray {
                add("0x$signedTransaction")
            },
        )
        Timber.d("send transaction: $signedTransaction")
        val response = http.post(rpcUrl) {
            setBody(payload)
        }
        val responseBody = response.bodyAsText()
        Timber.d("broadcast response: $responseBody")
        val jsonObject = response.body<SendTransactionJson>()
        if (jsonObject.error != null) {
            val message = jsonObject.error.message
            if (message.contains("known") ||
                message.contains("already known") ||
                message.contains("Transaction is temporarily banned") ||
                message.contains("nonce too low: next nonce") ||
                message.contains("nonce too low. allowed nonce range:") ||
                message.contains("transaction already exists") ||
                message.contains("nonce too low: address") || // this message happens on layer 2
                message.contains("tx already in mempool") ||
                message.contains("existing tx") ||
                message.contains("tx already exists in cache") ||
                message.contains("code=10055")
            ) {
                // even the server returns an error , but this still consider as success
                return Numeric.hexStringToByteArray(signedTransaction).toKeccak256()
            } else {
                throw Exception(responseBody)
            }
        }
        return jsonObject.result ?: error("send transaction failed")
    }

    override suspend fun findCustomToken(contractAddress: String): List<CustomTokenResponse> {
        val (payload1, payload2) = generateCustomTokenPayload(contractAddress)
        return try {
            val response = http.post(rpcUrl) {
                setBody(
                    listOf(
                        payload1,
                        payload2
                    )
                )
            }
            val responseList = response.body<List<RpcResponse>>()
            responseList.map {
                CustomTokenResponse(
                    id = it.id,
                    result = it.result,
                )
            }
        } catch (e: Exception) {
            Timber.d("find custom token error: ${e.message}")
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

    override suspend fun getBaseFee(): BigInteger {
        val response = fetch<EvmRpcResponseJson<EvmBaseFeeJson>>(
            method = "eth_getBlockByNumber",
            params = buildJsonArray {
                add("latest")
                add(false)
            }
        )
        return response.result.baseFeePerGas.convertToBigIntegerOrZero()
    }

    override suspend fun getFeeHistory(): List<BigInteger> {
        val response = fetch<EvmFeeHistoryResponseJson>(
            method = "eth_feeHistory",
            params = buildJsonArray {
                add(10)
                add("latest")
                addJsonArray {
                    add(5)
                }
            }
        )

        val rewards = response.result.reward

        return rewards.mapNotNull { it.firstOrNull() }
            .map { it.convertToBigIntegerOrZero() }
            .sorted()
    }

    override suspend fun zkEstimateFee(
        srcAddress: String,
        dstAddress: String,
        data: String,
    ): ZkGasFee {
        val response = fetch<RpcResponseJson>(
            "zks_estimateFee",
            buildJsonArray {
                addJsonObject {
                    put("from", srcAddress)
                    put("to", dstAddress)
                    put("data", data)
                }
            }
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
                gasLimit = resultJson.gasLimit
                    .convertToBigIntegerOrZero(),
                gasPerPubdataLimit = resultJson.gasPerPubdataLimit
                    .convertToBigIntegerOrZero(),
                maxFeePerGas = resultJson.maxFeePerGas
                    .convertToBigIntegerOrZero(),
                maxPriorityFeePerGas = resultJson.maxPriorityFeePerGas
                    .convertToBigIntegerOrZero(),
            )
        }
    }

    override suspend fun getBalances(
        address: String,
    ): VultisigBalanceJson = http.postRpc(
        rpcUrl,
        "alchemy_getTokenBalances",
        params = buildJsonArray {
            add(address)
        }
    )

    private suspend inline fun <reified T> fetch(
        method: String,
        params: JsonArray,
        id: Int = 1,
    ): T = http.post(rpcUrl) {
        setBody(
            RpcPayload(
                method = method,
                params = params,
                id = id
            )
        )
    }.body()

    private fun generateCustomTokenPayload(
        contractAddress: String,
    ): Pair<RpcPayload, RpcPayload> {
        val payload1 = RpcPayload(
            method = "eth_call",
            params = buildJsonArray {
                addJsonObject {
                    put("to", contractAddress)
                    put("data", CUSTOM_TOKEN_REQUEST_TICKER_DATA)
                }
                add("latest")
            },
            id = CUSTOM_TOKEN_RESPONSE_TICKER_ID,
        )
        val payload2 = RpcPayload(
            method = "eth_call",
            params = buildJsonArray {
                addJsonObject {
                    put("to", contractAddress)
                    put("data", CUSTOM_TOKEN_REQUEST_DECIMAL_DATA)
                }
                add("latest")
            },
            id = CUSTOM_TOKEN_RESPONSE_DECIMAL_ID_,
        )
        return Pair(
            payload1,
            payload2
        )
    }

    private suspend fun fetchEns(params: Map<String, String>): String {
        val rpcResp = fetch<RpcResponse>(
            method = "eth_call",
            params =
            buildJsonArray {
                addJsonObject {
                    params.forEach { (key, value) ->
                        put(key, value)
                    }
                }
                add("latest")
            }
        )
        val data = rpcResp.result?.stripHexPrefix()?.let { Numeric.hexStringToByteArray(it) }
        return Numeric.toHexString(data?.copyOfRange(data.size - 20, data.size))
    }

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

