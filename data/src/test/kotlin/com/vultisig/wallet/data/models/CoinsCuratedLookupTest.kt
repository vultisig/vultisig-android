package com.vultisig.wallet.data.models

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * [Coins.findCurated] resolves a pool's chain/ticker pair to a curated coin, and its result decides
 * which id a price is cached under. That only works while the catalogue keeps chain+ticker and
 * chain+contract unique — an invariant nothing else enforces, so it is pinned here.
 */
internal class CoinsCuratedLookupTest {

    @Test
    fun `no two curated coins on a chain share a ticker`() {
        val collisions =
            Coins.allResolvable
                .groupBy { it.chain to it.ticker.lowercase() }
                .filterValues { it.size > 1 }
                .map { (key, coins) -> "${key.first}/${key.second}: ${coins.map { it.id }}" }

        assertTrue(collisions.isEmpty(), "ambiguous chain+ticker in the catalogue: $collisions")
    }

    /**
     * Case-insensitively, because that is how [Coins.findCurated] compares: a pool names an EVM
     * contract in upper case while the catalogue carries the checksummed mixed-case form. Two
     * entries differing only in case would make the match order-dependent.
     */
    @Test
    fun `no two curated coins on a chain share a contract address`() {
        val collisions =
            Coins.allResolvable
                .filter { it.contractAddress.isNotEmpty() }
                .groupBy { it.chain to it.contractAddress.lowercase() }
                .filterValues { it.size > 1 }
                .map { (key, coins) -> "${key.first}/${key.second}: ${coins.map { it.id }}" }

        assertTrue(collisions.isEmpty(), "ambiguous chain+contract in the catalogue: $collisions")
    }

    @Test
    fun `an EVM contract resolves whatever case the pool names it in`() {
        val usdc = Coins.Ethereum.USDC

        assertEquals(
            usdc.id,
            Coins.findCurated(Chain.Ethereum, "USDC", usdc.contractAddress.uppercase())?.id,
        )
        assertEquals(
            usdc.id,
            Coins.findCuratedByContract(Chain.Ethereum, usdc.contractAddress.lowercase())?.id,
        )
    }

    @Test
    fun `a native pool asset resolves on chain and ticker alone`() {
        assertEquals(
            Coins.Bitcoin.BTC.id,
            Coins.findCurated(Chain.Bitcoin, "btc", contractAddress = "")?.id,
        )
    }

    /** Every native coin carries an empty contract, so an empty query would match arbitrarily. */
    @Test
    fun `an empty contract never resolves by contract alone`() {
        assertNull(Coins.findCuratedByContract(Chain.Bitcoin, ""))
    }

    @Test
    fun `a contract the catalogue does not carry resolves to nothing`() {
        assertNull(
            Coins.findCuratedByContract(
                Chain.Ethereum,
                "0x00000000000000000000000000000000deadbeef",
            )
        )
    }
}
