package com.vultisig.wallet.data.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChainDisplayNameTest {

    @Test
    fun `tidied chains show their label while keeping their identifier`() {
        mapOf(
                Chain.BitcoinCash to ("Bitcoin-Cash" to "Bitcoin Cash"),
                Chain.ZkSync to ("Zksync" to "ZKsync"),
                Chain.CronosChain to ("CronosChain" to "Cronos Chain"),
                Chain.Dydx to ("Dydx" to "dYdX"),
                Chain.TerraClassic to ("TerraClassic" to "Terra Classic"),
                Chain.BscChain to ("BSC" to "BSC"),
                Chain.Ton to ("Ton" to "TON (GRAM)"),
            )
            .forEach { (chain, expected) ->
                val (raw, label) = expected
                assertEquals(raw, chain.raw)
                assertEquals(label, chain.displayName)
            }
    }

    @Test
    fun `displayNameOf resolves a stored id to its label`() {
        assertEquals("Bitcoin Cash", Chain.displayNameOf("Bitcoin-Cash"))
        assertEquals("Kujira", Chain.displayNameOf("Kujira"))
    }

    @Test
    fun `search matches the label and the identifier ignoring case`() {
        assertTrue(Chain.TerraClassic.matchesSearch("terra c"))
        assertTrue(Chain.TerraClassic.matchesSearch("terrac"))
        assertTrue(Chain.BitcoinCash.matchesSearch("bitcoin cash"))
        assertTrue(Chain.BitcoinCash.matchesSearch("bitcoin-cash"))
        assertTrue(Chain.Dydx.matchesSearch("DYDX"))
        assertTrue(Chain.Ton.matchesSearch("gram"))
    }

    @Test
    fun `search rejects unrelated queries`() {
        assertFalse(Chain.Terra.matchesSearch("classic"))
    }
}
