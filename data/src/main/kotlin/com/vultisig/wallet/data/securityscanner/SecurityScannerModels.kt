package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.Chain
import java.math.BigInteger

data class SecurityScannerTransaction(
    val chain: Chain,
    val type: SecurityTransactionType,
    val from: String,
    val to: String,
    val amount: BigInteger = BigInteger.ZERO,
    val data: String = "",
)

data class SecurityScannerMetadata(
    val requestId: String = "",
    val classification: String = "",
    val resultType: String = "",
)

enum class SecurityTransactionType {
    COIN_TRANSFER,
    TOKEN_TRANSFER,
    SWAP,
    APPROVAL,
    SMART_CONTRACT,
}

data class SecurityScannerResult(
    val provider: String,
    val isSecure: Boolean,
    val riskLevel: SecurityRiskLevel,
    val warnings: List<SecurityWarning>,
    val recommendations: String,
    val metadata: SecurityScannerMetadata = SecurityScannerMetadata(),
)

data class SecurityWarning(
    val type: SecurityWarningType,
    val severity: SecuritySeverity,
    val message: String,
    val details: String?,
)

enum class SecuritySeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL,
}

enum class SecurityRiskLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

enum class SecurityWarningType {
    SUSPICIOUS_CONTRACT,
    HIGH_VALUE_TRANSFER,
    UNKNOWN_TOKEN,
    PHISHING_ATTEMPT,
    MALICIOUS_CONTRACT,
    UNUSUAL_ACTIVITY,
    RUG_PULL_RISK,
    SANDWICH_ATTACK,
    FRONT_RUNNING,
    OTHER,
}

enum class SecurityScannerFeaturesType {
    SCAN_TRANSACTION,
}

data class SecurityScannerSupport(
    val provider: String,
    val feature: List<Feature>,
) {
    data class Feature(
        val chains: List<Chain>,
        val featureType: SecurityScannerFeaturesType,
    )
}