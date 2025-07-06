package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.Chain
import java.math.BigInteger

data class SecurityScannerTransaction(
    val chain: Chain,
    val type: SecurityTransactionType,
    val from: String,
    val to: String,
    val amount: BigInteger = BigInteger.ZERO,
    val data: String = "0x",
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
    val description: String?,
    val recommendations: String,
    val metadata: SecurityScannerMetadata = SecurityScannerMetadata(),
)

data class SecurityWarning(
    val type: SecurityRiskLevel,
    val severity: String,
    val message: String,
    val details: String?,
)

enum class SecurityRiskLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
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