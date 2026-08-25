package com.vultisig.wallet.data.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the pool-asset → [Coin] resolution behind the referral payout-asset picker (issue #5684).
 *
 * The asset id itself must survive unaltered: it is what the THORName memo carries, and thornode
 * matches it verbatim against its own pool table.
 */
class ThorChainPoolCoinTest {

    @Test
    fun `resolves a native pool asset to its registry coin`() {
        val resolved = ThorChainPoolCoin.from("BTC.BTC")

        assertEquals("BTC.BTC", resolved?.asset)
        assertEquals(Chain.Bitcoin, resolved?.coin?.chain)
        assertEquals("BTC", resolved?.coin?.ticker)
        assertTrue(resolved?.coin?.isNativeToken == true)
    }

    @Test
    fun `resolves a contract-qualified pool asset to the matching registry coin`() {
        val resolved =
            ThorChainPoolCoin.from("ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48", 6)

        assertEquals("ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48", resolved?.asset)
        assertEquals(Chain.Ethereum, resolved?.coin?.chain)
        assertEquals("USDC", resolved?.coin?.ticker)
        assertEquals(
            Coins.Ethereum.USDC.contractAddress.lowercase(),
            resolved?.coin?.contractAddress?.lowercase(),
        )
    }

    @Test
    fun `resolves a THORChain asset by its lowercased denom`() {
        val resolved = ThorChainPoolCoin.from("THOR.TCY", 8)

        assertEquals(Chain.ThorChain, resolved?.coin?.chain)
        assertEquals("TCY", resolved?.coin?.ticker)
        assertEquals("tcy", resolved?.coin?.contractAddress)
    }

    @Test
    fun `falls back to the ticker when the denom is not the registry's`() {
        // THOR.RUJI is held as `x/ruji`, which the pool id does not spell out.
        val resolved = ThorChainPoolCoin.from("THOR.RUJI", 8)

        assertEquals("RUJI", resolved?.coin?.ticker)
        assertEquals("x/ruji", resolved?.coin?.contractAddress)
    }

    @Test
    fun `synthesizes a coin for an asset missing from the registry`() {
        val resolved = ThorChainPoolCoin.from("AVAX.SOL-0XFE6B19286885A4F7F55ADAD09C3CD1F906D2478F")

        assertEquals(Chain.Avalanche, resolved?.coin?.chain)
        assertEquals("SOL", resolved?.coin?.ticker)
        assertEquals("0XFE6B19286885A4F7F55ADAD09C3CD1F906D2478F", resolved?.coin?.contractAddress)
        assertEquals(false, resolved?.coin?.isNativeToken)
        // No precision reported by the pool, so THORChain's own 8 decimals stand in.
        assertEquals(8, resolved?.coin?.decimal)
    }

    @Test
    fun `takes the pool's decimals for a synthesized coin`() {
        val resolved =
            ThorChainPoolCoin.from("BASE.VVV-0XACFE6019ED1A7DC6F7B508C02D1B04EC88CC21BF", 6)

        assertEquals(6, resolved?.coin?.decimal)
    }

    @Test
    fun `returns null for the sentinel a nameless preferred asset uses`() {
        assertNull(ThorChainPoolCoin.from("."))
        assertNull(ThorChainPoolCoin.from(""))
    }

    @Test
    fun `returns null for a chain the wallet does not hold`() {
        assertNull(ThorChainPoolCoin.from("ALEO.ALEO"))
    }
}
