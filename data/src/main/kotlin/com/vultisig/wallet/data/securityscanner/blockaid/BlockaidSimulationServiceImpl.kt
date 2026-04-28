package com.vultisig.wallet.data.securityscanner.blockaid

import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.securityscanner.BLOCKAID_PROVIDER
import com.vultisig.wallet.data.utils.toEvenLengthHexString
import io.ktor.util.decodeBase64Bytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import wallet.core.jni.Base58

/**
 * Cache + inflight-coalescing implementation of [BlockaidSimulationService].
 *
 * Mirrors the iOS `BlockaidSimulationService` actor:
 * - one [Mutex] guards the cache + inflight maps, so map mutation and awaiting on inflight requests
 *   are atomic per call
 * - successful scans are cached forever (process lifetime)
 * - empty scans (chain supported, no diff, no risk) are cached as well, so we don't keep retrying a
 *   "this dApp call has no balance change" verdict
 * - failures are not cached: the next screen retries
 *
 * The cache key is built lazily — only payloads whose [BlockaidSimulationCacheKey.from] returns
 * non-null are eligible. Everything else short-circuits to [BlockaidKeysignScanResult.EMPTY]
 * without hitting the network.
 */
internal class BlockaidSimulationServiceImpl(private val rpcClient: BlockaidRpcClientContract) :
    BlockaidSimulationService {

    private val mutex = Mutex()
    private val cache = mutableMapOf<BlockaidSimulationCacheKey, BlockaidKeysignScanResult>()
    private val inflight =
        mutableMapOf<BlockaidSimulationCacheKey, CompletableDeferred<BlockaidKeysignScanResult>>()

    override suspend fun scan(payload: KeysignPayload): BlockaidKeysignScanResult {
        val key = BlockaidSimulationCacheKey.from(payload) ?: return BlockaidKeysignScanResult.EMPTY

        // Fast path: cached successes return without acquiring an outer lock
        // for very long. The mutex is released once we either (a) return cached
        // value, (b) await an inflight task, or (c) install our own deferred.
        val pending: CompletableDeferred<BlockaidKeysignScanResult>
        val isLeader: Boolean
        mutex.withLock {
            cache[key]?.let {
                return it
            }
            inflight[key]?.let {
                pending = it
                isLeader = false
                return@withLock
            }
            val deferred = CompletableDeferred<BlockaidKeysignScanResult>()
            inflight[key] = deferred
            pending = deferred
            isLeader = true
        }

        if (!isLeader) {
            // Followers wait on the leader's deferred. The leader always
            // completes the deferred with a value (EMPTY on its own failure),
            // so [await] only throws if the follower's OWN coroutine is
            // cancelled — in which case Kotlin requires we let the exception
            // propagate so the follower's scope unwinds correctly.
            return pending.await()
        }

        // Leader path: dispatch the actual RPC. The cleanup block MUST run
        // even if this coroutine is cancelled mid-flight, otherwise the
        // [inflight] entry leaks and any future caller for the same key would
        // suspend forever on an orphaned deferred. [NonCancellable] guarantees
        // suspension points inside the block (mutex acquisition) ignore the
        // cancellation signal until cleanup is done.
        val outcome = runCatching { dispatchScan(payload, key) }
        val result = outcome.getOrDefault(BlockaidKeysignScanResult.EMPTY)

        var stillOwns = false
        withContext(NonCancellable) {
            mutex.withLock {
                // Capture our former owned entry so we can detect whether [invalidateAll] has run
                // while our dispatch was in flight. If the entry is no longer ours we MUST NOT
                // write to [cache] — doing so would resurrect data the caller meant to clear.
                val ownedEntry = inflight.remove(key)
                stillOwns = ownedEntry === pending
                // Empty results are cached: when the chain returns no diff or no risk, the
                // verdict is stable for that calldata. Errors are NOT cached so the next screen
                // can retry — on the same payload, a transient network error today shouldn't
                // poison verify → done forever.
                if (outcome.isSuccess && stillOwns) {
                    cache[key] = result
                } else if (!outcome.isSuccess) {
                    outcome.exceptionOrNull()?.let {
                        if (it !is CancellationException) {
                            Timber.w(it, "Blockaid simulation scan failed for %s", key)
                        }
                    }
                }
            }
            // Complete inside NonCancellable too so followers always wake up, even when the
            // leader is being cancelled. [pending] is the captured local — NOT [inflight[key]] —
            // so a re-entered scan that installed a fresh deferred is not affected.
            pending.complete(result)
        }

        // The leader's caller, on the other hand, MUST observe the CancellationException so the
        // surrounding scope can unwind. Kotlin's coroutines contract is unambiguous: catch
        // CancellationException only to perform cleanup, then rethrow.
        outcome.exceptionOrNull()?.let { if (it is CancellationException) throw it }

        // Symmetry with the cache write above: if [invalidateAll] cleared our entry while we were
        // dispatching, the caller asked us to discard this scan. Returning EMPTY keeps the
        // contract consistent with what followers received and prevents the leader from
        // applying a stale verdict to UI state that has since moved on (e.g. a vault switch).
        return if (stillOwns) result else BlockaidKeysignScanResult.EMPTY
    }

    override suspend fun invalidateAll() {
        // Mutex protects both maps; we MUST hold it because [scan] mutates
        // them under the same lock. Pre-collect deferreds so we can complete
        // them after releasing the lock — completion resumes awaiting
        // coroutines, which we don't want to do while holding a non-reentrant
        // lock.
        val pending: List<CompletableDeferred<BlockaidKeysignScanResult>>
        mutex.withLock {
            cache.clear()
            pending = inflight.values.toList()
            inflight.clear()
        }
        pending.forEach { it.complete(BlockaidKeysignScanResult.EMPTY) }
    }

    private suspend fun dispatchScan(
        payload: KeysignPayload,
        key: BlockaidSimulationCacheKey,
    ): BlockaidKeysignScanResult =
        when (key) {
            is BlockaidSimulationCacheKey.Evm -> scanEvm(payload)
            is BlockaidSimulationCacheKey.Solana -> scanSolana(payload)
        }

    private suspend fun scanEvm(payload: KeysignPayload): BlockaidKeysignScanResult {
        val response =
            rpcClient.simulateEvmTransaction(
                chain = payload.coin.chain,
                from = payload.coin.address,
                to = payload.toAddress,
                amount = payload.toAmount.toEvenLengthHexString(),
                data = payload.memo ?: "0x",
            )
        val simulation = BlockaidSimulationParser.parseEvm(response, payload.coin.chain)
        val scannerResult = response.toSecurityScannerResultOrNull(BLOCKAID_PROVIDER)
        return BlockaidKeysignScanResult(simulation = simulation, scannerResult = scannerResult)
    }

    private suspend fun scanSolana(payload: KeysignPayload): BlockaidKeysignScanResult {
        // The Android keysign payload carries Solana raw transactions as base64
        // (matches the proto definition `SignSolana.raw_transactions`). The
        // Blockaid simulate endpoint expects base58, so we transcode here.
        val rawBase64 = payload.signSolana?.rawTransactions.orEmpty()
        if (rawBase64.isEmpty()) return BlockaidKeysignScanResult.EMPTY
        val rawBase58 =
            rawBase64.map { base64 ->
                // encodeNoCheck rather than encode: these are raw transactions, not
                // addresses, so the 4-byte SHA-256 checksum that Base58Check appends
                // would corrupt the payload Blockaid receives.
                runCatching { Base58.encodeNoCheck(base64.decodeBase64Bytes()) }
                    .getOrElse {
                        Timber.w(it, "Solana raw transaction base64 decode failed")
                        return BlockaidKeysignScanResult.EMPTY
                    }
            }
        val response =
            rpcClient.simulateSolanaTransaction(
                address = payload.coin.address,
                rawTransactionsBase58 = rawBase58,
            )
        val simulation = BlockaidSimulationParser.parseSolana(response)
        val scannerResult = response.toSecurityScannerResultOrNull(BLOCKAID_PROVIDER)
        return BlockaidKeysignScanResult(simulation = simulation, scannerResult = scannerResult)
    }
}
