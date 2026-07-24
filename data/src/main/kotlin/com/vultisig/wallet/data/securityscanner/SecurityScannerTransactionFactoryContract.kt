package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.models.Transaction

interface SecurityScannerTransactionFactoryContract {
    suspend fun createSecurityScannerTransaction(
        transaction: Transaction
    ): SecurityScannerTransaction

    suspend fun createSecurityScannerTransaction(
        transaction: SwapTransaction
    ): SecurityScannerTransaction

    /**
     * Screens a swap's external recipient on the destination chain, independently of whether the
     * source-chain swap transaction itself can be scanned.
     */
    fun createRecipientSecurityScannerTransaction(
        transaction: SwapTransaction
    ): SecurityScannerTransaction
}
