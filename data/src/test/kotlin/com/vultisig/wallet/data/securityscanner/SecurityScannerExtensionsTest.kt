package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.utils.NetworkErrorKind
import com.vultisig.wallet.data.utils.NetworkException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import timber.log.Timber

/**
 * Covers [runSecurityScan]'s failure path: on any non-cancellation exception it must rethrow as
 * [SecurityScannerException] (#5432 — so the caller surfaces a distinct scan-unavailable state
 * instead of a fabricated risk finding), while logging Blockaid's HTTP status and response body
 * when available (#5431 — so the next rejection report carries the provider's `detail` field
 * instead of it only being implicitly present inside the exception's stack trace).
 */
internal class SecurityScannerExtensionsTest {

    private data class LogEntry(val message: String?, val t: Throwable?)

    /**
     * Timber appends the throwable's stack trace to the message before handing it to the tree, so
     * the exact string [runSecurityScan] built is only the first line.
     */
    private fun LogEntry.firstLine(): String? = message?.substringBefore('\n')

    private val logEntries = mutableListOf<LogEntry>()

    private val capturingTree =
        object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                logEntries.add(LogEntry(message, t))
            }
        }

    @BeforeEach
    fun setUp() {
        logEntries.clear()
        Timber.plant(capturingTree)
    }

    @AfterEach
    fun tearDown() {
        Timber.uproot(capturingTree)
    }

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

    @Test
    fun `an HTTP error response logs the status code and response body`() = runTest {
        val networkException = NetworkException(400, "{\"detail\":\"invalid transaction\"}")

        assertThrows<SecurityScannerException> {
            runSecurityScan(transaction) { throw networkException }
        }

        val logged = logEntries.single { it.t === networkException }
        assertEquals(
            "SecurityScanner: Error scanning ${Chain.Bitcoin.name} transaction " +
                "(HTTP 400: {\"detail\":\"invalid transaction\"})",
            logged.firstLine(),
        )
    }

    @Test
    fun `a transport failure with no HTTP response is labelled distinctly from HTTP 0`() = runTest {
        val transportException =
            NetworkException(0, "Unable to resolve host", NetworkErrorKind.NoConnectivity, null)

        assertThrows<SecurityScannerException> {
            runSecurityScan(transaction) { throw transportException }
        }

        val logged = logEntries.single { it.t === transportException }
        assertEquals(
            "SecurityScanner: Error scanning ${Chain.Bitcoin.name} transaction " +
                "(transport error: Unable to resolve host)",
            logged.firstLine(),
        )
    }

    @Test
    fun `a non-network exception logs without a network detail suffix`() = runTest {
        val exception = IllegalStateException("boom")

        assertThrows<SecurityScannerException> { runSecurityScan(transaction) { throw exception } }

        val logged = logEntries.single { it.t === exception }
        assertEquals(
            "SecurityScanner: Error scanning ${Chain.Bitcoin.name} transaction",
            logged.firstLine(),
        )
    }
}
