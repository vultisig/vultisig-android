package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.Transaction

interface SecurityScannerTransactionFactoryContract {
    suspend fun createSecurityScannerTransaction(transaction: Transaction): SecurityScannerTransaction
}