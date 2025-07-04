package com.vultisig.wallet.data.securityscanner

import timber.log.Timber

internal suspend fun <T> runSecurityScan(
    transaction: SecurityScannerTransaction,
    block: suspend () -> T
): T {
    Timber.d("SecurityScanner: Scanning ${transaction.chain.name} transaction: $transaction")
    return try {
        block()
    } catch (t: Throwable) {
        val errorMessage = "SecurityScanner: Error scanning ${transaction.chain.name}"
        Timber.e(t, errorMessage)
        throw SecurityScannerException(errorMessage, t, transaction.chain, transaction.toString())
    }
}