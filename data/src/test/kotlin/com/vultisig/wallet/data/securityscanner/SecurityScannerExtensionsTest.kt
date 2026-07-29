package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.Chain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SecurityScannerExtensionsTest {

    private val transaction =
        SecurityScannerTransaction(
            chain = Chain.Bitcoin,
            type = SecurityTransactionType.COIN_TRANSFER,
            from = "bc1qsender",
            to = "bc1qrecipient",
        )

    @Test
    fun `runSecurityScan returns the block result on success`() = runTest {
        val expected =
            SecurityScannerResult(
                provider = "blockaid",
                isSecure = true,
                riskLevel = SecurityRiskLevel.NONE,
                warnings = emptyList(),
                description = null,
                recommendations = "",
            )

        val result = runSecurityScan(transaction) { expected }

        assertSame(expected, result)
    }

    /**
     * The whole point of #5432: a scan failure (transport error, or a mapper throwing
     * [SecurityScannerException] for a provider-signaled error status) must propagate so the caller
     * can surface a distinct scan-unavailable state, never a fabricated risk finding.
     */
    @Test
    fun `runSecurityScan rethrows as SecurityScannerException instead of fabricating a result`() =
        runTest {
            val cause = SecurityScannerException("SecurityScanner Error: 502 Bad Gateway")

            val thrown =
                assertThrows<SecurityScannerException> {
                    runSecurityScan(transaction) { throw cause }
                }

            assertSame(cause, thrown.cause)
            assertEquals(Chain.Bitcoin, thrown.chain)
        }

    @Test
    fun `runSecurityScan rethrows CancellationException unwrapped`() = runTest {
        assertThrows<CancellationException> {
            runSecurityScan(transaction) { throw CancellationException("scope cancelled") }
        }
    }
}
