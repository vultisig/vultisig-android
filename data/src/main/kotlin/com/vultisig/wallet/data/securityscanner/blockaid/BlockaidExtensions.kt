package com.vultisig.wallet.data.securityscanner.blockaid

import com.vultisig.wallet.data.securityscanner.SecurityRiskLevel
import com.vultisig.wallet.data.securityscanner.SecurityScannerException
import com.vultisig.wallet.data.securityscanner.SecurityScannerMetadata
import com.vultisig.wallet.data.securityscanner.SecurityScannerResult
import com.vultisig.wallet.data.securityscanner.SecuritySeverity
import com.vultisig.wallet.data.securityscanner.SecurityWarning
import com.vultisig.wallet.data.securityscanner.SecurityWarningType

fun BlockaidTransactionScanResponse.toSecurityScannerResult(provider: String): SecurityScannerResult {
    val riskLevel = this.toValidationRiskLevel()
    val securityWarnings = validation?.features?.map { feature ->
        SecurityWarning(
            type = feature.type.toWarningType(),
            severity = (feature.severity ?: "medium").toSecuritySeverity(),
            message = feature.description,
            details = feature.address
        )
    } ?: emptyList()
    val recommendations = validation?.classification?.toRecommendations() ?: ""
    val isSecure = riskLevel == SecurityRiskLevel.NONE || riskLevel == SecurityRiskLevel.LOW

    return SecurityScannerResult(
        provider = provider,
        riskLevel = riskLevel,
        warnings = securityWarnings,
        isSecure = isSecure,
        recommendations = recommendations,
        metadata = SecurityScannerMetadata(
            requestId = requestId ?: "",
            classification = validation?.classification ?: "",
            resultType = validation?.resultType ?: "",
        )
    )
}

private fun BlockaidTransactionScanResponse.toValidationRiskLevel(): SecurityRiskLevel {
    val hasFeatures = validation?.features?.isEmpty() == false
    val classification = validation?.classification
    val status = validation?.status
    val resultType = validation?.resultType

    if (status.equals("error", true)
        || resultType.equals("error", true)
    ) {
        val errorMessage = validation?.error ?: "Scanning failed"
        throw SecurityScannerException("SecurityScanner $errorMessage , payload: $this")
    }

    val isBenign = status.equals("success", ignoreCase = true) &&
            resultType.equals("benign", ignoreCase = true) &&
            !hasFeatures
    if (isBenign) return SecurityRiskLevel.NONE

    val label = classification?.takeIf { it.isNotBlank() } ?: resultType

    return when (label?.lowercase()) {
        "benign" -> SecurityRiskLevel.LOW
        "warning", "spam" -> SecurityRiskLevel.MEDIUM
        "malicious" -> SecurityRiskLevel.CRITICAL
        else -> SecurityRiskLevel.MEDIUM
    }
}

private fun String.toWarningType(): SecurityWarningType {
    return when (this) {
        "malicious_contract" -> SecurityWarningType.MALICIOUS_CONTRACT
        "suspicious_contract" -> SecurityWarningType.SUSPICIOUS_CONTRACT
        "phishing" -> SecurityWarningType.PHISHING_ATTEMPT
        "high_value" -> SecurityWarningType.HIGH_VALUE_TRANSFER
        "unknown_token" -> SecurityWarningType.UNKNOWN_TOKEN
        "rug_pull" -> SecurityWarningType.RUG_PULL_RISK
        "sandwich_attack" -> SecurityWarningType.SANDWICH_ATTACK
        else -> SecurityWarningType.OTHER
    }
}

private fun String.toSecuritySeverity(): SecuritySeverity {
    return when (this.lowercase()) {
        "low" -> SecuritySeverity.INFO
        "medium" -> SecuritySeverity.WARNING
        "high" -> SecuritySeverity.ERROR
        "critical" -> SecuritySeverity.CRITICAL
        else -> SecuritySeverity.WARNING
    }
}

private fun String.toRecommendations(): String {
    return when (this.lowercase()) {
        "malicious" -> "⚠️ This transaction is flagged as malicious. Do not proceed."
        "warning" -> "⚠️ This transaction has been flagged with warnings. Review carefully before proceeding."
        "spam" -> "This transaction appears to be spam. Consider avoiding it."
        else -> ""
    }
}