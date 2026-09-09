package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.ZcashBlockchainInfoResponse
import com.vultisig.wallet.data.api.models.ZcashRpcRequest
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

interface ZcashApi {
    /**
     * Resolves the active ZIP-243 consensus branch id for the next block, as the little-endian hex
     * string WalletCore expects on the transaction plan (e.g. `30f33754`). The Zcash node reports
     * it big-endian (`consensus.nextblock`, e.g. `5437f330`); this reverses the four bytes.
     *
     * The value is cached in memory for [CACHE_TTL_MS] (one hour): the branch id only changes at a
     * network upgrade, so a single keysign's preimage-hash and final-compile passes (and repeated
     * sends) all read the same cached value, keeping the digest stable across them.
     *
     * Returns `null` when the RPC is unreachable or the response is malformed; signing then refuses
     * to proceed (there is no compiled-in fallback, since a stale branch id yields a network-
     * rejected tx). The branch id is a network-global fact, so every device that resolves it at
     * signing time agrees, keeping MPC co-signers in sync.
     */
    suspend fun getConsensusBranchIdHex(): String?
}

internal class ZcashApiImpl @Inject constructor(private val httpClient: HttpClient) : ZcashApi {

    private val cacheMutex = Mutex()
    @Volatile private var cachedBranchId: String? = null
    @Volatile private var cachedAtMs: Long = 0L

    override suspend fun getConsensusBranchIdHex(): String? {
        freshCachedBranchId()?.let {
            return it
        }
        // Double-checked under the lock so concurrent co-sign rounds issue a single network call.
        return cacheMutex.withLock {
            freshCachedBranchId()?.let {
                return it
            }
            val fetched = fetchBranchId()
            // Only cache successful fetches so a transient RPC failure (which returns null and
            // makes
            // the caller refuse to sign) doesn't pin the wallet to that failure for a whole hour.
            if (fetched != null) {
                cachedBranchId = fetched
                cachedAtMs = System.currentTimeMillis()
            }
            fetched
        }
    }

    private fun freshCachedBranchId(): String? =
        cachedBranchId?.takeIf { System.currentTimeMillis() - cachedAtMs < CACHE_TTL_MS }

    private suspend fun fetchBranchId(): String? {
        return try {
            val response =
                httpClient
                    .post(BASE_URL) {
                        header("Content-Type", "application/json")
                        setBody(ZcashRpcRequest(method = "getblockchaininfo", params = emptyList()))
                    }
                    .bodyOrThrow<ZcashBlockchainInfoResponse>()
            val branchIdBigEndian = response.result?.consensus?.nextblock
            if (branchIdBigEndian.isNullOrEmpty()) {
                Timber.w("Zcash getblockchaininfo returned no consensus.nextblock branch id")
                return null
            }
            reverseHexBytes(branchIdBigEndian)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "Failed to fetch Zcash consensus branch id")
            null
        }
    }

    /**
     * Reverses the byte order of a 4-byte (8 hex char) big-endian branch id into the little-endian
     * form WalletCore reads from the plan. Returns `null` for any input that is not exactly four
     * hex bytes so a malformed RPC response makes signing refuse rather than use a bad sighash.
     */
    private fun reverseHexBytes(hex: String): String? {
        if (hex.length != 8 || !hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            Timber.w("Zcash branch id '%s' is not a 4-byte hex value", hex)
            return null
        }
        return hex.chunked(2).reversed().joinToString("").lowercase()
    }

    companion object {
        private const val BASE_URL = "https://api.vultisig.com/zcash/"
        private const val CACHE_TTL_MS = 60L * 60L * 1000L
    }
}
