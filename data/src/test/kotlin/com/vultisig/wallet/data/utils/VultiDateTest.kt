package com.vultisig.wallet.data.utils

import java.time.YearMonth
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

internal class VultiDateTest {

    // Regression test: ChronoUnit.MONTHS.between(Instant, Instant) throws
    // UnsupportedTemporalTypeException at runtime, since Instant only supports time-based
    // units. getEpochMonth() must diff calendar dates, not instants.
    @Test
    fun `getEpochMonth does not throw and returns months since the Unix epoch`() {
        val result = assertDoesNotThrow { VultiDate.getEpochMonth() }

        val now = YearMonth.now()
        val expected = (now.year - 1970) * 12 + (now.monthValue - 1)

        assertEquals(expected, result)
    }
}
