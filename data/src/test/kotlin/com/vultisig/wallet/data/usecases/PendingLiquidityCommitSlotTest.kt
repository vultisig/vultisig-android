package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.usecases.PendingLiquidityCleanupSchedule.MayaChain
import com.vultisig.wallet.data.usecases.PendingLiquidityCleanupSchedule.THORChain
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class PendingLiquidityCommitSlotTest {

    // Store order is ARB.ETH, BTC.BTC, DASH.DASH, ETH.ETH: four pools, a slot every 25 blocks.
    private val pools = listOf("eth.eth", "BTC.BTC", "DASH.DASH", "ARB.ETH")

    private fun blocks(
        schedule: PendingLiquidityCleanupSchedule,
        pool: String,
        lastAddHeight: Long = 0,
        currentHeight: Long,
    ) =
        blocksUntilPendingLiquidityCommit(
            schedule = schedule,
            pool = pool,
            cleanedPools = pools,
            lastAddHeight = lastAddHeight,
            ageLimit = 100,
            currentHeight = currentHeight,
        )

    @Test
    fun `nothing commits until the deposit is strictly older than the age limit`() {
        // Aged out at 101; DASH.DASH's slots fall at 50 into each cycle on both nodes.
        assertEquals(140L, blocks(THORChain, "DASH.DASH", currentHeight = 10))
        assertEquals(140L, blocks(MayaChain, "DASH.DASH", currentHeight = 10))
    }

    @Test
    fun `the first pool in store order is cleaned only at the start of a cycle`() {
        assertEquals(190L, blocks(THORChain, "ARB.ETH", currentHeight = 10))
        assertEquals(190L, blocks(MayaChain, "ARB.ETH", currentHeight = 10))
    }

    @Test
    fun `an aged deposit whose slot has passed waits for the next one`() {
        // thornode: DASH.DASH only at 50 into the cycle, so 250. mayanode also cleans every pool
        // at the start of a cycle, so 200.
        assertEquals(90L, blocks(THORChain, "DASH.DASH", currentHeight = 160))
        assertEquals(40L, blocks(MayaChain, "DASH.DASH", currentHeight = 160))
    }

    @Test
    fun `mayanode cleans a later pool on every multiple of its step`() {
        // BTC.BTC is index 1: step 25, so 125 is its next slot after 101.
        assertEquals(115L, blocks(MayaChain, "BTC.BTC", currentHeight = 10))
        assertEquals(1L, blocks(MayaChain, "BTC.BTC", currentHeight = 124))
    }

    @Test
    fun `pool names match case-insensitively`() {
        assertEquals(
            blocks(MayaChain, "ETH.ETH", currentHeight = 10),
            blocks(MayaChain, "Eth.Eth", currentHeight = 10),
        )
    }

    @Test
    fun `a pool the node does not clean has no countdown`() {
        assertNull(blocks(THORChain, "ZEC.ZEC", currentHeight = 10))
    }

    @Test
    fun `a non-positive age limit has no countdown`() {
        assertNull(
            blocksUntilPendingLiquidityCommit(
                schedule = MayaChain,
                pool = "BTC.BTC",
                cleanedPools = pools,
                lastAddHeight = 0,
                ageLimit = 0,
                currentHeight = 10,
            )
        )
    }

    @Test
    fun `cleaned pools are the live non-synthetic ones holding pending liquidity`() {
        fun cleaned(asset: String, status: String? = "Available", pending: String = "1") =
            isCleanedPendingLiquidityPool(
                asset = asset,
                status = status,
                pendingInboundNative = pending,
                pendingInboundAsset = "0",
                derivedChainPrefix = "THOR",
            )

        assertTrue(cleaned("BTC.BTC"))
        assertTrue(cleaned("BTC.BTC", status = "Staged"))
        assertTrue(cleaned("BTC.BTC", status = null))
        assertFalse(cleaned("BTC.BTC", status = "Suspended"))
        assertFalse(cleaned("BTC.BTC", pending = "0"))
        assertFalse(cleaned("BTC/BTC"))
        assertFalse(cleaned("THOR.BTC"))
    }
}
