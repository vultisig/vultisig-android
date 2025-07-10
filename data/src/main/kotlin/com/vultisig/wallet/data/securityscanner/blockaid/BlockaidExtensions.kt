package com.vultisig.wallet.data.securityscanner.blockaid

import com.vultisig.wallet.data.securityscanner.SecurityRiskLevel
import com.vultisig.wallet.data.securityscanner.SecurityScannerException
import com.vultisig.wallet.data.securityscanner.SecurityScannerMetadata
import com.vultisig.wallet.data.securityscanner.SecurityScannerResult
import com.vultisig.wallet.data.securityscanner.SecurityWarning
import timber.log.Timber

fun BlockaidTransactionScanResponseJson.toSolanaSecurityScannerResult(provider: String): SecurityScannerResult {
    when {
        status.equals("error", ignoreCase = true) || !error.isNullOrEmpty() -> {
            throw SecurityScannerException("SecurityScanner Error: ${error ?: "Unknown error"}. Payload: $this")
        }

        result == null -> {
            throw SecurityScannerException("SecurityScanner Invalid response: 'result' is null. Payload: $this")
        }

        else -> {
            val riskLevel = this.result.toSolanaValidationRiskLevel()
            val isSecure = riskLevel == SecurityRiskLevel.NONE || riskLevel == SecurityRiskLevel.LOW
            val description: String? = if (isSecure) {
                null
            } else {
                this.result.validation.features
                    .take(3)
                    .joinToString("\n")
                    .takeIf { it.isNotEmpty() }
            }

            val warnings = this.result.validation.extendedFeatures.map { extendedFeature ->
                SecurityWarning(
                    type = extendedFeature.type.toWarningType(),
                    message = extendedFeature.description,
                    severity = "",
                    details = null,
                )
            }

            return SecurityScannerResult(
                provider = provider,
                isSecure = isSecure,
                riskLevel = riskLevel,
                warnings = warnings,
                description = description,
                recommendations = "",
            )
        }
    }
}

fun BlockaidTransactionScanResponseJson.toSecurityScannerResult(provider: String): SecurityScannerResult {
    val riskLevel = this.toValidationRiskLevel()
    val securityWarnings = validation?.features?.map { feature ->
        SecurityWarning(
            type = feature.type.toWarningType(),
            severity = feature.featureId,
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
        description = validation?.description,
        metadata = SecurityScannerMetadata(
            requestId = requestId ?: "",
            classification = validation?.classification ?: "",
            resultType = validation?.resultType ?: "",
        )
    )
}

private fun BlockaidTransactionScanResponseJson.BlockaidSolanaResultJson.toSolanaValidationRiskLevel(): SecurityRiskLevel {
    val resultType = validation.resultType
    val features = validation.features

    val isBenign = resultType.equals("benign", ignoreCase = true) && features.isEmpty()

    if (isBenign) return SecurityRiskLevel.NONE

    return resultType.toWarningType()
}

private fun BlockaidTransactionScanResponseJson.toValidationRiskLevel(): SecurityRiskLevel {
    val hasFeatures = validation?.features?.isEmpty() == false
    val classification = validation?.classification
    val validationStatus = validation?.status
    val globalStatus = status
    val resultType = validation?.resultType

    if (validationStatus.equals("error", true)
        || resultType.equals("error", true)
        || globalStatus.equals("error", ignoreCase = true)
    ) {
        val errorMessage = validation?.error ?: "Scanning failed"
        throw SecurityScannerException("SecurityScanner $errorMessage , payload: $this")
    }

    val isBenign = validationStatus.equals("success", ignoreCase = true) &&
            resultType.equals("benign", ignoreCase = true) &&
            !hasFeatures
    if (isBenign) return SecurityRiskLevel.NONE

    val label = resultType ?: classification

    return label.toWarningType()
}

private fun String?.toWarningType(): SecurityRiskLevel {
    return when (this?.lowercase()) {
        "benign", "info" -> SecurityRiskLevel.LOW
        "warning", "spam" -> SecurityRiskLevel.MEDIUM
        "malicious" -> SecurityRiskLevel.CRITICAL
        else -> {
            if (this != null) {
                Timber.w("SecurityScanner: Unknown risk classification: $this")
            }
            SecurityRiskLevel.MEDIUM
        }
    }
}

private fun String.toRecommendations(): String {
    return when (this.lowercase()) {
        "malicious" -> "This transaction is flagged as malicious. Do not proceed."
        "warning" -> "This transaction has been flagged with warnings. Review carefully before proceeding."
        "spam" -> "This transaction appears to be spam. Consider avoiding it."
        else -> ""
    }
}