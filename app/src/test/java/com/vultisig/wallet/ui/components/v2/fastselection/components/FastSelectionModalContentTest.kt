package com.vultisig.wallet.ui.components.v2.fastselection.components

import org.junit.Assert.assertEquals
import org.junit.Test

internal class FastSelectionModalContentTest {

    private data class Item(val id: String, val ticker: String)

    @Test
    fun `keys are all distinct for a list of unique items`() {
        val items =
            listOf(Item("chain-eth", "ETH"), Item("chain-btc", "BTC"), Item("chain-sol", "SOL"))
        val keys = buildPaddedItemKeys(items, visibleItemCount = 5, key = { it.id })
        assertEquals("expected all keys to be distinct", keys.size, keys.distinct().size)
    }

    @Test
    fun `items sharing the same ticker but different ids produce distinct keys`() {
        // Regression guard: using ticker as key lets two chains collide; id must be used instead.
        val items = listOf(Item("erc20-eth-mainnet", "ETH"), Item("erc20-eth-sepolia", "ETH"))
        val keys = buildPaddedItemKeys(items, visibleItemCount = 5, key = { it.id })
        assertEquals("expected all keys to be distinct", keys.size, keys.distinct().size)
    }

    @Test
    fun `padding keys do not collide with item keys`() {
        // Item key is Pair("item", ...) and padding key is Pair("padding", index),
        // so they are disjoint regardless of what key(item) returns.
        val items = listOf(Item("0", "BTC"), Item("1", "ETH"))
        val keys = buildPaddedItemKeys(items, visibleItemCount = 3, key = { it.id })
        assertEquals("expected all keys to be distinct", keys.size, keys.distinct().size)
    }

    @Test
    fun `single item list produces unique keys`() {
        val items = listOf(Item("only-item", "ONLY"))
        val keys = buildPaddedItemKeys(items, visibleItemCount = 7, key = { it.id })
        assertEquals("expected all keys to be distinct", keys.size, keys.distinct().size)
    }

    @Test
    fun `empty items list produces only unique padding keys`() {
        val keys = buildPaddedItemKeys(emptyList<Item>(), visibleItemCount = 5, key = { it.id })
        assertEquals("expected all keys to be distinct", keys.size, keys.distinct().size)
    }

    @Test
    fun `paddedItemKey returns item-namespaced pair for non-null item`() {
        val item = Item("chain-1", "ETH")
        val result = paddedItemKey(index = 0, item = item, key = { it.id })
        assertEquals(Pair("item", "chain-1"), result)
    }

    @Test
    fun `paddedItemKey returns padding-namespaced pair for null item`() {
        val result = paddedItemKey<Item>(index = 2, item = null, key = { it.id })
        assertEquals(Pair("padding", 2), result)
    }
}
