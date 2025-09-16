package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.PolkadotResponseJson
import com.vultisig.wallet.data.api.models.RpcPayload
import com.vultisig.wallet.data.api.models.cosmos.PolkadotBroadcastTransactionJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetBlockHashJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetBlockHeaderJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetNonceJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetRunTimeVersionJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject


interface PolkadotApi {
    suspend fun getBalance(address: String): BigInteger
    suspend fun getNonce(address: String): BigInteger
    suspend fun getBlockHash(isGenesis: Boolean = false): String
    suspend fun getGenesisBlockHash(): String
    suspend fun getRuntimeVersion(): Pair<BigInteger, BigInteger>
    suspend fun getBlockHeader(): BigInteger
    suspend fun broadcastTransaction(tx: String): String?
    suspend fun queryInfo(tx: String)
}

internal class PolkadotApiImp @Inject constructor(
    private val httpClient: HttpClient
) : PolkadotApi {
    private val polkadotApiUrl = "https://polkadot-rpc.publicnode.com"
    private val polkadotBalanceApiUrl = "https://polkadot.api.subscan.io/api/v2/scan/search"


    override suspend fun getBalance(address: String): BigInteger {
        try {
            val bodyMap = mapOf(
                "key" to address
            )
            val response = httpClient
                .post(polkadotBalanceApiUrl) {
                    setBody(bodyMap)
                }
            val rpcResp = response.body<PolkadotResponseJson>()
            val respCode = rpcResp.code
            if (respCode == 10004) {
                return BigInteger.ZERO
            }
            val balance = BigDecimal(rpcResp.data.account.balance)
            return balance.multiply(BigDecimal(10000000000)).toBigInteger()
        } catch (e: Exception) {
            Timber.e("Error fetching Polkadot balance: ${e.message}")
            return BigInteger.ZERO
        }
    }

    override suspend fun getNonce(address: String): BigInteger {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "system_accountNextIndex",
            params = buildJsonArray {
                add(address)
            },
            id = 1,
        )
        val response = httpClient.post(polkadotApiUrl) {
            setBody(payload)
        }
        return response.body<PolkadotGetNonceJson>().result
    }

    override suspend fun getBlockHash(isGenesis: Boolean): String {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "chain_getBlockHash",
            params = buildJsonArray {
                if (isGenesis) add(0)
            },
            id = 1,
        )
        val response = httpClient.post(polkadotApiUrl) {
            setBody(payload)
        }
        return response.body<PolkadotGetBlockHashJson>().result
    }

    override suspend fun getGenesisBlockHash(): String {
        return getBlockHash(true)
    }

    override suspend fun getRuntimeVersion(): Pair<BigInteger, BigInteger> {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "state_getRuntimeVersion",
            params = buildJsonArray { },
            id = 1,
        )
        val response = httpClient.post(polkadotApiUrl) {
            setBody(payload)
        }
        val rpcResp = response.body<PolkadotGetRunTimeVersionJson>()
        val specVersion = rpcResp.result.specVersion
        val transactionVersion = rpcResp.result.transactionVersion
        return Pair(specVersion, transactionVersion)
    }

    override suspend fun getBlockHeader(): BigInteger {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "chain_getHeader",
            params = buildJsonArray { },
            id = 1,
        )

        val response = httpClient.post(polkadotApiUrl) {
            setBody(payload)
        }
        val responseContent = response.body<PolkadotGetBlockHeaderJson>()
        val number = responseContent.result.number
        return BigInteger(number.drop(2), 16)
    }

    override suspend fun broadcastTransaction(tx: String): String? {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "author_submitExtrinsic",
            params = buildJsonArray {
                add(if (tx.startsWith("0x")) tx else "0x${tx}")
            },
            id = 1,
        )
        val response = httpClient.post(polkadotApiUrl) {
            setBody(payload)
        }
        val responseContent = response.body<PolkadotBroadcastTransactionJson>()
        if (responseContent.error != null) {
            if (responseContent.error.code == 1012) {
                return null
            }
            throw Exception("Error broadcasting transaction: $responseContent")
        }
        return responseContent.result
    }

    override suspend fun queryInfo(tx: String) {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "payment_queryInfo",
            params = buildJsonArray {
                add(if (tx.startsWith("0x")) tx else "0x${tx}")
            },
            id = 1,
        )

        val response = httpClient.post(polkadotApiUrl) {
            setBody(payload)
        }
    }
}