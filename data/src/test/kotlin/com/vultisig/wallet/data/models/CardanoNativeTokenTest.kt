package com.vultisig.wallet.data.models

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the metadata of the curated Cardano native tokens (issue #5807).
 *
 * Every field here is one a plausible-looking guess gets wrong: `priceProviderID` is not derivable
 * from the ticker (`usdm` and `iusd-2` are other chains' tokens), and the asset-name hex is not
 * derivable from the ticker either — USDM carries a CIP-67 label prefix, DJED's name decodes to
 * `DjedMicroUSD` (its sibling under the same policy is `ShenMicroUSD`) and WMTX's to
 * `WorldMobileTokenX`. A wrong id prices the token at zero and a wrong asset name makes the entry
 * unmatchable, neither of which fails loudly anywhere else.
 */
internal class CardanoNativeTokenTest {

    /**
     * Each token as the registry states it, with the asset name spelled out rather than hex.
     *
     * The hex halves of `contractAddress` are re-derived here from the readable asset name and its
     * CIP-67 label, so a mistyped name byte in `Coins.kt` fails instead of matching a hex blob
     * copied out of the same file. Policy ids stay literal — a 28-byte script hash has no readable
     * form to derive it from — but the policy-id shape test below catches a truncated or
     * upper-cased transcription.
     */
    private val expected =
        listOf(
            Coins.Cardano.USDM to
                Row(
                    ticker = "USDM",
                    decimals = 6,
                    priceProviderID = "usdm-2",
                    policyId = "c48cbb3d5e57ed56e276bc45f99ab39abe94e6cd7ac39fb402da47ad",
                    assetName = "USDM",
                    // CIP-67 label 333, which prefixes Mehen's fungible asset name.
                    cip67Label = "0014df10",
                ),
            Coins.Cardano.IUSD to
                Row(
                    ticker = "iUSD",
                    decimals = 6,
                    priceProviderID = "iusd",
                    policyId = "f66d78b4a3cb3d37afa0ec36461e51ecbde00f26c8f0a68f94b69880",
                    assetName = "iUSD",
                ),
            Coins.Cardano.DJED to
                Row(
                    ticker = "DJED",
                    decimals = 6,
                    priceProviderID = "djed",
                    policyId = "8db269c3ec630e06ae29f74bc39edd1f87c819f1056206e879a1cd61",
                    assetName = "DjedMicroUSD",
                ),
            Coins.Cardano.LQ to
                Row(
                    ticker = "LQ",
                    decimals = 6,
                    priceProviderID = "liqwid-finance",
                    policyId = "da8c30857834c6ae7203935b89278c532b3995245295456f993e1d24",
                    assetName = "LQ",
                ),
            Coins.Cardano.MIN to
                Row(
                    ticker = "MIN",
                    decimals = 6,
                    priceProviderID = "minswap",
                    policyId = "29d222ce763455e3d7a09a665ce554f00ac89d2e99a1a83d267170c6",
                    assetName = "MIN",
                ),
            Coins.Cardano.SNEK to
                Row(
                    ticker = "SNEK",
                    decimals = 0,
                    priceProviderID = "snek",
                    policyId = "279c909f348e533da5808898f87f9a14bb2c3dfbbacccd631d927a3f",
                    assetName = "SNEK",
                ),
            Coins.Cardano.SUNDAE to
                Row(
                    ticker = "SUNDAE",
                    decimals = 6,
                    priceProviderID = "sundaeswap",
                    policyId = "9a9693a9a37912a5097918f97918d15240c92ab729a0b7c4aa144d77",
                    assetName = "SUNDAE",
                ),
            Coins.Cardano.IAG to
                Row(
                    ticker = "IAG",
                    decimals = 6,
                    priceProviderID = "iagon",
                    policyId = "5d16cc1a177b5d9ba9cfa9793b07e60f1fb70fea1f8aef064415d114",
                    assetName = "IAG",
                ),
            Coins.Cardano.HOSKY to
                Row(
                    ticker = "HOSKY",
                    decimals = 0,
                    priceProviderID = "hosky",
                    policyId = "a0028f350aaabe0545fdcb56b039bfb08e4bb4d8c4d7c3c7d481c235",
                    assetName = "HOSKY",
                ),
            Coins.Cardano.WMTX to
                Row(
                    ticker = "WMTX",
                    decimals = 6,
                    priceProviderID = "world-mobile-token",
                    policyId = "e5a42a1a1d3d1da71b0449663c32798725888d2eb0843c4dabeca05a",
                    assetName = "WorldMobileTokenX",
                ),
        )

    /** Pins every field of every curated entry against [expected]. */
    @Test
    fun `each curated token carries the registry metadata`() {
        expected.forEach { (coin, row) ->
            assertEquals(row.ticker, coin.ticker)
            assertEquals(Chain.Cardano, coin.chain)
            assertEquals(row.decimals, coin.decimal, "${row.ticker} decimals")
            assertEquals(row.priceProviderID, coin.priceProviderID, "${row.ticker} price id")
            assertEquals(row.assetId, coin.contractAddress, "${row.ticker} asset id")
            assertFalse(coin.isNativeToken, "${row.ticker} is not the native coin")
        }
    }

    /** A policy id is a 28-byte script hash, and the asset id form keeps it lowercase. */
    @Test
    fun `every policy id is a 28-byte lowercase hex hash`() {
        expected.forEach { (_, row) ->
            assertTrue(
                row.policyId.matches(Regex("[0-9a-f]{56}")),
                "${row.ticker} policy id must be 56 lowercase hex characters",
            )
        }
    }

    /** Guards the order and completeness of `Coins.coins[Cardano]`, which drives the token list. */
    @Test
    fun `the catalogue publishes ADA plus the ten native tokens`() {
        assertEquals(
            listOf(Coins.Cardano.ADA) + expected.map { it.first },
            Coins.coins.getValue(Chain.Cardano),
        )
    }

    /**
     * The lookup a discovery path has to reach before it builds a coin from provider metadata: iOS
     * shipped it the other way round and USDM auto-discovered as `_USDM` with no logo or price.
     */
    @Test
    fun `findCuratedByContract resolves every asset id`() {
        expected.forEach { (coin, row) ->
            assertSame(
                coin,
                Coins.findCuratedByContract(Chain.Cardano, row.assetId),
                "${row.ticker} is not resolvable by its asset id",
            )
        }
    }

    /** A url here would bypass `getCoinLogo`, so the logo must stay a bundled drawable name. */
    @Test
    fun `every curated token has a bundled logo name rather than a url`() {
        expected.forEach { (coin, row) ->
            assertEquals(row.ticker.lowercase(), coin.logo, "${row.ticker} logo")
            assertFalse(coin.logo.contains("/"), "${row.ticker} logo must not be a url")
        }
    }

    /** Mixed-case hex from a registry must still produce the chain's lowercase asset-id form. */
    @Test
    fun `the asset id builder lowercases and dot-separates its parts`() {
        assertTrue(
            cardanoAssetId(policyId = "AB12", assetNameHex = "CD34") == "ab12.cd34",
            "asset ids must be stored in the chain's own lowercase dot-separated form",
        )
    }

    private data class Row(
        val ticker: String,
        val decimals: Int,
        val priceProviderID: String,
        val policyId: String,
        val assetName: String,
        val cip67Label: String = "",
    ) {
        /** The `<policy_id>.<asset_name_hex>` id the catalog must store for this token. */
        val assetId: String
            get() {
                val nameHex = assetName.encodeToByteArray().joinToString("") { "%02x".format(it) }
                return "$policyId.$cip67Label$nameHex"
            }
    }
}
