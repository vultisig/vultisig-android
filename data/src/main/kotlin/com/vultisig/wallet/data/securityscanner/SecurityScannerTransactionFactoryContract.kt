package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.models.Transaction

interface SecurityScannerTransactionFactoryContract {
    suspend fun createSecurityScannerTransaction(transaction: Transaction): SecurityScannerTransaction
    suspend fun createSecurityScannerTransaction(transaction: SwapTransaction): SecurityScannerTransaction
}