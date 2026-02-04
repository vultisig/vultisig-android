package com.vultisig.wallet.data.api

import RippleBroadcastSuccessResponseJson
import com.vultisig.wallet.data.api.models.PolkadotResponseJson
import com.vultisig.wallet.data.api.models.RpcPayload
import com.vultisig.wallet.data.api.models.cosmos.PolkadotBroadcastTransactionJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotExtrinsicResponseJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetBlockHashJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetBlockHeaderJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetNonceJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetRunTimeVersionJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotQueryInfoResponseJson
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    suspend fun getPartialFee(tx: String): BigInteger
    suspend fun getTsStatus(txHash: String): PolkadotExtrinsicResponseJson?

}

internal class PolkadotApiImp @Inject constructor(
    private val httpClient: HttpClient
) : PolkadotApi {
    override suspend fun getBalance(address: String): BigInteger {
        try {
            val bodyMap = mapOf(
                "key" to address
            )
            val response = httpClient
                .post(POLKADOT_BALANCE_API_URL) {
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
        val response = httpClient.post(POLKADOT_API_URL) {
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
        val response = httpClient.post(POLKADOT_API_URL) {
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
        val response = httpClient.post(POLKADOT_API_URL) {
            setBody(payload)
        }
        val rpcResp = response.body<PolkadotGetRunTimeVersionJson>()
        val specVersion = rpcResp.result.specVersion
        val transactionVersion = rpcResp.result.transactionVersion
        return Pair(
            specVersion,
            transactionVersion
        )
    }

    override suspend fun getBlockHeader(): BigInteger {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "chain_getHeader",
            params = buildJsonArray { },
            id = 1,
        )

        val response = httpClient.post(POLKADOT_API_URL) {
            setBody(payload)
        }
        val responseContent = response.body<PolkadotGetBlockHeaderJson>()
        val number = responseContent.result.number
        return BigInteger(
            number.drop(2),
            16
        )
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
        val response = httpClient.post(POLKADOT_API_URL) {
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

    override suspend fun getPartialFee(tx: String): BigInteger {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "payment_queryInfo",
            params = buildJsonArray {
                add(if (tx.startsWith("0x")) tx else "0x${tx}")
            },
            id = 1,
        )

        val response = httpClient.post(POLKADOT_API_URL) {
            setBody(payload)
        }

        return response.bodyOrThrow<PolkadotQueryInfoResponseJson>().result
            ?.partialFee
            ?.toBigIntegerOrNull()
            ?: throw Exception("Can't obtained Partial Fee")
    }

    override suspend fun getTsStatus(txHash: String): PolkadotExtrinsicResponseJson? {

            val response = httpClient.post(POLKADOT_EXTRINSIC_API_URL) {
                setBody(mapOf("hash" to txHash))
            }
            return response.bodyOrThrow<PolkadotExtrinsicResponseJson>()
        }

    private companion object {
        private const val POLKADOT_API_URL = "https://api.vultisig.com/dot/"
        val POLKADOT_BALANCE_API_URL =
            "https://assethub-polkadot.api.subscan.io/api/v2/scan/search"
        val POLKADOT_EXTRINSIC_API_URL =
            "https://assethub-polkadot.api.subscan.io/api/scan/extrinsic"
    }
}