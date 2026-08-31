package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.TiersNFTRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.BRONZE_DISCOUNT_BPS
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.DIAMOND_DISCOUNT_BPS
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.GOLD_DISCOUNT_BPS
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.PLATINUM_DISCOUNT_BPS
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.SILVER_DISCOUNT_BPS
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.ULTIMATE_DISCOUNT_BPS
import com.vultisig.wallet.data.utils.SimpleCache
import com.vultisig.wallet.ui.screens.settings.TierType
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Use case to calculate the discount in basis points (BPS) based on VULT token balance. Fetches the
 * VULT balance internally from the vault.
 */
interface GetDiscountBpsUseCase {
    suspend operator fun invoke(vaultId: String, swapProvider: SwapProvider): Int

    /** True when the vault holds at least the Silver-tier VULT amount (>= 3000 VULT). */
    suspend fun hasReachedSilverTier(vaultId: String): Boolean

    /** The vault's VULT balance in raw token units (18 decimals), or null if unavailable. */
    suspend fun getVultBalance(vaultId: String): BigInteger?
}

internal class GetDiscountBpsUseCaseImpl
@Inject
constructor(
    private val vaultRepository: VaultRepository,
    private val balanceRepository: BalanceRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val tiersNFTRepository: TiersNFTRepository,
) : GetDiscountBpsUseCase {

    // A quote fetch asks for the discount once per swap provider candidate, all at the same time,
    // so a vault with no cached VULT row would fire one duplicate eth_call per candidate. Share a
    // single live read per vault behind a lock and a short-lived cache; the null of a failed read
    // is cached too, so a failure does not retry once per candidate either.
    private val liveVultBalanceCache = SimpleCache<String, LiveVultBalance>(LIVE_BALANCE_TTL_MS)

    private val liveVultBalanceLocks = ConcurrentHashMap<String, Mutex>()

    private fun lockFor(vaultId: String) = liveVultBalanceLocks.computeIfAbsent(vaultId) { Mutex() }

    private class LiveVultBalance(val value: BigInteger?)

    override suspend fun invoke(vaultId: String, swapProvider: SwapProvider): Int {
        if (!supportedProviders.contains(swapProvider)) {
            return NO_DISCOUNT_BPS
        }

        val balance = getVultBalance(vaultId) ?: return NO_DISCOUNT_BPS
        val hasNFT = tiersNFTRepository.hasTierNFT(vaultId)

        val discount = getDiscountForBalance(balance)

        return if (!hasNFT) {
            discount
        } else {
            discount.getNextDiscount()
        }
    }

    override suspend fun hasReachedSilverTier(vaultId: String): Boolean {
        val balance = getVultBalance(vaultId) ?: return false
        return balance >= SILVER_TIER_THRESHOLD
    }

    override suspend fun getVultBalance(vaultId: String): BigInteger? {
        try {
            val vault = vaultRepository.get(vaultId) ?: return null

            val (address, derivedPublicKey) =
                chainAccountAddressRepository.getAddress(Chain.Ethereum, vault)

            // The VULT the vault holds does not depend on Ethereum being one of its enabled
            // chains, so fall back to an in-memory coin when it isn't in the coin list. It is only
            // used to read the balance: nothing here persists it or makes it visible in the vault.
            val vultCoin =
                vault.coins.find { it.id == Coins.Ethereum.VULT.id }
                    ?: Coins.Ethereum.VULT.copy(address = address, hexPublicKey = derivedPublicKey)

            val cachedBalance =
                balanceRepository
                    .getCachedTokenBalances(listOf(address), listOf(vultCoin))
                    .find { it.coinId == Coins.Ethereum.VULT.id }
                    ?.tokenBalance
                    ?.tokenValue
                    ?.value

            // A missing cache entry is not a zero balance — a vault that never refreshed VULT would
            // otherwise be shown a fabricated 0. Read it live instead; that read fills the cache,
            // so later calls take the cached path again, and a failed read stays null (fail
            // closed).
            return cachedBalance ?: getLiveBalance(vaultId, address, vultCoin)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e)
            return null
        }
    }

    private suspend fun getLiveBalance(vaultId: String, address: String, coin: Coin): BigInteger? =
        lockFor(vaultId).withLock {
            liveVultBalanceCache
                .getOrPut(vaultId) {
                    LiveVultBalance(balanceRepository.getBalanceOrNull(address, coin))
                }
                .value
        }

    fun getDiscountForBalance(vultBalance: BigInteger): Int {
        return when {
            vultBalance >= ULTIMATE_TIER_THRESHOLD -> ULTIMATE_DISCOUNT_BPS
            vultBalance >= DIAMOND_TIER_THRESHOLD -> DIAMOND_DISCOUNT_BPS
            vultBalance >= PLATINUM_TIER_THRESHOLD -> PLATINUM_DISCOUNT_BPS
            vultBalance >= GOLD_TIER_THRESHOLD -> GOLD_DISCOUNT_BPS
            vultBalance >= SILVER_TIER_THRESHOLD -> SILVER_DISCOUNT_BPS
            vultBalance >= BRONZE_TIER_THRESHOLD -> BRONZE_DISCOUNT_BPS
            else -> NO_DISCOUNT_BPS
        }
    }

    private fun Int.getNextDiscount(): Int {
        return when (this) {
            NO_DISCOUNT_BPS -> BRONZE_DISCOUNT_BPS
            BRONZE_DISCOUNT_BPS -> SILVER_DISCOUNT_BPS
            SILVER_DISCOUNT_BPS -> GOLD_DISCOUNT_BPS
            GOLD_DISCOUNT_BPS -> PLATINUM_DISCOUNT_BPS
            // starting from PLATINUM NFT has no effect
            else -> this
        }
    }

    companion object {
        // Long enough to cover the concurrent candidates of one quote fetch, short enough that a
        // refresh still sees a fresh balance.
        private const val LIVE_BALANCE_TTL_MS = 12 * 1000L

        // Discount amounts in basis points
        const val NO_DISCOUNT_BPS = 0
        const val BRONZE_DISCOUNT_BPS = 5
        const val SILVER_DISCOUNT_BPS = 10
        const val GOLD_DISCOUNT_BPS = 20
        const val PLATINUM_DISCOUNT_BPS = 25

        const val DIAMOND_DISCOUNT_BPS = 35
        const val ULTIMATE_DISCOUNT_BPS = 50

        // VULT has 18 decimals; scale whole-token thresholds to raw units with pure BigInteger
        // math so they stay usable from unit tests (avoids the TrustWallet Core native lib).
        private val VULT_UNIT = BigInteger.TEN.pow(Coins.Ethereum.VULT.decimal)
        val BRONZE_TIER_THRESHOLD = "1500".toBigInteger() * VULT_UNIT
        val SILVER_TIER_THRESHOLD = "3000".toBigInteger() * VULT_UNIT
        val GOLD_TIER_THRESHOLD = "7500".toBigInteger() * VULT_UNIT
        val PLATINUM_TIER_THRESHOLD = "15000".toBigInteger() * VULT_UNIT
        val DIAMOND_TIER_THRESHOLD = "100000".toBigInteger() * VULT_UNIT
        val ULTIMATE_TIER_THRESHOLD = "1000000".toBigInteger() * VULT_UNIT

        private val supportedProviders =
            setOf(
                SwapProvider.THORCHAIN,
                SwapProvider.MAYA,
                SwapProvider.ONEINCH,
                SwapProvider.LIFI,
                SwapProvider.KYBER,
                SwapProvider.SWAPKIT,
                // Jupiter now charges a VULT-scaled affiliate fee too (#5053), so the holder's tier
                // discount must reach it — without this the Jupiter candidate always pays full bps.
                SwapProvider.JUPITER,
            )
    }
}

internal fun Int.getTierType() =
    when (this) {
        BRONZE_DISCOUNT_BPS -> TierType.BRONZE
        SILVER_DISCOUNT_BPS -> TierType.SILVER
        GOLD_DISCOUNT_BPS -> TierType.GOLD
        PLATINUM_DISCOUNT_BPS -> TierType.PLATINUM
        DIAMOND_DISCOUNT_BPS -> TierType.DIAMOND
        ULTIMATE_DISCOUNT_BPS -> TierType.ULTIMATE
        else -> null
    }
