package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.Chain
import timber.log.Timber

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
        val errorMessage = "SecurityScanner: Error scanning ${transaction.chain.name}"
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

fun List<SecurityScannerSupport>.isChainSupported(chain: Chain): Boolean {
    return any { support -> support.feature.any { feature -> chain in feature.chains } }
}
