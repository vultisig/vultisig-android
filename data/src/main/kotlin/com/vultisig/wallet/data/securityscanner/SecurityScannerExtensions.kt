package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.utils.NetworkException
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Executes [block] as a security scan for [transaction], returning a scan-unavailable MEDIUM result
 * on any non-cancellation failure instead of propagating the exception.
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
        // NetworkException.message is the raw response body; surfacing it with the status code
        // here (rather than relying on it merely showing up inside the stack trace) is what lets
        // the next Blockaid rejection report carry the provider's `detail` field.
        val networkDetail =
            (t as? NetworkException)
                ?.let { " (HTTP ${it.httpStatusCode}: ${it.message})" }
                .orEmpty()
        val errorMessage =
            "SecurityScanner: Error scanning ${transaction.chain.name} transaction$networkDetail"
        Timber.e(t, errorMessage)
        SecurityScannerResult(
            provider = "",
            isSecure = false,
            riskLevel = SecurityRiskLevel.MEDIUM,
            warnings = emptyList(),
            description = "Scan unavailable",
            recommendations = "",
        )
    }
}

/** Returns true if any [SecurityScannerSupport] entry in this list covers the given [chain]. */
fun List<SecurityScannerSupport>.isChainSupported(chain: Chain): Boolean {
    return any { support -> support.feature.any { feature -> chain in feature.chains } }
}
