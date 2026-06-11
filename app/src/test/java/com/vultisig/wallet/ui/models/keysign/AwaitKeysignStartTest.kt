@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class AwaitKeysignStartTest {

    private val timeout = 2.minutes
    private val pollInterval = 1.seconds

    @Test
    fun `returns Started immediately when the ceremony has already begun`() = runTest {
        var calls = 0

        val outcome =
            awaitKeysignStart(timeout = timeout, pollInterval = pollInterval) {
                calls++
                true
            }

        assertEquals(KeysignStartOutcome.Started, outcome)
        assertEquals(1, calls)
        assertEquals(0L, currentTime) // no polling delay consumed
    }

    @Test
    fun `keeps polling until the ceremony starts`() = runTest {
        var calls = 0

        val outcome =
            awaitKeysignStart(timeout = timeout, pollInterval = pollInterval) {
                calls++
                calls >= 3 // false, false, then true
            }

        assertEquals(KeysignStartOutcome.Started, outcome)
        assertEquals(3, calls)
        // Two failed checks → two poll intervals waited before success.
        assertEquals(2 * pollInterval.inWholeMilliseconds, currentTime)
    }

    @Test
    fun `times out when the initiator never starts`() = runTest {
        var calls = 0

        val outcome =
            awaitKeysignStart(timeout = timeout, pollInterval = pollInterval) {
                calls++
                false // never starts
            }

        assertEquals(KeysignStartOutcome.TimedOut, outcome)
        // The wait is bounded by the timeout rather than spinning forever.
        assertEquals(timeout.inWholeMilliseconds, currentTime)
    }
}
