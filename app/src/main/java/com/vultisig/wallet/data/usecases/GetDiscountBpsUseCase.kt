package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.toUnit
import timber.log.Timber
import wallet.core.jni.CoinType
import java.math.BigInteger
import javax.inject.Inject

/**
 * Use case to calculate the discount in basis points (BPS) based on VULT token balance.
 * Fetches the VULT balance internally from the vault.
 * 
 * Tier structure:
 * - Bronze: 1,000+ VULT = 10 BPS discount
 * - Silver: 2,500+ VULT = 20 BPS discount  
 * - Gold: 5,000+ VULT = 30 BPS discount
 * - Platinum: 10,000+ VULT = 35 BPS discount
 */
interface GetDiscountBpsUseCase {
    suspend operator fun invoke(vaultId: String, swapProvider: SwapProvider): Int
}

internal class GetDiscountBpsUseCaseImpl @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val balanceRepository: BalanceRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
) : GetDiscountBpsUseCase {
    
    override suspend fun invoke(vaultId: String, swapProvider: SwapProvider): Int {
        if (!supportedProviders.contains(swapProvider)) {
            return NO_DISCOUNT_BPS
        }

        val balance = getVultBalance(vaultId) ?: return NO_DISCOUNT_BPS
        return getDiscountForBalance(balance)
    }
    
    suspend fun getVultBalance(vaultId: String): BigInteger? {
        try {
            val vault = vaultRepository.get(vaultId) ?: return null

            val vultCoin = vault.coins.find { it.id == Coins.Ethereum.VULT.id } ?: return null
            
            val (address, _) = chainAccountAddressRepository.getAddress(
                Chain.Ethereum,
                vault
            )
            
            val tokenBalance = balanceRepository.getCachedTokenBalances(
                listOf(address),
                listOf(vultCoin),
            ).find { it.coinId == Coins.Ethereum.VULT.id }?.tokenBalance?.tokenValue?.value
                ?: BigInteger.ZERO
            
            return tokenBalance
        } catch (e: Exception) {
            Timber.e(e)
            return null
        }
    }


    fun getDiscountForBalance(vultBalance: BigInteger): Int {
        return when {
            vultBalance >= PLATINUM_TIER_THRESHOLD -> PLATINUM_DISCOUNT_BPS
            vultBalance >= GOLD_TIER_THRESHOLD -> GOLD_DISCOUNT_BPS
            vultBalance >= SILVER_TIER_THRESHOLD -> SILVER_DISCOUNT_BPS
            vultBalance >= BRONZE_TIER_THRESHOLD -> BRONZE_DISCOUNT_BPS
            else -> NO_DISCOUNT_BPS
        }
    }
    
    companion object {
        // Discount amounts in basis points
        const val NO_DISCOUNT_BPS = 0
        const val BRONZE_DISCOUNT_BPS = 10
        const val SILVER_DISCOUNT_BPS = 20
        const val GOLD_DISCOUNT_BPS = 30
        const val PLATINUM_DISCOUNT_BPS = 35
        
        val BRONZE_TIER_THRESHOLD = CoinType.ETHEREUM.toUnit("1000".toBigInteger())
        val SILVER_TIER_THRESHOLD =  CoinType.ETHEREUM.toUnit("5000".toBigInteger())
        val GOLD_TIER_THRESHOLD =  CoinType.ETHEREUM.toUnit("10000".toBigInteger())
        val PLATINUM_TIER_THRESHOLD =  CoinType.ETHEREUM.toUnit("50000".toBigInteger())

        private val supportedProviders = setOf(
            SwapProvider.THORCHAIN,
            SwapProvider.MAYA,
            SwapProvider.ONEINCH,
            SwapProvider.LIFI,
        )
    }
}