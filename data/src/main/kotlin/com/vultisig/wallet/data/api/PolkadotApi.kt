package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.RpcPayload
import com.vultisig.wallet.data.api.models.cosmos.PolkadotBroadcastTransactionJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotExtrinsicResponseJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetBlockHashJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetBlockHeaderJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetNonceJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetRunTimeVersionJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetStorageJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotQueryInfoResponseJson
import com.vultisig.wallet.data.api.utils.postRpc
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import timber.log.Timber
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.Hash

interface PolkadotApi {
    suspend fun getBalance(address: String): BigInteger

    suspend fun getNonce(address: String): BigInteger

    suspend fun getBlockHash(isGenesis: Boolean = false): String

    suspend fun getGenesisBlockHash(): String

    suspend fun getRuntimeVersion(): Pair<BigInteger, BigInteger>

    suspend fun getBlockHeader(): BigInteger

    suspend fun broadcastTransaction(tx: String): String?

    suspend fun getPartialFee(tx: String): BigInteger

    suspend fun getTxStatus(txHash: String): PolkadotExtrinsicResponseJson?
}

internal class PolkadotApiImp @Inject constructor(private val httpClient: HttpClient) :
    PolkadotApi {
    override suspend fun getBalance(address: String): BigInteger {
        try {
            val pubKey = AnyAddress(address, CoinType.POLKADOT).data()
            val blake2b128 = Hash.blake2b(pubKey, 16)
            val storageKey =
                "0x" +
                    SYSTEM_ACCOUNT_PREFIX +
                    blake2b128.joinToString("") { "%02x".format(it) } +
                    pubKey.joinToString("") { "%02x".format(it) }
            val result =
                httpClient
                    .postRpc<PolkadotGetStorageJson>(
                        url = POLKADOT_API_URL,
                        method = "state_getStorage",
                        params = buildJsonArray { add(storageKey) },
                    )
                    .result ?: return BigInteger.ZERO
            val hex = result.removePrefix("0x")
            if (hex.length < 64) return BigInteger.ZERO
            // AccountInfo SCALE layout: nonce(u32) + consumers(u32) + providers(u32) +
            // sufficients(u32) + free(u128) + ...
            // free balance starts at byte offset 16 (4 x u32 = 16 bytes)
            val freeBytes =
                (0 until 16)
                    .map { i -> hex.substring(32 + i * 2, 34 + i * 2).toInt(16).toByte() }
                    .toByteArray()
            return BigInteger(1, freeBytes.reversedArray())
        } catch (e: Exception) {
            Timber.e("Error fetching Polkadot balance: ${e.message}")
            return BigInteger.ZERO
        }
    }

    override suspend fun getNonce(address: String): BigInteger {
        val payload =
            RpcPayload(
                jsonrpc = "2.0",
                method = "system_accountNextIndex",
                params = buildJsonArray { add(address) },
                id = 1,
            )
        val response = httpClient.post(POLKADOT_API_URL) { setBody(payload) }
        return response.body<PolkadotGetNonceJson>().result
    }

    override suspend fun getBlockHash(isGenesis: Boolean): String {
        val payload =
            RpcPayload(
                jsonrpc = "2.0",
                method = "chain_getBlockHash",
                params = buildJsonArray { if (isGenesis) add(0) },
                id = 1,
            )
        val response = httpClient.post(POLKADOT_API_URL) { setBody(payload) }
        return response.body<PolkadotGetBlockHashJson>().result
    }

    override suspend fun getGenesisBlockHash(): String {
        return getBlockHash(true)
    }

    override suspend fun getRuntimeVersion(): Pair<BigInteger, BigInteger> {
        val payload =
            RpcPayload(
                jsonrpc = "2.0",
                method = "state_getRuntimeVersion",
                params = buildJsonArray {},
                id = 1,
            )
        val response = httpClient.post(POLKADOT_API_URL) { setBody(payload) }
        val rpcResp = response.body<PolkadotGetRunTimeVersionJson>()
        val specVersion = rpcResp.result.specVersion
        val transactionVersion = rpcResp.result.transactionVersion
        return Pair(specVersion, transactionVersion)
    }

    override suspend fun getBlockHeader(): BigInteger {
        val payload =
            RpcPayload(
                jsonrpc = "2.0",
                method = "chain_getHeader",
                params = buildJsonArray {},
                id = 1,
            )

        val response = httpClient.post(POLKADOT_API_URL) { setBody(payload) }
        val responseContent = response.body<PolkadotGetBlockHeaderJson>()
        val number = responseContent.result.number
        return BigInteger(number.drop(2), 16)
    }

    override suspend fun broadcastTransaction(tx: String): String? {
        val payload =
            RpcPayload(
                jsonrpc = "2.0",
                method = "author_submitExtrinsic",
                params = buildJsonArray { add(if (tx.startsWith("0x")) tx else "0x${tx}") },
                id = 1,
            )
        val response = httpClient.post(POLKADOT_API_URL) { setBody(payload) }
        val responseContent = response.body<PolkadotBroadcastTransactionJson>()
        if (responseContent.error != null) {
            if (responseContent.error.code == 1012 || responseContent.error.code == 1013) {
                return null
            }
            throw Exception(
                "Error broadcasting transaction: ${responseContent.error.data ?: responseContent.error.message}"
            )
        }
        return responseContent.result
    }

    override suspend fun getPartialFee(tx: String): BigInteger {
        val payload =
            RpcPayload(
                jsonrpc = "2.0",
                method = "payment_queryInfo",
                params = buildJsonArray { add(if (tx.startsWith("0x")) tx else "0x${tx}") },
                id = 1,
            )

        val response = httpClient.post(POLKADOT_API_URL) { setBody(payload) }

        return response
            .bodyOrThrow<PolkadotQueryInfoResponseJson>()
            .result
            ?.partialFee
            ?.toBigIntegerOrNull() ?: throw Exception("Can't obtained Partial Fee")
    }

    override suspend fun getTxStatus(txHash: String): PolkadotExtrinsicResponseJson? {

        val response =
            httpClient.post(POLKADOT_EXTRINSIC_API_URL) { setBody(mapOf("hash" to txHash)) }
        return response.bodyOrThrow<PolkadotExtrinsicResponseJson>()
    }

    private companion object {
        private const val POLKADOT_API_URL = "https://api.vultisig.com/dot/"
        private const val POLKADOT_EXTRINSIC_API_URL =
            "https://assethub-polkadot.api.subscan.io/api/scan/extrinsic"
        // xxHash128("System") + xxHash128("Account") — well-known Substrate storage prefix
        private const val SYSTEM_ACCOUNT_PREFIX =
            "26aa394eea5630e07c48ae0c9558cef7b99d880ec681799c0cf30e8886371da9"
    }
}
