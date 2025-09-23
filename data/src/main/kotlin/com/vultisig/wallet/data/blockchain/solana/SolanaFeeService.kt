package com.vultisig.wallet.data.blockchain.solana

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.blockchain.BasicFee
import com.vultisig.wallet.data.blockchain.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.Transfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

// TODO: WIP (Refactor)
class SolanaFeeService @Inject constructor(
    private val solanaApi: SolanaApi,
) : FeeService {
    
    companion object {
        // Base fee per signature in lamports
        private const val BASE_FEE_PER_SIGNATURE = 5000L
        
        // Default priority fee in lamports if API fails
        private val DEFAULT_PRIORITY_FEE = BigInteger.valueOf(1_000_000L) // 0.001 SOL
        
        // Compute unit limits for different transaction types
        private const val COMPUTE_UNITS_NATIVE_TRANSFER = 600L
        private const val COMPUTE_UNITS_SPL_TRANSFER = 100_000L
        private const val COMPUTE_UNITS_SPL_WITH_ATA = 200_000L // When creating Associated Token Account
        private const val COMPUTE_UNITS_SWAP = 400_000L
        
        // Priority fee price in micro-lamports per compute unit
        private const val DEFAULT_PRIORITY_FEE_PRICE = 1_000_000L // 1 SOL per million compute units
        
        // Rent exemption for creating Associated Token Account (165 bytes)
        private const val ATA_RENT_EXEMPTION = 2_039_280L // ~0.00203928 SOL
    }
    
    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee = withContext(Dispatchers.IO) {
        require(transaction is Transfer) {
            "Invalid Transaction Type: ${transaction::class.simpleName}"
        }

        error("")
        /*try {
            val isNativeToken = transaction.coin.isNativeToken
            val fromAddress = transaction.coin.address
            
            // Get dynamic priority fee from network
            val priorityFee = fetchPriorityFee(fromAddress)
            
            // Calculate total fee based on transaction type
            val totalFee = calculateTotalFee(
                isNativeToken = isNativeToken,
                priorityFee = priorityFee,
                isSwap = false,
                needsAtaCreation = false // For simplicity, assuming ATA exists
            )
            
            Timber.d("Solana fee calculated: $totalFee lamports (${totalFee.divide(BigInteger.valueOf(1_000_000_000))} SOL)")
            
            BasicFee(totalFee)
        } catch (e: Exception) {
            Timber.e(e, "Error calculating Solana fee, using default")
            calculateDefaultFees(transaction)
        } */
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        TODO("Not yet implemented")
    }

    private suspend fun fetchPriorityFee(address: String): BigInteger {
        return try {
            val priorityFeeString = solanaApi.getHighPriorityFee(address)
            val priorityFee = BigInteger(priorityFeeString)
            
            // Ensure we have at least the default priority fee
            maxOf(priorityFee, DEFAULT_PRIORITY_FEE)
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch priority fee, using default")
            DEFAULT_PRIORITY_FEE
        }
    }

    //TODO: Simulate
    fun getComputeUnits(
        isNativeToken: Boolean,
        isSwap: Boolean,
        needsAtaCreation: Boolean
    ): Long {
        return when {
            isSwap -> COMPUTE_UNITS_SWAP
            !isNativeToken && needsAtaCreation -> COMPUTE_UNITS_SPL_WITH_ATA
            !isNativeToken -> COMPUTE_UNITS_SPL_TRANSFER
            else -> COMPUTE_UNITS_NATIVE_TRANSFER
        }
    }

    fun getPriorityFeePrice(priorityFeeLamports: BigInteger, computeUnits: Long): Long {
        // Convert lamports to micro-lamports per compute unit
        // Formula: (priorityFeeLamports * 1,000,000) / computeUnits
        val microLamports = priorityFeeLamports.multiply(BigInteger.valueOf(1_000_000))
        return microLamports.divide(BigInteger.valueOf(computeUnits)).toLong()
    }
}