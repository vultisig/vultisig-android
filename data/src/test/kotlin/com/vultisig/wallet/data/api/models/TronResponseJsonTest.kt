package com.vultisig.wallet.data.api.models

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * TRON never combines bandwidth pools (#5892): a transfer must fit in one pool alone.
 */
internal class TronResponseJsonTest {

    @Test
    fun `available bandwidth is not the sum of both pools`() {
        val resource = TronAccountResourceJson(
            freeNetLimit = 600, freeNetUsed = 400, // 200 free left
            netLimit = 400, netUsed = 200, // 200 staked left
        )

        val stats = resource.calculateResourceStats()

        assertEquals(200, stats.availableBandwidth)
        assertEquals(1000, stats.totalBandwidth)
    }

    @Test
    fun `free pool alone can cover the transfer`() {
        val resource = TronAccountResourceJson(
            freeNetLimit = 600, freeNetUsed = 0,
            netLimit = 0, netUsed = 0,
        )

        val stats = resource.calculateResourceStats()

        assertEquals(600, stats.availableBandwidth)
        assertEquals(600, stats.totalBandwidth)
    }

    @Test
    fun `staked pool alone can cover the transfer`() {
        val resource = TronAccountResourceJson(
            freeNetLimit = 0, freeNetUsed = 0,
            netLimit = 600, netUsed = 0,
        )

        val stats = resource.calculateResourceStats()

        assertEquals(600, stats.availableBandwidth)
        assertEquals(600, stats.totalBandwidth)
    }
}
