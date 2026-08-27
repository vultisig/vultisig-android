package com.vultisig.wallet.data.repositories.swap

import androidx.annotation.VisibleForTesting
import com.vultisig.wallet.data.api.errors.SwapKitError
import com.vultisig.wallet.data.api.models.quotes.SwapKitProvidersResponseJson
import com.vultisig.wallet.data.api.swapAggregators.SwapKitApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.evmChainId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * 24h in-memory cache for SwapKit `/providers` enablement data. The eligible chain set is the union
 * of every non-filtered provider's `enabledChainIds`, mapped back to Vultisig's [Chain] enum.
 *
 * Two details this gets wrong easily, both mirroring iOS' `SwapKitProviderCache.chainEnabled`:
 * - `enabledChainIds`, not `supportedChainIds`. The latter is a superset a provider merely knows
 *   about; unioning it marks chains live that route nothing.
 * - THORChain / Maya sub-providers are excluded. Vultisig pays those affiliates through its own
 *   native integrations and filters their SwapKit routes out at ranking, so a chain only they
 *   enable is not a chain SwapKit can serve us.
 *
 * The cache is intentionally process-scoped (no disk persistence) — a cold launch every 24h is
 * cheap. A refresh that fails keeps serving the last successful snapshot (iOS does the same): once
 * an answer exists, a stale one beats reporting every chain disabled, which would hide SwapKit
 * app-wide off a single bad `/providers` call. Only the genuine no-data edge fails closed.
 *
 * A stale snapshot that is being served does not reset the TTL, so without a second deadline every
 * later call would re-attempt the failing endpoint: two dead round-trips in front of each quote,
 * since a pair check tests both legs. [RETRY_BACKOFF_MILLIS] holds those off while the stale answer
 * stands. The no-data edge deliberately keeps retrying eagerly — there is nothing to serve there,
 * so backing off would only extend an outage the next call might clear.
 */
interface SwapKitProviderCache {
    /**
     * Returns `true` when SwapKit currently routes on [chain]. Lazily refreshes the underlying
     * `/providers` response on first call or when the cache TTL has elapsed. A failed refresh falls
     * back to the last successful snapshot — briefly re-served without another network attempt —
     * and surfaces `false` only when there has never been one (fail-closed: better to skip SwapKit
     * than offer a bad quote).
     */
    suspend fun isEnabled(chain: Chain): Boolean

    /** Force-invalidates the cached response. Mainly for tests and developer tooling. */
    suspend fun invalidate()
}

/**
 * In-memory [SwapKitProviderCache] with a 24h TTL, mutex-guarded refresh, and fail-closed reads.
 */
@Singleton
internal class SwapKitProviderCacheImpl @Inject constructor(private val api: SwapKitApi) :
    SwapKitProviderCache {

    /** Minimal clock seam so tests can advance time without sleeping. */
    fun interface Clock {
        fun nowMillis(): Long
    }

    /**
     * Overridable clock so tests can advance time deterministically; production uses
     * `System.currentTimeMillis`. Not [Inject]ed to keep Hilt wiring trivial.
     */
    @VisibleForTesting internal var clock: Clock = Clock { System.currentTimeMillis() }

    private val mutex = Mutex()
    @Volatile private var enabledChains: Set<Chain> = emptySet()
    @Volatile private var fetchedAtMillis: Long = 0

    /**
     * Wall-clock instant before which a stale snapshot is served without re-attempting
     * `/providers`. Zero when no refresh has failed since the last success. Only ever set while a
     * last-good snapshot exists, so a reader that observes it non-zero has already observed a
     * non-zero [fetchedAtMillis], and therefore the [enabledChains] published before it.
     */
    @Volatile private var retryAfterMillis: Long = 0

    override suspend fun isEnabled(chain: Chain): Boolean {
        val cached = ensureFresh() ?: return false
        return chain in cached
    }

    override suspend fun invalidate() =
        mutex.withLock {
            // Publish through `fetchedAtMillis` last to match the refresh-path ordering
            // (chains-then-timestamp). A reader on the fast path that sees the reset timestamp is
            // then guaranteed to see the cleared chains too — without this swap the two volatile
            // writes have no joint happens-before and a brief stale read is observable.
            enabledChains = emptySet()
            retryAfterMillis = 0
            fetchedAtMillis = 0
        }

    private suspend fun ensureFresh(): Set<Chain>? {
        if (isServable(clock.nowMillis())) {
            return enabledChains
        }
        return mutex.withLock {
            val now = clock.nowMillis()
            if (isServable(now)) {
                return@withLock enabledChains
            }
            try {
                val response = api.providers()
                val chains = response.toEnabledChains()
                enabledChains = chains
                retryAfterMillis = 0
                fetchedAtMillis = now
                chains
            } catch (e: CancellationException) {
                throw e
            } catch (e: SwapKitError) {
                // Expected transport/decoding failure from the SwapKit proxy — already classified
                // at the API layer. Serve the last good answer if there is one.
                backOffAndServeLastGood(now)
            } catch (e: Exception) {
                // Unexpected (mapping/parse regression, programmer error). Surface it in logs
                // rather than silently treat SwapKit as "disabled" forever.
                Timber.w(e, "SwapKit providers refresh failed unexpectedly")
                backOffAndServeLastGood(now)
            }
        }
    }

    /**
     * True when [enabledChains] can be returned as-is: either still inside the TTL, or stale but
     * inside the retry window opened by the last failed refresh. `fetchedAtMillis != 0L` gates both
     * — it is what makes the read see a published snapshot rather than the empty initial one.
     */
    private fun isServable(now: Long): Boolean =
        fetchedAtMillis != 0L && ((now - fetchedAtMillis) < TTL_MILLIS || now < retryAfterMillis)

    /**
     * Serves the last good snapshot after a failed refresh and, when there is one, holds off the
     * next attempt for [RETRY_BACKOFF_MILLIS]. The deadline is only armed alongside an answer: with
     * no snapshot the call already returns `false`, so retrying costs nothing a caller can see and
     * is the only way back. Called under [mutex].
     */
    private fun backOffAndServeLastGood(now: Long): Set<Chain>? {
        val lastGood = lastGoodOrNull() ?: return null
        retryAfterMillis = now + RETRY_BACKOFF_MILLIS
        return lastGood
    }

    /**
     * The last successfully fetched chain set, or null when `/providers` has never been read in
     * this process. `fetchedAtMillis` is the flag rather than `enabledChains.isEmpty()`: a
     * legitimately empty response would otherwise be indistinguishable from no data. Called only
     * under [mutex], after a refresh attempt left both fields untouched.
     */
    private fun lastGoodOrNull(): Set<Chain>? = if (fetchedAtMillis == 0L) null else enabledChains

    private fun SwapKitProvidersResponseJson.toEnabledChains(): Set<Chain> =
        filterNot { it.provider.uppercase(Locale.ROOT) in FILTERED_PROVIDERS }
            .flatMap { it.enabledChainIds }
            .mapNotNull { id ->
                swapKitChainToVultisig(id).also {
                    if (it == null) Timber.w("Unknown SwapKit chain id: %s", id)
                }
            }
            .toSet()

    companion object {
        /** Cache TTL — 24h. */
        private const val TTL_MILLIS: Long = 24L * 60L * 60L * 1000L

        /**
         * How long a stale snapshot stands after a failed refresh before `/providers` is tried
         * again. Short enough that a recovered endpoint is picked up within a session, long enough
         * that a sustained outage does not put a dead request in front of every quote.
         */
        private const val RETRY_BACKOFF_MILLIS: Long = 5L * 60L * 1000L

        /**
         * Sub-providers whose enablement never counts as SwapKit coverage. Vultisig routes
         * THORChain and Maya through its own native integrations and drops their SwapKit routes at
         * ranking, so treating a chain they alone enable as SwapKit-eligible would offer a provider
         * that can only lose. Matches iOS' `SwapKitConfig.filteredProviders`; compared upper-cased
         * because the upstream has returned mixed casing for these names.
         */
        private val FILTERED_PROVIDERS =
            setOf("THORCHAIN", "THORCHAIN_STREAMING", "MAYACHAIN", "MAYACHAIN_STREAMING")

        /**
         * Maps a SwapKit V3 chain-id entry to Vultisig's [Chain] enum. EVM networks are decimal
         * chain ids (`"1"`, `"56"`, `"4663"`, `"999"`, ...), non-EVM are lowercase slugs
         * (`"solana"`, `"bitcoin"`, ...). Returns `null` for a chain the wallet holds no account on
         * — the caller drops it.
         *
         * EVM ids resolve through [Chain.evmChainId] rather than a hand-written list, so a network
         * the wallet already supports is recognised the moment SwapKit lights it up. The named
         * aliases below stay for the non-numeric spellings the endpoint has historically returned.
         *
         * `"hype"` is deliberately absent: it is HyperCore, a separate venue whose assets carry
         * `USDC:0x…`-style addresses. HyperEVM — the chain this wallet holds — is `"999"`.
         */
        internal fun swapKitChainToVultisig(swapKitChain: String): Chain? {
            val id = swapKitChain.lowercase(Locale.ROOT)
            EVM_CHAINS_BY_ID[id]?.let {
                return it
            }
            return when (id) {
                "ethereum" -> Chain.Ethereum
                "bsc",
                "bnb" -> Chain.BscChain
                "avalanche" -> Chain.Avalanche
                "arbitrum" -> Chain.Arbitrum
                "optimism" -> Chain.Optimism
                "base" -> Chain.Base
                "polygon",
                "matic" -> Chain.Polygon
                "solana" -> Chain.Solana
                "bitcoin" -> Chain.Bitcoin
                "bitcoincash" -> Chain.BitcoinCash
                "litecoin" -> Chain.Litecoin
                "dogecoin" -> Chain.Dogecoin
                "dash" -> Chain.Dash
                "zcash" -> Chain.Zcash
                "ripple",
                "xrp" -> Chain.Ripple
                // Tron is the one non-EVM chain SwapKit keys by a decimal id, so it cannot come
                // from the EVM map above.
                "728126428",
                "tron",
                "trx" -> Chain.Tron
                "cardano" -> Chain.Cardano
                "ton" -> Chain.Ton
                "sui" -> Chain.Sui
                else -> null
            }
        }

        /**
         * Every EVM chain the wallet holds, keyed by its decimal chain id. Built from the enum so
         * Robinhood (4663), HyperEVM (999) and any future network need no entry of their own.
         */
        private val EVM_CHAINS_BY_ID: Map<String, Chain> =
            Chain.entries.mapNotNull { chain -> chain.evmChainId()?.let { it to chain } }.toMap()
    }
}
