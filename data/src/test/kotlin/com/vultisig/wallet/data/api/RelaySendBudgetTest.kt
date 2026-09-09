package com.vultisig.wallet.data.api

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Test

/**
 * Pins the arithmetic that makes the awaited relay send safe to introduce at all (issue #5813).
 *
 * The send used to be fire-and-forget, so how long it took was nobody's problem. Now the ceremony
 * waits on it, and a send that outlives the 60 s stall restarts this party while its peers are
 * still waiting on the old session — the exact failure the change is meant to remove.
 */
class RelaySendBudgetTest {

    private val stallLimit = 60.seconds

    @Test
    fun `default worst case stays inside the ceremony stall limit`() {
        val budget = RelaySendBudget()

        // 4 attempts x 8 s, plus 1 s + 2 s + 4 s of backoff between them.
        assertEquals(39.seconds, budget.worstCase)
        assertTrue(
            budget.worstCase < stallLimit,
            "send budget ${budget.worstCase} must stay under the $stallLimit stall",
        )
    }

    @Test
    fun `backoff doubles from one second`() {
        val budget = RelaySendBudget()

        assertEquals(1.seconds, budget.backoffAfter(0))
        assertEquals(2.seconds, budget.backoffAfter(1))
        assertEquals(4.seconds, budget.backoffAfter(2))
    }

    @Test
    fun `a single attempt has no backoff to add`() {
        val budget = RelaySendBudget(maxAttempts = 1)

        assertEquals(8.seconds, budget.worstCase)
    }

    @Test
    fun `maxAttempts below one is rejected`() {
        assertFailsWith<IllegalArgumentException> { RelaySendBudget(maxAttempts = 0) }
    }

    @Test
    fun `without a deadline every request gets the full timeout`() {
        val budget = RelaySendBudget()

        assertEquals(8_000L, budget.requestTimeoutMillisAt(nowNanos = 0))
    }

    @Test
    fun `a request is clipped to what is left of the deadline`() {
        val now = 1_000_000_000L
        val budget = RelaySendBudget(deadlineNanos = now + 3.seconds.inWholeNanoseconds)

        assertEquals(3_000L, budget.requestTimeoutMillisAt(now))
    }

    @Test
    fun `a request keeps the full timeout when the deadline is further out`() {
        val now = 1_000_000_000L
        val budget = RelaySendBudget(deadlineNanos = now + 30.seconds.inWholeNanoseconds)

        assertEquals(8_000L, budget.requestTimeoutMillisAt(now))
    }

    /**
     * Null is the caller's signal to abandon the send rather than start a request nobody awaits.
     */
    @Test
    fun `a passed deadline yields no request timeout at all`() {
        val now = 1_000_000_000L
        val budget = RelaySendBudget(deadlineNanos = now - 1)

        assertNull(budget.requestTimeoutMillisAt(now))
    }

    @Test
    fun `a sub-millisecond remainder still asks for a real request`() {
        val now = 1_000_000_000L
        val budget = RelaySendBudget(deadlineNanos = now + 500)

        assertEquals(1L, budget.requestTimeoutMillisAt(now))
    }

    @Test
    fun `a backoff that fits inside the deadline is allowed`() {
        val now = 1_000_000_000L
        val budget = RelaySendBudget(deadlineNanos = now + 5.seconds.inWholeNanoseconds)

        assertTrue(budget.backoffFitsAt(1.seconds, now))
    }

    @Test
    fun `a backoff that would overshoot the deadline is refused`() {
        val now = 1_000_000_000L
        val budget = RelaySendBudget(deadlineNanos = now + 500.milliseconds.inWholeNanoseconds)

        assertFalse(budget.backoffFitsAt(4.seconds, now))
    }

    @Test
    fun `without a deadline every backoff fits`() {
        assertTrue(RelaySendBudget().backoffFitsAt(4.seconds, nowNanos = 0))
    }
}
