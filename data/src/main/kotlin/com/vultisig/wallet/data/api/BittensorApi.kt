package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.RpcPayload
import com.vultisig.wallet.data.api.models.cosmos.PolkadotBroadcastTransactionJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetBlockHashJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetBlockHeaderJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetNonceJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetRunTimeVersionJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotQueryInfoResponseJson
import com.vultisig.wallet.data.api.utils.postRpc
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray

interface BittensorApi {
    suspend fun getBalance(address: String): BigInteger

    suspend fun getNonce(address: String): BigInteger

    suspend fun getBlockHash(isGenesis: Boolean = false): String

    suspend fun getBlockHashForNumber(blockNumber: BigInteger): String

    suspend fun getGenesisBlockHash(): String

    suspend fun getRuntimeVersion(): Pair<BigInteger, BigInteger>

    suspend fun getBlockHeader(): BigInteger

    suspend fun broadcastTransaction(tx: String): String?

    suspend fun getPartialFee(tx: String): BigInteger

    suspend fun getTxStatus(txHash: String): TaostatsExtrinsicData?
}

@kotlinx.serialization.Serializable
data class TaostatsExtrinsicResponse(val data: List<TaostatsExtrinsicData>? = null)

@kotlinx.serialization.Serializable
data class TaostatsExtrinsicData(
    val success: Boolean? = null,
    @kotlinx.serialization.SerialName("block_number") val blockNumber: Int? = null,
)

internal class BittensorApiImp @Inject constructor(private val httpClient: HttpClient) :
    BittensorApi {

    override suspend fun getBalance(address: String): BigInteger {
        // Balance via RPC state_getStorage — no API key needed
        val storageKey = computeAccountStorageKey(address)
        val response =
            httpClient.postRpc<StorageResponse>(
                url = BITTENSOR_RPC_URL,
                method = "state_getStorage",
                params = buildJsonArray { add(storageKey) },
            )
        val result = response.result ?: return BigInteger.ZERO
        return parseBalanceFree(result)
    }

    override suspend fun getNonce(address: String): BigInteger =
        httpClient
            .postRpc<PolkadotGetNonceJson>(
                url = BITTENSOR_RPC_URL,
                method = "system_accountNextIndex",
                params = buildJsonArray { add(address) },
            )
            .result

    override suspend fun getBlockHash(isGenesis: Boolean): String =
        httpClient
            .postRpc<PolkadotGetBlockHashJson>(
                url = BITTENSOR_RPC_URL,
                method = "chain_getBlockHash",
                params = buildJsonArray { if (isGenesis) add(0) },
            )
            .result

    override suspend fun getGenesisBlockHash(): String = getBlockHash(true)

    override suspend fun getBlockHashForNumber(blockNumber: BigInteger): String =
        httpClient
            .postRpc<PolkadotGetBlockHashJson>(
                url = BITTENSOR_RPC_URL,
                method = "chain_getBlockHash",
                params = buildJsonArray { add(blockNumber.toLong()) },
            )
            .result

    override suspend fun getRuntimeVersion(): Pair<BigInteger, BigInteger> {
        val rpcResp =
            httpClient.postRpc<PolkadotGetRunTimeVersionJson>(
                url = BITTENSOR_RPC_URL,
                method = "state_getRuntimeVersion",
                params = buildJsonArray {},
            )
        return Pair(rpcResp.result.specVersion, rpcResp.result.transactionVersion)
    }

    override suspend fun getBlockHeader(): BigInteger {
        val responseContent =
            httpClient.postRpc<PolkadotGetBlockHeaderJson>(
                url = BITTENSOR_RPC_URL,
                method = "chain_getHeader",
                params = buildJsonArray {},
            )
        return BigInteger(responseContent.result.number.drop(2), 16)
    }

    override suspend fun broadcastTransaction(tx: String): String? {
        // Broadcast stays manual due to error-field check
        val payload =
            RpcPayload(
                jsonrpc = "2.0",
                method = "author_submitExtrinsic",
                params = buildJsonArray { add(if (tx.startsWith("0x")) tx else "0x$tx") },
                id = 1,
            )
        val response = httpClient.post(BITTENSOR_RPC_URL) { setBody(payload) }
        val responseContent = response.body<PolkadotBroadcastTransactionJson>()
        if (responseContent.error != null) {
            if (responseContent.error.message?.contains("Already Imported") == true) {
                return null
            }
            throw Exception(
                "Bittensor broadcast failed: ${responseContent.error.data ?: responseContent.error.message}"
            )
        }
        return responseContent.result
    }

    override suspend fun getPartialFee(tx: String): BigInteger {
        val hexTx = if (tx.startsWith("0x")) tx else "0x$tx"
        return httpClient
            .postRpc<PolkadotQueryInfoResponseJson>(
                url = BITTENSOR_RPC_URL,
                method = "payment_queryInfo",
                params = buildJsonArray { add(hexTx) },
            )
            .result
            ?.partialFee
            ?.toBigIntegerOrNull() ?: throw Exception("Can't obtain Bittensor partial fee")
    }

    /** Compute System.Account storage key for an SS58 address */
    private fun computeAccountStorageKey(address: String): String {
        val pubkey = com.vultisig.wallet.data.chains.helpers.BittensorHelper.ss58Decode(address)
        val blake2Hash = wallet.core.jni.Hash.blake2b(pubkey, 16)
        return "0x" +
            SYSTEM_ACCOUNT_PREFIX +
            blake2Hash.joinToString("") { "%02x".format(it) } +
            pubkey.joinToString("") { "%02x".format(it) }
    }

    /** Parse free balance from SCALE-encoded AccountInfo */
    private fun parseBalanceFree(hex: String): BigInteger {
        val clean = if (hex.startsWith("0x")) hex.drop(2) else hex
        if (clean.length < 64) return BigInteger.ZERO
        // free balance at bytes 16-31 (hex chars 32-63), u128 LE
        val freeHex = clean.substring(32, 64)
        val beHex = freeHex.chunked(2).reversed().joinToString("")
        return BigInteger(beHex, 16)
    }

    override suspend fun getTxStatus(txHash: String): TaostatsExtrinsicData? {
        val hash = if (txHash.startsWith("0x")) txHash else "0x$txHash"
        val response = httpClient.get("${TAOSTATS_PROXY_URL}/extrinsic/v1?hash=$hash")
        if (response.status == HttpStatusCode.NotFound) return null
        return response.body<TaostatsExtrinsicResponse>().data?.firstOrNull()
    }

    private companion object {
        const val BITTENSOR_RPC_URL = "https://bittensor-finney.api.onfinality.io/public"
        const val TAOSTATS_PROXY_URL = "https://api.vultisig.com/tao-tx"
        const val SYSTEM_ACCOUNT_PREFIX =
            "26aa394eea5630e07c48ae0c9558cef7b99d880ec681799c0cf30e8886371da9"
    }
}

@kotlinx.serialization.Serializable private data class StorageResponse(val result: String? = null)
