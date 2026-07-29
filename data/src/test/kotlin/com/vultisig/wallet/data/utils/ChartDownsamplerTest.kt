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
    fun `rejects a maxPoints below 2`() {
        try {
            downsampleChartPoints(listOf(1, 2, 3), maxPoints = 1)
            throw AssertionError("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
