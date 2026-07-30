package com.vultisig.wallet.data.utils

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class ChartDownsamplerTest {

    @Test
    fun `returns the original list unchanged when already within bounds`() {
        val points = (1..10).toList()

        assertEquals(points, downsampleChartPoints(points, maxPoints = 300))
    }

    @Test
    fun `caps the result at maxPoints`() {
        val points = (1..5_000).toList()

        val result = downsampleChartPoints(points, maxPoints = 300)

        assertTrue(result.size <= 300)
    }

    @Test
    fun `always preserves the first and last point`() {
        val points = (1..5_000).toList()

        val result = downsampleChartPoints(points, maxPoints = 300)

        assertEquals(1, result.first())
        assertEquals(5_000, result.last())
    }

    @Test
    fun `handles the boundary where size equals maxPoints plus one`() {
        val points = (1..301).toList()

        val result = downsampleChartPoints(points, maxPoints = 300)

        assertEquals(300, result.size)
        assertEquals(1, result.first())
        assertEquals(301, result.last())
    }

    @Test
    fun `always includes the exact first and last point for awkward (size, maxPoints) pairs`() {
        // The generator's index formula for i=0 and i=maxPoints-1 relies on IEEE-754 division
        // being exact for these products, not on an explicit overwrite — deliberately awkward
        // (non-power-of-two, prime-ish, off-by-one) pairs pin that this holds in general, not just
        // for the round numbers used elsewhere in this file.
        val cases =
            listOf(
                997 to 37,
                4_801 to 169, // CoinGecko's own documented raw/target sizes for ALL/1W
                1_001 to 300,
                2 to 2,
                3 to 2,
            )

        for ((size, maxPoints) in cases) {
            val points = (1..size).toList()

            val result = downsampleChartPoints(points, maxPoints)

            assertEquals(1, result.first(), "size=$size maxPoints=$maxPoints")
            assertEquals(size, result.last(), "size=$size maxPoints=$maxPoints")
        }
    }

    @Test
    fun `rejects a maxPoints below 2`() {
        try {
            downsampleChartPoints(listOf(1, 2, 3), maxPoints = 1)
            throw AssertionError("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
