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

    // --- Crash-prevention tests ---
    // LazyColumn throws IllegalArgumentException when two entries share the same key.
    // The old implementation used position-index keys; when items were inserted or removed
    // during an AnimatedVisibility transition, indices shifted and Compose saw the same key
    // for two different items, causing the crash on API 35.

    @Test
    fun `item key is stable when a new item is prepended to the list`() {
        // With index-based keys, prepending shifts every existing item's key by one,
        // so key 0 would refer to different items across frames — the crash trigger.
        val itemB = Item("b", "B")
        val itemC = Item("c", "C")

        val keyB_before = paddedItemKey(index = 0, item = itemB, key = { it.id })
        val keyC_before = paddedItemKey(index = 1, item = itemC, key = { it.id })

        // After prepending A, B moves to index 1 and C to index 2.
        val keyB_after = paddedItemKey(index = 1, item = itemB, key = { it.id })
        val keyC_after = paddedItemKey(index = 2, item = itemC, key = { it.id })

        assertEquals(
            "B key must not change when an item is inserted before it",
            keyB_before,
            keyB_after,
        )
        assertEquals(
            "C key must not change when an item is inserted before it",
            keyC_before,
            keyC_after,
        )
    }

    @Test
    fun `keys remain unique after an item is removed from the list`() {
        // Removing an item must not cause any two surviving entries to share a key.
        val items = listOf(Item("a", "A"), Item("b", "B"), Item("c", "C"), Item("d", "D"))
        val keysAfterRemoval =
            buildPaddedItemKeys(
                items.filter { it.id != "b" },
                visibleItemCount = 5,
                key = { it.id },
            )
        assertEquals(
            "expected all keys to be distinct after removal",
            keysAfterRemoval.size,
            keysAfterRemoval.distinct().size,
        )
    }

    @Test
    fun `keys remain unique after an item is inserted into the list`() {
        val base = listOf(Item("b", "B"), Item("d", "D"))
        val expanded = listOf(Item("a", "A"), Item("b", "B"), Item("c", "C"), Item("d", "D"))
        val keys = buildPaddedItemKeys(expanded, visibleItemCount = 5, key = { it.id })
        assertEquals(
            "expected all keys to be distinct after insertion",
            keys.size,
            keys.distinct().size,
        )

        // Verify that the surviving items from the base list still have the same key.
        val baseKeys = buildPaddedItemKeys(base, visibleItemCount = 5, key = { it.id })
        assertEquals(
            "B key must survive list expansion unchanged",
            baseKeys.find { it == Pair("item", "b") },
            keys.find { it == Pair("item", "b") },
        )
    }
}
