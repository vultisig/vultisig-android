package com.vultisig.wallet.data.repositories.swap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the LI.FI "Auto" tiers ported from the SDK's `lifiSlippage.ts` (vultisig-sdk#524): 0.3% for
 * a stable-to-stable pair, 1% otherwise, and never LI.FI's own 0.5% default, which is what let
 * the #5801 swaps revert with `Insufficient output`.
 */
class LiFiSlippageTest {

    @Test
    fun `auto on a stable pair resolves to the stable tier`() {
        assertEquals(30, LiFiSlippage.resolveBps(null, "USDC", "USDT"))
    }

    @Test
    fun `auto on a volatile pair resolves to the volatile tier`() {
        assertEquals(100, LiFiSlippage.resolveBps(null, "ETH", "WBTC"))
    }

    @Test
    fun `auto on a half-stable pair takes the volatile tier`() {
        assertEquals(100, LiFiSlippage.resolveBps(null, "USDC", "ETH"))
        assertEquals(100, LiFiSlippage.resolveBps(null, "ETH", "USDC"))
    }

    @Test
    fun `ticker matching is case-insensitive`() {
        assertEquals(30, LiFiSlippage.resolveBps(null, "usdc", "Dai"))
    }

    @Test
    fun `a stable ticker only matches whole, so a wrapper like stUSDT stays volatile`() {
        assertFalse(LiFiSlippage.isStablePair("stUSDT", "USDC"))
        assertEquals(100, LiFiSlippage.resolveBps(null, "stUSDT", "USDC"))
    }

    @Test
    fun `a user tolerance overrides both tiers untouched`() {
        assertEquals(50, LiFiSlippage.resolveBps(50, "USDC", "USDT"))
        assertEquals(300, LiFiSlippage.resolveBps(300, "ETH", "WBTC"))
        // A deliberately tight custom value must not be widened to a tier.
        assertEquals(1, LiFiSlippage.resolveBps(1, "ETH", "WBTC"))
    }

    @Test
    fun `a non-positive override falls back to the tier instead of sending zero`() {
        assertEquals(30, LiFiSlippage.resolveBps(0, "USDC", "USDT"))
        assertEquals(100, LiFiSlippage.resolveBps(0, "ETH", "WBTC"))
        assertEquals(100, LiFiSlippage.resolveBps(-25, "ETH", "WBTC"))
    }

    @Test
    fun `every auto tier is a real tolerance, never zero`() {
        assertTrue(LiFiSlippage.STABLE_PAIR_BPS > 0)
        assertTrue(LiFiSlippage.DEFAULT_BPS > LiFiSlippage.STABLE_PAIR_BPS)
    }
}
