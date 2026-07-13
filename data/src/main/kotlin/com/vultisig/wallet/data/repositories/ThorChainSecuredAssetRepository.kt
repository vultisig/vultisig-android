package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * The catalog of THORChain secured assets, derived live from THORChain's `Available` pools so every
 * poolable L1 asset (BTC, ETH, ETH-USDC, GAIA.ATOM, …) is discoverable, not just the ones already
 * held.
 */
internal interface ThorChainSecuredAssetRepository {
    /** The current secured-asset catalog, refreshing from THORChain pools when stale. */
    suspend fun getSecuredAssetCoins(): List<Coin>
}

@Singleton
internal class ThorChainSecuredAssetRepositoryImpl
@Inject
constructor(private val thorChainApi: ThorChainApi) : ThorChainSecuredAssetRepository {

    @Volatile private var cache: List<Coin> = emptyList()
    @Volatile private var lastRefreshMs: Long = 0L
    private val mutex = Mutex()

    override suspend fun getSecuredAssetCoins(): List<Coin> {
        if (!isStale()) return cache
        mutex.withLock {
            if (!isStale()) return cache
            runCatching { fetch() }
                .onSuccess {
                    cache = it
                    lastRefreshMs = System.currentTimeMillis()
                }
                // Keep the last-good snapshot on failure.
                .onFailure {
                    Timber.w(it, "THORChain secured-asset pools refresh failed; keeping last-good")
                }
        }
        return cache
    }

    private fun isStale(): Boolean = System.currentTimeMillis() - lastRefreshMs >= CACHE_TTL_MS

    private suspend fun fetch(): List<Coin> =
        thorChainApi
            .getPools()
            .asSequence()
            .filter { it.status.equals(STATUS_AVAILABLE, ignoreCase = true) }
            .mapNotNull { toSecuredAssetCoin(it.asset) }
            .toList()

    /**
     * Converts a pool asset id (`BTC.BTC`, `ETH.USDC-0xA0b8…`) into its secured-asset [Coin]
     * (`btc-btc`, `eth-usdc-0xa0b8…`), or null when malformed or THOR-native (e.g. `THOR.TCY`,
     * which uses the `x/`-prefixed LP/staking convention, not the secured-asset one).
     */
    private fun toSecuredAssetCoin(poolAsset: String): Coin? {
        val dot = poolAsset.indexOf('.')
        if (dot <= 0 || dot >= poolAsset.length - 1) return null
        val chainPart = poolAsset.substring(0, dot)
        if (chainPart.equals("THOR", ignoreCase = true)) return null
        val rest = poolAsset.substring(dot + 1)
        val ticker = rest.substringBefore('-').uppercase()
        if (ticker.isBlank()) return null

        return Coin(
            chain = Chain.ThorChain,
            ticker = ticker,
            logo = ticker.lowercase(),
            address = "",
            // Every THORChain secured asset uses RUNE's 8-decimal precision, not the underlying
            // chain's native decimals.
            decimal = SECURED_ASSET_DECIMALS,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "${chainPart.lowercase()}-${rest.lowercase()}",
            isNativeToken = false,
        )
    }

    private companion object {
        const val STATUS_AVAILABLE = "available"
        const val SECURED_ASSET_DECIMALS = 8
        const val CACHE_TTL_MS = 5 * 60 * 1000L
    }
}
