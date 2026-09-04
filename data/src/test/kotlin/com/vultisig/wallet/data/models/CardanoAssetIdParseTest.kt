package com.vultisig.wallet.data.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The halves of a `Coin.contractAddress` are pushed into a Cardano signing input as raw hex, so a
 * malformed id has to be caught before it reaches a body the ledger would reject. Mirrors iOS
 * `CardanoAssetIdTests`.
 */
class CardanoAssetIdParseTest {

    private val policyId = "279c909f348e533da5808898f87f9a14bb2c3dfbbacccd631d927a3f"

    @Test
    fun `round-trips an id the catalog builds`() {
        val id = cardanoAssetId(policyId = policyId, assetNameHex = "534E454B")

        assertEquals(CardanoAssetId(policyId, "534e454b"), parseCardanoAssetId(id))
    }

    @Test
    fun `accepts a policy's unnamed asset`() {
        assertEquals(CardanoAssetId(policyId, ""), parseCardanoAssetId("$policyId."))
    }

    @Test
    fun `accepts the longest asset name CIP-14 allows`() {
        val name = "ab".repeat(32)

        assertEquals(CardanoAssetId(policyId, name), parseCardanoAssetId("$policyId.$name"))
    }

    @Test
    fun `rejects an id with no separator`() {
        assertNull(parseCardanoAssetId(policyId))
    }

    @Test
    fun `rejects a policy id of the wrong length`() {
        assertNull(parseCardanoAssetId("${policyId.dropLast(2)}.534e454b"))
    }

    @Test
    fun `rejects a non-hex policy id`() {
        assertNull(parseCardanoAssetId("${policyId.dropLast(1)}z.534e454b"))
    }

    @Test
    fun `rejects an uppercase policy id`() {
        // The catalog and every discovery path store lowercase; an uppercase id here would mean
        // the caller skipped normalization and would put different bytes on the wire.
        assertNull(parseCardanoAssetId("${policyId.uppercase()}.534e454b"))
    }

    @Test
    fun `rejects an odd-length asset name`() {
        assertNull(parseCardanoAssetId("$policyId.534e454"))
    }

    @Test
    fun `rejects an asset name past the CIP-14 ceiling`() {
        assertNull(parseCardanoAssetId("$policyId.${"ab".repeat(33)}"))
    }

    @Test
    fun `rejects an empty id`() {
        assertNull(parseCardanoAssetId(""))
    }
}
