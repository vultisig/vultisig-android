package com.vultisig.wallet.data.blockchain.cosmos.staking

import com.vultisig.wallet.data.models.Chain
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Fans out 4 LCD reads in parallel and folds them into the chain-wide [CosmosChainApyData] consumed
 * by per-validator APY display. Caches results per chain for 5 minutes — same TTL as the Windows
 * `useCosmosChainApyQuery` React Query hook.
 *
 * Fallback chain matches the Windows / iOS behavior:
 * 1. Try full LCD fan-out, compute APY, display.
 * 2. If any of the 4 LCD calls fails (timeout / 5xx / 404 / 501): fall back to the per-chain
 *    baseline — 0.125 for LUNA (the prior iOS constant), `null` for LUNC (no stable post-split
 *    baseline). The view layer hides the APY row when `null`.
 *
 * Port of iOS `CosmosStakingAPYResolver.swift` (vultisig-ios PR #4432).
 */
interface CosmosStakingAPYResolver {
    suspend fun chainApy(chain: Chain, stakingDenom: String): CosmosChainApyData?

    fun baselineFallback(chain: Chain): BigDecimal?

    companion object {
        /**
         * Per-validator multiplier — `(1 - communityTax) × (inflation / bondedRatio) × (1 -
         * commission)`. Collapses to `null` when inflation or bonded ratio is zero so the view
         * layer can hide the row.
         */
        fun computeValidatorAPY(
            chainData: CosmosChainApyData,
            commission: BigDecimal,
        ): BigDecimal? {
            val inflation = clamp01(chainData.inflation)
            val bondedRatio = chainData.bondedRatio
            if (inflation <= BigDecimal.ZERO || bondedRatio <= BigDecimal.ZERO) return null
            val communityTax = clamp01(chainData.communityTax)
            val commissionClamped = clamp01(commission)
            val chainBase =
                BigDecimal.ONE.subtract(communityTax)
                    .multiply(inflation.divide(bondedRatio, 18, RoundingMode.HALF_UP))
            val apy = chainBase.multiply(BigDecimal.ONE.subtract(commissionClamped))
            return if (apy > BigDecimal.ZERO) apy else null
        }

        private fun clamp01(value: BigDecimal): BigDecimal =
            when {
                value < BigDecimal.ZERO -> BigDecimal.ZERO
                value > BigDecimal.ONE -> BigDecimal.ONE
                else -> value
            }
    }
}

@Singleton
internal class CosmosStakingAPYResolverImpl
@Inject
constructor(private val cosmosStakingService: CosmosStakingService) : CosmosStakingAPYResolver {

    /**
     * Wall clock + TTL are `internal var` so unit tests can pin them deterministically. Dagger
     * ignores Kotlin default-valued constructor params, so they are intentionally NOT in the
     * `@Inject` constructor — production uses the defaults.
     */
    internal var clock: () -> Long = { System.currentTimeMillis() }
    internal var ttlMillis: Long = 5L * 60L * 1000L

    private data class CachedEntry(val data: CosmosChainApyData, val fetchedAt: Long)

    private val mutex = Mutex()
    private val cache = mutableMapOf<Chain, CachedEntry>()
    /**
     * Coalesces concurrent callers for the same chain into a single in-flight LCD fan-out — the
     * second caller awaits the first deferred instead of re-issuing the 4 GETs. Mirrors iOS
     * `inFlight: [Chain: Task]` behavior.
     */
    private val inFlight = mutableMapOf<Chain, CompletableDeferred<CosmosChainApyData?>>()

    override suspend fun chainApy(chain: Chain, stakingDenom: String): CosmosChainApyData? {
        // Cache hit, then in-flight coalescing, then fresh fan-out — all under the mutex.
        // Ownership is decided inside the mutex so only the creator runs the fan-out; an identity
        // check after the lock would be true for every coalescing caller too (they share the same
        // deferred reference) and let duplicate 4-GET fan-outs slip through.
        var owns = false
        val deferred: CompletableDeferred<CosmosChainApyData?> =
            mutex.withLock {
                cache[chain]?.let { entry ->
                    if (clock() - entry.fetchedAt < ttlMillis) {
                        return entry.data
                    }
                }
                inFlight[chain]?.let {
                    return@withLock it
                }
                val newDeferred = CompletableDeferred<CosmosChainApyData?>()
                inFlight[chain] = newDeferred
                owns = true
                newDeferred
            }

        // Only the caller that created the deferred performs the fan-out.
        if (owns) {
            val result =
                try {
                    fanOut(chain, stakingDenom)
                } catch (e: Throwable) {
                    Timber.w(
                        e,
                        "Chain APY fan-out failed for %s — falling back to baseline",
                        chain.raw,
                    )
                    null
                }
            mutex.withLock {
                inFlight.remove(chain)
                if (result != null) cache[chain] = CachedEntry(result, clock())
            }
            deferred.complete(result)
        }
        return deferred.await()
    }

    /** 12.5% for LUNA, `null` for LUNC. */
    override fun baselineFallback(chain: Chain): BigDecimal? =
        when (chain) {
            Chain.Terra -> BigDecimal("0.125")
            else -> null
        }

    private suspend fun fanOut(chain: Chain, stakingDenom: String): CosmosChainApyData =
        coroutineScope {
            val inflationDeferred = async {
                cosmosStakingService.fetchMintInflation(chain).inflation.toBigDecimalOrZero()
            }
            val poolDeferred = async {
                cosmosStakingService.fetchStakingPool(chain).pool.bondedTokens.toBigDecimalOrZero()
            }
            val supplyDeferred = async {
                cosmosStakingService
                    .fetchBankSupplyByDenom(chain, stakingDenom)
                    .amount
                    .amount
                    .toBigDecimalOrZero()
            }
            val paramsDeferred = async {
                cosmosStakingService
                    .fetchDistributionParams(chain)
                    .params
                    .communityTax
                    .toBigDecimalOrZero()
            }
            val inflation = inflationDeferred.await()
            val pool = poolDeferred.await()
            val supply = supplyDeferred.await()
            val params = paramsDeferred.await()
            val bondedRatio =
                if (supply > BigDecimal.ZERO) clamp01(pool.divide(supply, 18, RoundingMode.HALF_UP))
                else BigDecimal.ZERO
            CosmosChainApyData(
                inflation = clamp01(inflation),
                bondedRatio = bondedRatio,
                communityTax = clamp01(params),
            )
        }

    private fun String.toBigDecimalOrZero(): BigDecimal = toBigDecimalOrNull() ?: BigDecimal.ZERO

    private fun clamp01(value: BigDecimal): BigDecimal =
        when {
            value < BigDecimal.ZERO -> BigDecimal.ZERO
            value > BigDecimal.ONE -> BigDecimal.ONE
            else -> value
        }
}
