package com.vultisig.wallet.data.keygen

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetryUtilsTest {

    @Test
    fun `succeeds on first attempt without delay`() = runTest {
        var calls = 0
        val result =
            retryWithDelay(3, 1000L) {
                calls++
                "ok"
            }
        assertEquals("ok", result)
        assertEquals(1, calls)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `retries and succeeds on second attempt`() = runTest {
        var calls = 0
        val result =
            retryWithDelay(3, 1000L) {
                calls++
                if (calls < 2) error("transient failure")
                "ok"
            }
        assertEquals("ok", result)
        assertEquals(2, calls)
    }

    @Test
    fun `retries and succeeds on last attempt`() = runTest {
        var calls = 0
        val result =
            retryWithDelay(3, 1000L) {
                calls++
                if (calls < 3) error("transient failure")
                "ok"
            }
        assertEquals("ok", result)
        assertEquals(3, calls)
    }

    @Test
    fun `throws after all retries exhausted`() = runTest {
        var calls = 0
        val exception =
            assertFailsWith<IllegalStateException> {
                retryWithDelay(3, 1000L) {
                    calls++
                    error("permanent failure")
                }
            }
        assertEquals(3, calls)
        assertEquals("permanent failure", exception.message)
    }

    @Test
    fun `throws last exception message on exhaustion`() = runTest {
        var calls = 0
        val exception =
            assertFailsWith<IllegalStateException> {
                retryWithDelay(3, 1000L) {
                    calls++
                    error("failure #$calls")
                }
            }
        assertEquals("failure #3", exception.message)
    }

    @Test
    fun `delays between retries but not after last failure`() = runTest {
        var calls = 0
        assertFailsWith<IllegalStateException> {
            retryWithDelay(3, 1000L) {
                calls++
                error("fail")
            }
        }
        // 2 delays: after attempt 1 and after attempt 2, no delay after attempt 3
        assertEquals(2000L, currentTime)
    }

    @Test
    fun `delay matches configured value`() = runTest {
        var calls = 0
        retryWithDelay(2, 500L) {
            calls++
            if (calls < 2) error("fail")
            "ok"
        }
        assertEquals(500L, currentTime)
    }

    @Test
    fun `single retry with maxRetries 1 does not delay`() = runTest {
        val result = retryWithDelay(1, 1000L) { "immediate" }
        assertEquals("immediate", result)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `single retry throws immediately on failure`() = runTest {
        assertFailsWith<IllegalStateException> { retryWithDelay(1, 1000L) { error("fail") } }
        assertEquals(0L, currentTime)
    }

    @Test
    fun `only catches IllegalStateException`() = runTest {
        assertFailsWith<RuntimeException> {
            retryWithDelay(3, 1000L) { throw RuntimeException("not retried") }
        }
    }

    @Test
    fun `retry constants match expected values`() {
        assertEquals(3, MldsaKeysign.FINISH_MAX_RETRIES)
        assertEquals(1000L, MldsaKeysign.FINISH_RETRY_DELAY_MS)
    }
}
