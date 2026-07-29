package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.utils.NetworkException
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Executes [block] as a security scan for [transaction], logging and rethrowing as
 * [SecurityScannerException] on any non-cancellation exception so the caller can surface a distinct
 * scan-unavailable state instead of a fabricated risk finding. Fatal JVM errors ([Error], e.g.
 * [OutOfMemoryError]) are not caught and propagate immediately.
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
    } catch (t: Exception) {
        if (t is CancellationException) throw t
        // NetworkException.message is the raw response body; surfacing it with the status code
        // here (rather than relying on it merely showing up inside the stack trace) is what lets
        // the next Blockaid rejection report carry the provider's `detail` field. httpStatusCode
        // == 0 means no HTTP response was received at all (see NetworkException's kdoc), so that
        // case is labelled as a transport failure rather than a misleading "HTTP 0".
        val networkDetail =
            (t as? NetworkException)
                ?.let {
                    if (it.httpStatusCode > 0) {
                        " (HTTP ${it.httpStatusCode}: ${it.message})"
                    } else {
                        " (transport error: ${it.message})"
                    }
                }
                .orEmpty()
        val errorMessage =
            "SecurityScanner: Error scanning ${transaction.chain.name} transaction$networkDetail"
        Timber.e(t, errorMessage)
        throw SecurityScannerException(errorMessage, t, transaction.chain, transaction.toString())
    }
}

/** Returns true if any [SecurityScannerSupport] entry in this list covers the given [chain]. */
fun List<SecurityScannerSupport>.isChainSupported(chain: Chain): Boolean {
    return any { support -> support.feature.any { feature -> chain in feature.chains } }
}
