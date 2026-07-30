package com.vultisig.wallet.data.utils

import com.vultisig.wallet.data.models.MarketChartPoint
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class MarketChartDecoderTest {

    @Test
    fun `decodes well-formed price pairs`() {
        val raw = listOf(listOf(1_000.0, 100.5), listOf(2_000.0, 101.25))

        val points = decodeMarketChartPoints(raw)

        assertEquals(
            listOf(
                MarketChartPoint(1_000L, BigDecimal.valueOf(100.5)),
                MarketChartPoint(2_000L, BigDecimal.valueOf(101.25)),
            ),
            points,
        )
    }

    @Test
    fun `returns empty list for an empty input`() {
        assertTrue(decodeMarketChartPoints(emptyList()).isEmpty())
    }

    @Test
    fun `drops short sub-arrays instead of crashing`() {
        val raw = listOf(listOf(1_000.0), listOf(2_000.0, 101.25), emptyList())

        val points = decodeMarketChartPoints(raw)

        assertEquals(listOf(MarketChartPoint(2_000L, BigDecimal.valueOf(101.25))), points)
    }

    @Test
    fun `sorts out-of-order pairs ascending by timestamp`() {
        val raw = listOf(listOf(3_000.0, 103.0), listOf(1_000.0, 101.0), listOf(2_000.0, 102.0))

        val points = decodeMarketChartPoints(raw)

        assertEquals(listOf(1_000L, 2_000L, 3_000L), points.map { it.timestampMillis })
    }

    @Test
    fun `changePercent is zero for fewer than two points`() {
        assertEquals(0.0, changePercent(emptyList()))
        assertEquals(0.0, changePercent(listOf(MarketChartPoint(1L, BigDecimal.TEN))))
    }

    @Test
    fun `changePercent is zero when the first price is zero`() {
        val points =
            listOf(MarketChartPoint(1L, BigDecimal.ZERO), MarketChartPoint(2L, BigDecimal.TEN))

        assertEquals(0.0, changePercent(points))
    }

    @Test
    fun `changePercent reflects a positive move from first to last point`() {
        val points =
            listOf(
                MarketChartPoint(1L, BigDecimal("100")),
                MarketChartPoint(2L, BigDecimal("50")),
                MarketChartPoint(3L, BigDecimal("110")),
            )

        assertEquals(10.0, changePercent(points))
    }

    @Test
    fun `changePercent reflects a negative move from first to last point`() {
        val points =
            listOf(MarketChartPoint(1L, BigDecimal("100")), MarketChartPoint(2L, BigDecimal("90")))

        assertEquals(-10.0, changePercent(points))
    }
}
