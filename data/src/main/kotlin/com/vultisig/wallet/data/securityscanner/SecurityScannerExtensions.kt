package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.Chain
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Executes [block] as a security scan for [transaction], logging and rethrowing as
 * [SecurityScannerException] on any non-cancellation failure so the caller can surface a distinct
 * scan-unavailable state instead of a fabricated risk finding.
 */
internal suspend fun runSecurityScan(
    transaction: SecurityScannerTransaction,
    block: suspend () -> SecurityScannerResult,
): SecurityScannerResult {
    Timber.d("SecurityScanner: Scanning ${transaction.chain.name} transaction: $transaction")
    return try {
        val result = block()
        Timber.d("SecurityScanner: Result for ${transaction.chain.name} transaction: $result")
        result
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        val errorMessage = "SecurityScanner: Error scanning ${transaction.chain.name}"
        Timber.e(t, errorMessage)
        throw SecurityScannerException(errorMessage, t, transaction.chain, transaction.toString())
    }
}

/** Returns true if any [SecurityScannerSupport] entry in this list covers the given [chain]. */
fun List<SecurityScannerSupport>.isChainSupported(chain: Chain): Boolean {
    return any { support -> support.feature.any { feature -> chain in feature.chains } }
}
