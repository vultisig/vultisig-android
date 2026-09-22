package com.vultisig.wallet.data.blockchain.ton

import com.vultisig.wallet.data.api.chains.ton.TonApi
import com.vultisig.wallet.data.api.chains.ton.TonStakingApi
import com.vultisig.wallet.data.api.chains.ton.tonUserFriendlyAddress
import com.vultisig.wallet.data.utils.SimpleCache
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * A vault's Tonstakers position: its tsTON balance and the jetton wallet that holds it, which is
 * where an unstake's burn is sent. [jettonWalletAddress] is null while the vault has never held
 * tsTON (no wallet is deployed), and the balance is then zero.
 */
data class TonLiquidPosition(val tsTonBalance: BigInteger, val jettonWalletAddress: String?) {
    val hasPosition: Boolean
        get() = tsTonBalance.signum() > 0
}

/**
 * What the pool says about itself. [totalBalance] over [supply] is the tsTON→TON rate; [apy] and
 * [minStake] come from tonapi's pool listing and are display/validation metadata, so either may be
 * missing on a partial read without blocking the position.
 */
data class TonLiquidPoolState(
    val totalBalance: BigInteger,
    val supply: BigInteger,
    val apy: Double?,
    val minStake: BigInteger?,
    val isDepositOpen: Boolean,
    val isOptimistic: Boolean,
) {
    /** TON (nanotons) the pool would pay for [tsTon] base units at the current rate, floored. */
    fun tonValueOf(tsTon: BigInteger): BigInteger =
        if (supply.signum() > 0) tsTon * totalBalance / supply else BigInteger.ZERO

    /** tsTON base units the pool mints for a net [depositAmount] (nanotons), floored. */
    fun tsTonFor(depositAmount: BigInteger): BigInteger =
        if (totalBalance.signum() > 0) depositAmount * supply / totalBalance else BigInteger.ZERO
}

/**
 * Reads the two halves of a Tonstakers position. The tsTON balance and jetton wallet come from the
 * indexer's jetton-wallet lookup — the same call a jetton transfer resolves its wallet with — and
 * the pool state from tonapi.
 *
 * Both are cached, because the DeFi screen reloads on every resume and is entered from the stake
 * and unstake forms: without this, backing out of a form paid for the whole read again. The TTLs
 * follow what the data can actually do in the window — a position only changes when the user signs
 * something (and a broadcast clears it early through [invalidate]), while the pool's rate and APY
 * move once a validation round, so its entry is shared by every vault. Reads are guarded per key
 * the way [com.vultisig.wallet.data.repositories.BalanceRepository] guards its own [SimpleCache]:
 * the cache is not thread-safe, and the lock doubles as in-flight coalescing, so the two callers
 * this has — the DeFi screen and the portfolio's balance service — make one request between them
 * rather than two.
 */
class TonLiquidStakingService
internal constructor(
    private val tonApi: TonApi,
    private val tonStakingApi: TonStakingApi,
    /**
     * The user-friendly bounceable spelling of a TON address, or null for one that will not
     * convert. A parameter because WalletCore's converter is a native call with no host-JVM binary.
     */
    private val toBounceable: (String) -> String?,
    timeSource: TimeSource,
) {

    @Inject
    constructor(
        tonApi: TonApi,
        tonStakingApi: TonStakingApi,
        timeSource: TimeSource,
    ) : this(tonApi, tonStakingApi, ::tonUserFriendlyAddress, timeSource)

    private val positionCache = SimpleCache<String, TonLiquidPosition>(POSITION_TTL, timeSource)
    private val poolStateCache = SimpleCache<String, TonLiquidPoolState>(POOL_STATE_TTL, timeSource)
    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun lockFor(key: String): Mutex = locks.computeIfAbsent(key) { Mutex() }

    /**
     * The vault's tsTON balance and jetton wallet. [forceRefresh] skips the cache for a pull to
     * refresh, which is the gesture that means "I know something changed".
     */
    suspend fun getPosition(
        ownerAddress: String,
        forceRefresh: Boolean = false,
    ): TonLiquidPosition =
        lockFor(POSITION_KEY_PREFIX + ownerAddress).withLock {
            if (forceRefresh) positionCache.remove(ownerAddress)
            positionCache.get(ownerAddress)
                ?: fetchPosition(ownerAddress).also { positionCache.put(ownerAddress, it) }
        }

    /** Drops the cached position for [ownerAddress] — called when a broadcast may have moved it. */
    suspend fun invalidate(ownerAddress: String) {
        lockFor(POSITION_KEY_PREFIX + ownerAddress).withLock { positionCache.remove(ownerAddress) }
    }

    private suspend fun fetchPosition(ownerAddress: String): TonLiquidPosition {
        val wallet =
            tonApi
                .getJettonWallet(ownerAddress, Tonstakers.TSTON_MASTER_ADDRESS)
                .matchingWallet(Tonstakers.TSTON_MASTER_ADDRESS, toBounceable)
                ?: return TonLiquidPosition(BigInteger.ZERO, null)
        // The burn has to reach the wallet bounceable, whichever spelling the indexer used.
        val jettonWallet = toBounceable(wallet.address) ?: wallet.address
        return TonLiquidPosition(
            tsTonBalance = wallet.balance.toBigIntegerOrNull() ?: BigInteger.ZERO,
            jettonWalletAddress = jettonWallet,
        )
    }

    /**
     * The pool's rate and gates, or null when the get-method read failed — without the rate a
     * position has no value and a stake no preview, so callers fail closed. The listing metadata
     * (APY, minimum) is best-effort on top.
     */
    suspend fun getPoolState(forceRefresh: Boolean = false): TonLiquidPoolState? =
        lockFor(POOL_STATE_KEY).withLock {
            if (forceRefresh) poolStateCache.remove(POOL_STATE_KEY)
            // A failed read is never cached: the callers fail closed on a null pool state, and
            // holding that answer would keep the form shut for the rest of the window.
            poolStateCache.get(POOL_STATE_KEY)
                ?: fetchPoolState()?.also { poolStateCache.put(POOL_STATE_KEY, it) }
        }

    private suspend fun fetchPoolState(): TonLiquidPoolState? = coroutineScope {
        val fullData = async { tonStakingApi.getLiquidPoolData(Tonstakers.POOL_ADDRESS) }
        val listing = async {
            try {
                tonStakingApi.getStakingPool(Tonstakers.POOL_ADDRESS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Tonstakers pool listing unavailable")
                null
            }
        }
        val data = fullData.await() ?: return@coroutineScope null
        val info = listing.await()
        TonLiquidPoolState(
            totalBalance = BigInteger.valueOf(data.totalBalance),
            supply = BigInteger.valueOf(data.supply),
            apy = info?.apy,
            minStake = info?.minStake?.takeIf { it > 0 }?.let(BigInteger::valueOf),
            isDepositOpen = data.isDepositOpen,
            isOptimistic = data.isOptimistic,
        )
    }

    private companion object {
        /**
         * A position moves only when the vault signs, and a broadcast invalidates it early, so this
         * only has to cover a stake made on another device. Matches the DeFi balance cache.
         */
        val POSITION_TTL = 12.seconds

        /**
         * The rate is a function of the pool's balance over its supply and the APY of the round,
         * both of which turn over once per validation round (~18 h).
         */
        val POOL_STATE_TTL = 1.minutes

        const val POOL_STATE_KEY = "tonstakers-pool-state"
        /** Keeps a wallet address from ever colliding with [POOL_STATE_KEY] in the lock map. */
        const val POSITION_KEY_PREFIX = "position:"
    }
}
