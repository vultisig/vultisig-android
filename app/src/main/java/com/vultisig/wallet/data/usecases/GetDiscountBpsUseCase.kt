package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Chain
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
import com.vultisig.wallet.data.utils.toUnit
import com.vultisig.wallet.ui.screens.settings.TierType
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import timber.log.Timber
import wallet.core.jni.CoinType

/**
 * Use case to calculate the discount in basis points (BPS) based on staked VULT (sVULT) balance.
 * Fetches the sVULT balance internally from the vault's Ethereum address. sVULT is a 1:1 wrapper of
 * VULT, so the tier thresholds stay identical to the raw VULT amounts.
 */
interface GetDiscountBpsUseCase {
    suspend operator fun invoke(vaultId: String, swapProvider: SwapProvider): Int

    /** True when the vault has staked at least the Silver-tier amount (>= 3000 sVULT). */
    suspend fun hasReachedSilverTier(vaultId: String): Boolean
}

internal class GetDiscountBpsUseCaseImpl
@Inject
constructor(
    private val vaultRepository: VaultRepository,
    private val balanceRepository: BalanceRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val tiersNFTRepository: TiersNFTRepository,
) : GetDiscountBpsUseCase {

    override suspend fun invoke(vaultId: String, swapProvider: SwapProvider): Int {
        if (!supportedProviders.contains(swapProvider)) {
            return NO_DISCOUNT_BPS
        }

        val balance = getStakedVultBalance(vaultId) ?: return NO_DISCOUNT_BPS
        val hasNFT = tiersNFTRepository.hasTierNFT(vaultId)

        val discount = getDiscountForBalance(balance)

        return if (!hasNFT) {
            discount
        } else {
            discount.getNextDiscount()
        }
    }

    override suspend fun hasReachedSilverTier(vaultId: String): Boolean {
        val balance = getStakedVultBalance(vaultId) ?: return false
        return balance >= SILVER_TIER_THRESHOLD
    }

    /**
     * Reads the vault's staked VULT (sVULT) balance via `balanceOf` on the sVULT contract. sVULT is
     * not held as a vault coin, so the balance is fetched fresh on-chain rather than from cache.
     */
    suspend fun getStakedVultBalance(vaultId: String): BigInteger? {
        try {
            val vault = vaultRepository.get(vaultId) ?: return null

            val (address, _) = chainAccountAddressRepository.getAddress(Chain.Ethereum, vault)

            return balanceRepository.getTokenValue(address, sVultCoin).first().value
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e)
            return null
        }
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
        /** Staked VULT (sVULT) contract on Ethereum — a 1:1 wrapper of VULT. */
        private const val SVULT_CONTRACT_ADDRESS = "0x11113d7311FB8584a6e82BB126aA11D92e5fB39B"

        /**
         * sVULT is not a vault coin; derive a synthetic coin from VULT (same chain/decimals) with
         * the sVULT ticker and contract so its `balanceOf` is read and cached independently. Shared
         * with the Discount Tiers screen so the displayed tier matches the applied discount.
         */
        val sVultCoin =
            Coins.Ethereum.VULT.copy(ticker = "sVULT", contractAddress = SVULT_CONTRACT_ADDRESS)

        // Discount amounts in basis points
        const val NO_DISCOUNT_BPS = 0
        const val BRONZE_DISCOUNT_BPS = 5
        const val SILVER_DISCOUNT_BPS = 10
        const val GOLD_DISCOUNT_BPS = 20
        const val PLATINUM_DISCOUNT_BPS = 25

        const val DIAMOND_DISCOUNT_BPS = 35
        const val ULTIMATE_DISCOUNT_BPS = 50

        val BRONZE_TIER_THRESHOLD = CoinType.ETHEREUM.toUnit("1500".toBigInteger())
        val SILVER_TIER_THRESHOLD = CoinType.ETHEREUM.toUnit("3000".toBigInteger())
        val GOLD_TIER_THRESHOLD = CoinType.ETHEREUM.toUnit("7500".toBigInteger())
        val PLATINUM_TIER_THRESHOLD = CoinType.ETHEREUM.toUnit("15000".toBigInteger())
        val DIAMOND_TIER_THRESHOLD = CoinType.ETHEREUM.toUnit("100000".toBigInteger())
        val ULTIMATE_TIER_THRESHOLD = CoinType.ETHEREUM.toUnit("1000000".toBigInteger())

        private val supportedProviders =
            setOf(
                SwapProvider.THORCHAIN,
                SwapProvider.MAYA,
                SwapProvider.ONEINCH,
                SwapProvider.LIFI,
                SwapProvider.KYBER,
                SwapProvider.SWAPKIT,
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
