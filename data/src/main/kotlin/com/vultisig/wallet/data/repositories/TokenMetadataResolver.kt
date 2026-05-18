package com.vultisig.wallet.data.repositories

import androidx.annotation.VisibleForTesting
import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Symbol + decimals pair for an ERC-20-shaped contract. */
data class TokenMetadata(val symbol: String, val decimals: Int)

/**
 * Caching resolver for unknown ERC-20 token metadata. Backs the verify and join-keysign screens
 * when an ABI-decoded transaction references a token contract that no installed vault holds — for
 * example a fresh stablecoin or LP token surfaced by a dApp.
 *
 * Resolution path:
 * 1. Returns a cached entry when one is fresher than [ttl].
 * 2. Coalesces concurrent calls for the same (chain, address) pair onto a single in-flight RPC so
 *    that wiring this into multiple rows on the same screen costs one round-trip, not several.
 * 3. Falls through to [TokenRepository.getEVMTokenByContract], which already executes the parallel
 *    `symbol()` / `decimals()` eth_calls via [com.vultisig.wallet.data.api.EvmApi.findCustomToken].
 *
 * Failures (network error, non-ERC-20 contract, garbage response) are returned as `null` and never
 * cached — a transient outage shouldn't poison the next 24 hours of lookups. The decimals upper
 * bound rejects contracts claiming more than [MAX_DECIMALS] places; downstream display code
 * computes `10 ^ decimals`, so a hostile or buggy contract returning e.g. 255 would chew CPU during
 * render without this guard. 36 is well above any legitimate token (18 is the de-facto ceiling).
 */
@Singleton
class TokenMetadataResolver
@Inject
constructor(
    private val tokenRepository: TokenRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal constructor(
        tokenRepository: TokenRepository,
        ioDispatcher: CoroutineDispatcher,
        clock: () -> Long,
        ttl: Duration,
    ) : this(tokenRepository, ioDispatcher) {
        this.clock = clock
        this.ttl = ttl
    }

    private var clock: () -> Long = { System.currentTimeMillis() }
        private set

    private var ttl: Duration = DEFAULT_TTL
        private set

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, CacheEntry>()
    private val inFlight = mutableMapOf<String, CompletableDeferred<TokenMetadata?>>()

    /**
     * Resolves the symbol + decimals for the ERC-20 token at [contractAddress] on [chain]. Returns
     * `null` when [chain] is not an EVM chain, when [contractAddress] is blank, when the RPC call
     * fails, or when the response does not look like a valid ERC-20.
     */
    suspend fun resolve(chain: Chain, contractAddress: String): TokenMetadata? {
        if (chain.standard != TokenStandard.EVM) return null
        val key = cacheKey(chain, contractAddress) ?: return null

        // Acquire either a fresh cache hit, a deferred to wait on, or a deferred to own. The lock
        // is released before any suspending await/fetch so a slow RPC never blocks unrelated
        // lookups.
        val slot =
            mutex.withLock {
                cache[key]?.let { cached ->
                    if (clock() - cached.fetchedAt < ttl.inWholeMilliseconds) {
                        return cached.metadata
                    }
                }
                val existing = inFlight[key]
                if (existing != null) {
                    Slot.Wait(existing)
                } else {
                    val owned = CompletableDeferred<TokenMetadata?>()
                    inFlight[key] = owned
                    Slot.Own(owned)
                }
            }

        return when (slot) {
            is Slot.Wait -> slot.deferred.await()
            is Slot.Own -> runFetch(chain, contractAddress, key, slot.deferred)
        }
    }

    private suspend fun runFetch(
        chain: Chain,
        contractAddress: String,
        key: String,
        owned: CompletableDeferred<TokenMetadata?>,
    ): TokenMetadata? {
        try {
            val metadata = fetch(chain, contractAddress)
            mutex.withLock {
                inFlight.remove(key)
                if (metadata != null) {
                    cache[key] = CacheEntry(metadata = metadata, fetchedAt = clock())
                }
            }
            owned.complete(metadata)
            return metadata
        } catch (e: CancellationException) {
            mutex.withLock { inFlight.remove(key) }
            owned.cancel(e)
            throw e
        } catch (t: Throwable) {
            mutex.withLock { inFlight.remove(key) }
            // Followers must not observe the failure as a thrown exception — completing with
            // `null` (the standard "couldn't resolve" sentinel everywhere else in this resolver)
            // keeps every awaiter on the same code path. The failure is also not cached, so a
            // transient outage doesn't poison the next [ttl] window.
            owned.complete(null)
            Timber.w(t, "Token metadata fetch failed for %s on %s", contractAddress, chain.raw)
            return null
        }
    }

    private suspend fun fetch(chain: Chain, contractAddress: String): TokenMetadata? {
        val coin =
            withContext(ioDispatcher) {
                tokenRepository.getEVMTokenByContract(chain.id, contractAddress)
            } ?: return null
        val symbol = coin.ticker.trim()
        if (symbol.isEmpty()) return null
        if (coin.decimal !in 0..MAX_DECIMALS) return null
        return TokenMetadata(symbol = symbol, decimals = coin.decimal)
    }

    private fun cacheKey(chain: Chain, contractAddress: String): String? {
        val trimmed = contractAddress.trim().takeIf { it.isNotEmpty() } ?: return null
        return "${chain.raw}|${trimmed.lowercase()}"
    }

    private data class CacheEntry(val metadata: TokenMetadata, val fetchedAt: Long)

    private sealed interface Slot {
        val deferred: CompletableDeferred<TokenMetadata?>

        data class Wait(override val deferred: CompletableDeferred<TokenMetadata?>) : Slot

        data class Own(override val deferred: CompletableDeferred<TokenMetadata?>) : Slot
    }

    companion object {
        /**
         * Hard ceiling for a contract's reported `decimals()`. Any value above this is treated as a
         * malicious or buggy response — downstream formatting computes `10 ^ decimals` and a value
         * like 255 would chew CPU during render. 36 is well above every legitimate token (18 is the
         * de-facto ceiling, USDC/USDT use 6).
         */
        const val MAX_DECIMALS: Int = 36

        /**
         * How long a successful resolution stays cached before the next on-screen access triggers a
         * fresh RPC call. 24h matches iOS so the two platforms surface the same metadata for the
         * same token within a single working session.
         */
        val DEFAULT_TTL: Duration = 24.hours
    }
}
