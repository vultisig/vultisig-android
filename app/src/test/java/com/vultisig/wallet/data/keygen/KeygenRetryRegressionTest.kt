package com.vultisig.wallet.data.keygen

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class KeygenRetryRegressionTest {

    @Test
    fun `routing can separate shared setup from exchange namespace`() {
        val routing = KeygenRouting.from(exchangeMessageId = "ecdsa")

        assertEquals(KeygenRouting(exchangeMessageId = "ecdsa", setupMessageId = null), routing)
    }

    @Test
    fun `routing reuses one namespace for setup and exchange when requested`() {
        val routing = KeygenRouting.from(setupMessageId = "mldsa")

        assertEquals(KeygenRouting(exchangeMessageId = "mldsa", setupMessageId = "mldsa"), routing)
    }

    @Test
    fun `retry helper rethrows cancellation without retrying`() = runTest {
        var retryCount = 0

        assertFailsWith<CancellationException> {
            runKeygenWithRetry(
                attempt = 0,
                retry = { _, _ ->
                    retryCount += 1
                    Unit
                },
            ) {
                throw CancellationException("cancelled")
            }
        }
        assertEquals(0, retryCount)
    }

    @Test
    fun `retry helper retries regular failures up to the next attempt`() = runTest {
        val attempts = mutableListOf<Int>()

        val result =
            runKeygenWithRetry(
                attempt = 0,
                retry = { nextAttempt, _ ->
                    attempts += nextAttempt
                    if (nextAttempt == 1) "recovered" else error("unexpected retry")
                },
            ) {
                error("boom")
            }

        assertEquals(listOf(1), attempts)
        assertEquals("recovered", result)
    }
}
