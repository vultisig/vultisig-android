package com.vultisig.wallet.data.securityscanner.blockaid

import com.vultisig.wallet.data.securityscanner.SecurityScannerException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BlockaidExtensionsTest {

    /**
     * A BTC send with 50+ inputs makes Blockaid's own node gateway time out; Blockaid still answers
     * HTTP 200 but with `validation.status: "Error"` (#5432). This must surface as a
     * scan-unavailable failure, never as a real MEDIUM risk finding.
     */
    @Test
    fun `toSecurityScannerResult throws when validation status is Error`() {
        val response = simulationErrorResponse("Simulation Error: execution reverted")

        assertThrows<SecurityScannerException> { response.toSecurityScannerResult("blockaid") }
    }

    /** Verifies that an Error resultType alone throws instead of returning a fake risk level. */
    @Test
    fun `toSecurityScannerResult throws when only resultType is Error`() {
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

        assertThrows<SecurityScannerException> { response.toSecurityScannerResult("blockaid") }
    }

    /** Verifies that a global status Error throws instead of returning a fake risk level. */
    @Test
    fun `toSecurityScannerResult throws when global status is Error`() {
        val response =
            BlockaidTransactionScanResponseJson(
                requestId = null,
                accountAddress = null,
                status = "Error",
                validation = null,
                result = null,
                error = null,
            )

        assertThrows<SecurityScannerException> { response.toSecurityScannerResult("blockaid") }
    }

    /**
     * Cross-path invariant: the EVM/BTC/SUI mapper ([toSecurityScannerResult]) and the Solana
     * mapper ([toSolanaSecurityScannerResult]) must agree on the same signal — a provider-signaled
     * error status must never be classified as a real risk level on either path. Solana's response
     * shape only carries the error signal at the top level (no nested `validation.status`), so the
     * two fixtures mirror that shape difference while asserting the identical outcome.
     */
    @Test
    fun `both EVM and Solana mappers throw for a provider-signaled error status`() {
        val evmResponse = simulationErrorResponse("Simulation Error: execution reverted")
        val solanaResponse =
            BlockaidTransactionScanResponseJson(
                requestId = null,
                accountAddress = null,
                status = "Error",
                validation = null,
                result = null,
                error = "Simulation Error: execution reverted",
            )

        assertThrows<SecurityScannerException> { evmResponse.toSecurityScannerResult("blockaid") }
        assertThrows<SecurityScannerException> {
            solanaResponse.toSolanaSecurityScannerResult("blockaid")
        }
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
