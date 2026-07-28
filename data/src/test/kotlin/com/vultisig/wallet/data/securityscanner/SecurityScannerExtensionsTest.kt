package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.utils.NetworkErrorKind
import com.vultisig.wallet.data.utils.NetworkException
import java.math.BigInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import timber.log.Timber

/**
 * Covers [runSecurityScan]'s failure-logging path (#5431's "Also needed": Blockaid's HTTP status
 * and response body must reach the log on scan failure, distinguishing a real HTTP error from a
 * transport failure that never got a response at all).
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
            amount = BigInteger.ZERO,
            data = "deadbeef",
        )

    @Test
    fun `a successful scan returns the block's result untouched`() = runBlocking {
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

    @Test
    fun `an HTTP error response logs the status code and response body`() = runBlocking {
        val networkException = NetworkException(400, "{\"detail\":\"invalid transaction\"}")

        runSecurityScan(transaction) { throw networkException }

        val logged = logEntries.single { it.t === networkException }
        assertEquals(
            "SecurityScanner: Error scanning ${Chain.Bitcoin.name} transaction " +
                "(HTTP 400: {\"detail\":\"invalid transaction\"})",
            logged.firstLine(),
        )
    }

    @Test
    fun `a transport failure with no HTTP response is labelled distinctly from HTTP 0`() =
        runBlocking {
            val transportException =
                NetworkException(0, "Unable to resolve host", NetworkErrorKind.NoConnectivity, null)

            runSecurityScan(transaction) { throw transportException }

            val logged = logEntries.single { it.t === transportException }
            assertEquals(
                "SecurityScanner: Error scanning ${Chain.Bitcoin.name} transaction " +
                    "(transport error: Unable to resolve host)",
                logged.firstLine(),
            )
        }

    @Test
    fun `a scan failure returns a scan-unavailable MEDIUM result instead of throwing`() =
        runBlocking {
            val result = runSecurityScan(transaction) { throw IllegalStateException("boom") }

            assertEquals(SecurityRiskLevel.MEDIUM, result.riskLevel)
            assertFalse(result.isSecure)
            assertEquals("Scan unavailable", result.description)
        }

    @Test
    fun `a non-network exception logs without a network detail suffix`() = runBlocking {
        val exception = IllegalStateException("boom")

        runSecurityScan(transaction) { throw exception }

        val logged = logEntries.single { it.t === exception }
        assertEquals(
            "SecurityScanner: Error scanning ${Chain.Bitcoin.name} transaction",
            logged.firstLine(),
        )
    }

    @Test
    fun `cancellation is rethrown instead of being reported as a scan failure`() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                runSecurityScan(transaction) { throw CancellationException("cancelled") }
            }
        }
    }
}
