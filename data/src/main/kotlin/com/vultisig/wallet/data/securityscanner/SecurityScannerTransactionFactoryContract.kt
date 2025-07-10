package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.Transaction

interface SecurityScannerTransactionFactoryContract {
    fun createSecurityScannerTransaction(transaction: Transaction): SecurityScannerTransaction
}