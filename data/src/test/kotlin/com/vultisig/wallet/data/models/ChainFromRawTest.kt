package com.vultisig.wallet.data.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Chain ids reach the app as text — from the database, from preferences, from a deep link, from
 * another device's QR — so they can name a chain this build no longer has. The two lookups have to
 * stay distinguishable: [Chain.fromRawOrNull] for the readers that skip what they can't resolve,
 * [Chain.fromRaw] for the callers that treat an unknown id as a bug and still expect the
 * [NoSuchElementException] they catch today.
 */
class ChainFromRawTest {

    // A chain id left behind in old databases, preferences and deep links after the entry was
    // removed from Chain.
    private val retiredChainId = "Kujira"

    @Test
    fun `fromRawOrNull resolves a known id regardless of case`() {
        assertEquals(Chain.Bitcoin, Chain.fromRawOrNull("Bitcoin"))
        assertEquals(Chain.Bitcoin, Chain.fromRawOrNull("bitcoin"))
        assertEquals(Chain.BscChain, Chain.fromRawOrNull("bsc"))
    }

    @Test
    fun `fromRawOrNull returns null for a retired chain`() {
        assertNull(Chain.fromRawOrNull(retiredChainId))
        assertNull(Chain.fromRawOrNull(""))
    }

    @Test
    fun `fromRaw still throws NoSuchElementException for a retired chain`() {
        val error =
            assertThrows(NoSuchElementException::class.java) { Chain.fromRaw(retiredChainId) }
        assertEquals("Unknown chain id $retiredChainId", error.message)
    }

    @Test
    fun `every entry resolves from its own raw id`() {
        Chain.entries.forEach { assertEquals(it, Chain.fromRawOrNull(it.raw)) }
    }
}
