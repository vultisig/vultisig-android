package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.CosmosApi
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.models.DenomMetadata
import com.vultisig.wallet.data.api.models.cosmos.CosmosBalance
import com.vultisig.wallet.data.api.models.cosmos.CosmosIbcDenomTraceDenomTraceJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.utils.NetworkException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

/**
 * Auto-discovers bank-denom holdings on Terra and Terra Classic.
 *
 * Companion to [EvmCoinFinder]: the chain-detail screen and the background token-refresh worker
 * both route through `TokenRepository.getTokensWithBalance`, which delegates here for chains in
 * [SUPPORTED_CHAINS]. For every entry returned by `/cosmos/bank/v1beta1/balances/{addr}` we
 * surface, in order of preference:
 * 1. the curated [Coins] entry whose `contractAddress` matches the denom — preserves logo and
 *    `priceProviderID`;
 * 2. a coin built from `/cosmos/bank/v1beta1/denoms_metadata/{denom}` — ticker from the symbol or
 *    display, decimals from the matching `denom_units` exponent;
 * 3. for `ibc/HASH` vouchers without chain metadata: the trace's `base_denom`, plus a metadata
 *    lookup for that base denom so decimals stay correct when the chain advertises them;
 * 4. otherwise a coin with a ticker derived from the denom string and the Cosmos-default six
 *    decimals.
 *
 * Metadata and IBC traces are immutable per chain, so both are cached in-memory for 24h. Failures
 * are logged at debug level — a transient LCD blip falls through to the fallback derivation and is
 * retried on the next refresh.
 *
 * Tracks vultisig/vultisig-android#4500 and the parallel iOS work in vultisig/vultisig-ios#4366.
 */
interface CosmosBankCoinFinder {
    suspend fun find(chain: Chain, address: String): List<Coin>
}

internal class CosmosBankCoinFinderImpl
@Inject
constructor(private val cosmosApiFactory: CosmosApiFactory) : CosmosBankCoinFinder {

    private val metadataCache = ConcurrentHashMap<DenomKey, CacheEntry<DenomMetadata>>()
    private val traceCache =
        ConcurrentHashMap<DenomKey, CacheEntry<CosmosIbcDenomTraceDenomTraceJson>>()

    override suspend fun find(chain: Chain, address: String): List<Coin> {
        if (chain !in SUPPORTED_CHAINS) return emptyList()

        val api = cosmosApiFactory.createCosmosApi(chain)
        val discoverable =
            fetchBalances(chain, api, address)?.filterNot {
                it.denom.equals(chain.feeUnit, ignoreCase = true)
            } ?: return emptyList()

        return coroutineScope {
            discoverable
                .map { balance -> async { resolveCoin(chain, api, balance.denom) } }
                .awaitAll()
        }
    }

    private suspend fun fetchBalances(
        chain: Chain,
        api: CosmosApi,
        address: String,
    ): List<CosmosBalance>? =
        try {
            api.getBalance(address)
        } catch (e: SocketTimeoutException) {
            Timber.e(e, "Cosmos bank balances timed out for %s", chain.id)
            null
        } catch (e: NetworkException) {
            Timber.e(e, "Cosmos bank balances failed for %s: status=%d", chain.id, e.httpStatusCode)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Cosmos bank balances failed for %s", chain.id)
            null
        }

    private suspend fun resolveCoin(chain: Chain, api: CosmosApi, denom: String): Coin =
        preferCurated(chain, denom)
            ?: cachedMetadata(chain, api, denom)?.let {
                buildCoin(chain, denom, metadata = it, fallbackTicker = denom.toCosmosTicker())
            }
            ?: resolveIbcVoucher(chain, api, denom)
            ?: buildCoin(chain, denom, metadata = null, fallbackTicker = denom.toCosmosTicker())

    private fun preferCurated(chain: Chain, denom: String): Coin? =
        Coins.coins[chain]?.firstOrNull { it.contractAddress.equals(denom, ignoreCase = true) }

    private suspend fun resolveIbcVoucher(chain: Chain, api: CosmosApi, denom: String): Coin? {
        if (!denom.startsWith(IBC_DENOM_PREFIX)) return null
        val trace = cachedTrace(chain, api, denom) ?: return null
        val baseMeta = cachedMetadata(chain, api, trace.baseDenom)
        return buildCoin(
            chain = chain,
            denom = denom,
            metadata = baseMeta,
            fallbackTicker = trace.baseDenom.toCosmosTicker(),
        )
    }

    private fun buildCoin(
        chain: Chain,
        denom: String,
        metadata: DenomMetadata?,
        fallbackTicker: String,
    ): Coin =
        Coin(
            chain = chain,
            ticker = metadata?.symbolOrDisplay() ?: fallbackTicker,
            logo = "",
            address = "",
            decimal = metadata?.decimalsFromUnits() ?: COSMOS_DEFAULT_DECIMALS,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = denom,
            isNativeToken = false,
        )

    private suspend fun cachedMetadata(
        chain: Chain,
        api: CosmosApi,
        denom: String,
    ): DenomMetadata? = cached(metadataCache, chain, denom) { api.getDenomMetadata(denom) }

    private suspend fun cachedTrace(
        chain: Chain,
        api: CosmosApi,
        denom: String,
    ): CosmosIbcDenomTraceDenomTraceJson? =
        cached(traceCache, chain, denom) {
            try {
                api.getIbcDenomTraces(denom)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.d(e, "IBC denom trace fetch failed for %s on %s", denom, chain.id)
                null
            }
        }

    private suspend fun <V : Any> cached(
        cache: ConcurrentHashMap<DenomKey, CacheEntry<V>>,
        chain: Chain,
        denom: String,
        fetch: suspend () -> V?,
    ): V? {
        val key = DenomKey(chain.id, denom)
        val now = System.currentTimeMillis()
        cache[key]
            ?.takeIf { it.expiresAt > now }
            ?.let {
                return it.value
            }
        // Cache only successful lookups. A `null` here is indistinguishable between a true 404 and
        // a transient LCD failure the API method swallowed — pinning either for 24h would freeze
        // the outage into the session, so retry on the next refresh instead.
        return fetch()?.also { cache[key] = CacheEntry(it, now + CACHE_TTL_MILLIS) }
    }

    private fun DenomMetadata.symbolOrDisplay(): String? =
        symbol?.takeIf { it.isNotEmpty() } ?: display?.takeIf { it.isNotEmpty() }

    private fun DenomMetadata.decimalsFromUnits(): Int? {
        val units = denomUnits ?: return null
        val byName =
            listOfNotNull(symbol, display)
                .filter { it.isNotEmpty() }
                .firstNotNullOfOrNull { name ->
                    units.firstOrNull { it.denom == name }?.exponent?.takeIf { it > 0 }
                }
        // The max fallback accepts `0` for genuine zero-decimal denoms — only the named lookup
        // skips zero, where matching the base unit instead of the display unit signals malformed
        // metadata that should fall through to the max.
        return byName ?: units.mapNotNull { it.exponent }.maxOrNull()?.takeIf { it >= 0 }
    }

    /**
     * Best-effort ticker for denoms with no metadata or trace data. Mirrors the conventions used by
     * the THORChain auto-discovery in `TokenRepositoryImpl.deriveTicker`: factory subdenoms get the
     * trailing component, native units with a leading `u`/`a` get the prefix stripped, and IBC
     * vouchers fall back to a short hash prefix so they stay distinguishable.
     */
    private fun String.toCosmosTicker(): String =
        when {
            startsWith(IBC_DENOM_PREFIX) ->
                IBC_TICKER_PREFIX +
                    removePrefix(IBC_DENOM_PREFIX).take(IBC_HASH_PREVIEW_LENGTH).uppercase()
            startsWith(FACTORY_DENOM_PREFIX) ->
                substringAfterLast('/').stripDenomUnitPrefix().uppercase()
            else -> stripDenomUnitPrefix().uppercase()
        }

    private fun String.stripDenomUnitPrefix(): String =
        if (length > 1 && first() in DENOM_UNIT_PREFIXES && this[1].isLetter()) drop(1) else this

    private data class DenomKey(val chainId: String, val denom: String)

    private data class CacheEntry<T : Any>(val value: T, val expiresAt: Long)

    companion object {
        /** Chains the bank auto-discovery is enabled for; matches the ticket scope. */
        val SUPPORTED_CHAINS: Set<Chain> = setOf(Chain.Terra, Chain.TerraClassic)

        private const val IBC_DENOM_PREFIX = "ibc/"
        private const val FACTORY_DENOM_PREFIX = "factory/"
        private const val IBC_TICKER_PREFIX = "IBC-"
        private const val IBC_HASH_PREVIEW_LENGTH = 6
        private const val COSMOS_DEFAULT_DECIMALS = 6
        private val DENOM_UNIT_PREFIXES = setOf('u', 'a')
        private val CACHE_TTL_MILLIS = TimeUnit.HOURS.toMillis(24)
    }
}
