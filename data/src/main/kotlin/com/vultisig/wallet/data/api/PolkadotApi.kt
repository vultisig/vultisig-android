package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.PolkadotGetStorageJson
import com.vultisig.wallet.data.api.models.RpcPayload
import com.vultisig.wallet.data.api.models.cosmos.PolkadotBlockResultJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotBroadcastTransactionJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetBlockHashJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetBlockHeaderJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetBlockJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetNonceJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotGetRunTimeVersionJson
import com.vultisig.wallet.data.api.models.cosmos.PolkadotQueryInfoResponseJson
import com.vultisig.wallet.data.api.utils.postRpc
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.utils.Numeric
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

internal fun parsePolkadotFreeBalance(hex: String): BigInteger {
    // minimum 64 hex chars = 32 bytes: 16-byte header (4×u32) + 16-byte free balance (u128)
    if (hex.length < 64) return BigInteger.ZERO
    // AccountInfo SCALE layout: nonce(u32) + consumers(u32) + providers(u32) +
    // sufficients(u32) + free(u128) + ...
    // free balance starts at byte offset 16 (4 x u32 = 16 bytes)
    val freeBytes =
        (0 until 16)
            .map { i -> hex.substring(32 + i * 2, 34 + i * 2).toInt(16).toByte() }
            .toByteArray()
    return BigInteger(1, freeBytes.reversedArray())
}

interface PolkadotApi {
    suspend fun getBalance(address: String): BigInteger

    suspend fun getNonce(address: String): BigInteger

    suspend fun getBlockHash(isGenesis: Boolean = false): String

    /** Returns the block hash pinned to [blockNumber] (not the racing head hash). */
    suspend fun getBlockHashForNumber(blockNumber: BigInteger): String

    suspend fun getGenesisBlockHash(): String

    suspend fun getRuntimeVersion(): Pair<BigInteger, BigInteger>

    suspend fun getBlockHeader(): BigInteger

    suspend fun broadcastTransaction(tx: String): String?

    suspend fun getPartialFee(tx: String): BigInteger

    /**
     * Walks back up to [depth] blocks from the current best head and returns true if a block
     * contains an extrinsic whose blake2b-256 hash matches [txHash]. Substrate full nodes do not
     * index extrinsics by hash, so confirmation is done by scanning recent blocks via plain RPC
     * instead of depending on a third-party indexer (e.g. Subscan).
     *
     * Head-relative, so only suitable right after broadcast (the inclusion block is near the head).
     * For status polling that may run long after broadcast, use [isExtrinsicInBlockRange], whose
     * window is anchored to an absolute block and does not drift as the head advances.
     */
    suspend fun isExtrinsicInChain(txHash: String, depth: Int): Boolean

    /**
     * Returns true if an extrinsic whose blake2b-256 hash matches [txHash] appears in any block in
     * the inclusive number range `[fromBlock, toBlock]`. The range is anchored at [toBlock] (which
     * the caller caps at the live head) and walked backward via `parentHash` down to [fromBlock] —
     * Substrate blocks only link to their parent and number sequentially, so the absolute window is
     * scanned regardless of how far the head has since advanced past it. This keeps a confirmed but
     * already-buried inclusion block reachable, unlike a head-relative scan.
     */
    suspend fun isExtrinsicInBlockRange(txHash: String, fromBlock: Long, toBlock: Long): Boolean
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
            return parsePolkadotFreeBalance(hex)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "Error fetching Polkadot balance")
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
        return response.bodyOrThrow<PolkadotGetNonceJson>().result
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
        return response.bodyOrThrow<PolkadotGetBlockHashJson>().result
    }

    override suspend fun getBlockHashForNumber(blockNumber: BigInteger): String {
        val payload =
            RpcPayload(
                jsonrpc = "2.0",
                method = "chain_getBlockHash",
                params = buildJsonArray { add(blockNumber.toLong()) },
                id = 1,
            )
        val response = httpClient.post(POLKADOT_API_URL) { setBody(payload) }
        return response.bodyOrThrow<PolkadotGetBlockHashJson>().result
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
        val rpcResp = response.bodyOrThrow<PolkadotGetRunTimeVersionJson>()
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
        val responseContent = response.bodyOrThrow<PolkadotGetBlockHeaderJson>()
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
        // Read the RPC body regardless of HTTP status: nodes may surface the idempotent
        // "already in pool" errors (1012/1013) with a non-2xx status, and the duplicate
        // rebroadcast from a second signing device must still resolve to null rather than throw.
        return SubstrateBroadcast.classify(response.body<PolkadotBroadcastTransactionJson>())
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

    override suspend fun isExtrinsicInChain(txHash: String, depth: Int): Boolean {
        val target = txHash.removePrefix("0x").lowercase()
        if (target.isEmpty()) return false

        // Start from the best head (params omitted) and follow parentHash links. Inclusion in the
        // best chain is enough to treat the transaction as on-chain; GRANDPA finalizes within a
        // couple of blocks so a reorg deep enough to drop it is not a practical concern here.
        var blockHash: String? = null
        var scanned = 0
        while (scanned < depth) {
            val block = getBlock(blockHash)?.block ?: break
            if (block.extrinsics.any { extrinsicHash(it) == target }) {
                return true
            }
            blockHash = block.header.parentHash
            scanned++
        }
        return false
    }

    override suspend fun isExtrinsicInBlockRange(
        txHash: String,
        fromBlock: Long,
        toBlock: Long,
    ): Boolean {
        val target = txHash.removePrefix("0x").lowercase()
        if (target.isEmpty() || toBlock < fromBlock) return false

        // Anchor at the top of the window and descend via parentHash. Block numbers are sequential
        // in Substrate, so the parent of block N is N-1 — we count down instead of re-reading the
        // header number each hop.
        //
        // A null hash/block mid-walk means the RPC returned an error envelope (postRpc reads a
        // nullable result), not that the extrinsic is absent. Returning false here would be
        // indistinguishable from a genuine miss, so checkByInclusionWindow could terminally Fail a
        // confirmed transfer once the head passes the window. Instead we throw on an incomplete
        // scan so it propagates to checkStatus and the tx stays Pending for a later retry; false is
        // returned only after the full window was traversed without a match.
        var blockHash: String =
            getBlockHashByNumber(toBlock)
                ?: error("Polkadot block hash unavailable for $toBlock; inclusion scan incomplete")
        var current = toBlock
        while (current >= fromBlock) {
            val block =
                getBlock(blockHash)?.block
                    ?: error("Polkadot block $current unavailable; inclusion scan incomplete")
            if (block.extrinsics.any { extrinsicHash(it) == target }) {
                return true
            }
            blockHash = block.header.parentHash
            current--
        }
        return false
    }

    private suspend fun getBlockHashByNumber(number: Long): String? =
        httpClient
            .postRpc<PolkadotGetBlockHashJson>(
                url = POLKADOT_API_URL,
                method = "chain_getBlockHash",
                params = buildJsonArray { add(number) },
            )
            .result
            .takeIf { it.isNotBlank() }

    private suspend fun getBlock(blockHash: String?): PolkadotBlockResultJson? {
        return httpClient
            .postRpc<PolkadotGetBlockJson>(
                url = POLKADOT_API_URL,
                method = "chain_getBlock",
                params = buildJsonArray { if (blockHash != null) add(blockHash) },
            )
            .result
    }

    // Canonical Substrate extrinsic hash: blake2b-256 over the full SCALE-encoded extrinsic, the
    // same bytes broadcast via author_submitExtrinsic. Mirrors PolkadotHelper.getSignedTransaction.
    private fun extrinsicHash(extrinsicHex: String): String =
        Numeric.toHexStringNoPrefix(Utils.blake2bHash(Numeric.hexStringToByteArray(extrinsicHex)))
            .lowercase()

    private companion object {
        private const val POLKADOT_API_URL = "https://api.vultisig.com/dot/"
        // xxHash128("System") + xxHash128("Account") — well-known Substrate storage prefix
        private const val SYSTEM_ACCOUNT_PREFIX =
            "26aa394eea5630e07c48ae0c9558cef7b99d880ec681799c0cf30e8886371da9"
    }
}
