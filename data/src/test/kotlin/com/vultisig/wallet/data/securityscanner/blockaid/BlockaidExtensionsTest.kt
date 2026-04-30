package com.vultisig.wallet.data.securityscanner.blockaid

import com.vultisig.wallet.data.securityscanner.SecurityRiskLevel
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test

class BlockaidExtensionsTest {

    @Test
    fun `toSecurityScannerResult does not throw when validation status is Error`() {
        val response = simulationErrorResponse("Simulation Error: execution reverted")

        val result = response.toSecurityScannerResult("blockaid")

        assertFalse(result.isSecure)
        assertEquals(SecurityRiskLevel.MEDIUM, result.riskLevel)
    }

    @Test
    fun `toSecurityScannerResult returns MEDIUM when only resultType is Error`() {
        val response =
            BlockaidTransactionScanResponseJson(
                requestId = null,
                accountAddress = null,
                status = null,
                validation =
                    BlockaidTransactionScanResponseJson.BlockaidValidationJson(
                        status = "Success",
                        classification = null,
                        resultType = "Error",
                        description = "",
                        reason = null,
                        features = emptyList(),
                        error = "Some error",
                    ),
                result = null,
                error = null,
            )

        val result = response.toSecurityScannerResult("blockaid")

        assertFalse(result.isSecure)
        assertEquals(SecurityRiskLevel.MEDIUM, result.riskLevel)
    }

    @Test
    fun `toSecurityScannerResult returns MEDIUM when global status is Error`() {
        val response =
            BlockaidTransactionScanResponseJson(
                requestId = null,
                accountAddress = null,
                status = "Error",
                validation = null,
                result = null,
                error = null,
            )

        val result = response.toSecurityScannerResult("blockaid")

        assertFalse(result.isSecure)
        assertEquals(SecurityRiskLevel.MEDIUM, result.riskLevel)
    }

    private fun simulationErrorResponse(errorMessage: String) =
        BlockaidTransactionScanResponseJson(
            requestId = null,
            accountAddress = null,
            status = null,
            validation =
                BlockaidTransactionScanResponseJson.BlockaidValidationJson(
                    status = "Error",
                    classification = null,
                    resultType = "Error",
                    description = "",
                    reason = null,
                    features = emptyList(),
                    error = errorMessage,
                ),
            result = null,
            error = null,
        )
}
