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
 * derivable from the ticker either — USDM and DJED carry CIP-67 label prefixes, and WMTX's name
 * decodes to `WorldMobileTokenX`. A wrong id prices the token at zero and a wrong asset name makes
 * the entry unmatchable, neither of which fails loudly anywhere else.
 */
internal class CardanoNativeTokenTest {

    /** `(ticker, decimals, priceProviderID, contractAddress)` as the token registry states them. */
    private val expected =
        listOf(
            Coins.Cardano.USDM to
                Row(
                    "USDM",
                    6,
                    "usdm-2",
                    "c48cbb3d5e57ed56e276bc45f99ab39abe94e6cd7ac39fb402da47ad.0014df105553444d",
                ),
            Coins.Cardano.IUSD to
                Row(
                    "iUSD",
                    6,
                    "iusd",
                    "f66d78b4a3cb3d37afa0ec36461e51ecbde00f26c8f0a68f94b69880.69555344",
                ),
            Coins.Cardano.DJED to
                Row(
                    "DJED",
                    6,
                    "djed",
                    "8db269c3ec630e06ae29f74bc39edd1f87c819f1056206e879a1cd61." +
                        "446a65644d6963726f555344",
                ),
            Coins.Cardano.LQ to
                Row(
                    "LQ",
                    6,
                    "liqwid-finance",
                    "da8c30857834c6ae7203935b89278c532b3995245295456f993e1d24.4c51",
                ),
            Coins.Cardano.MIN to
                Row(
                    "MIN",
                    6,
                    "minswap",
                    "29d222ce763455e3d7a09a665ce554f00ac89d2e99a1a83d267170c6.4d494e",
                ),
            Coins.Cardano.SNEK to
                Row(
                    "SNEK",
                    0,
                    "snek",
                    "279c909f348e533da5808898f87f9a14bb2c3dfbbacccd631d927a3f.534e454b",
                ),
            Coins.Cardano.SUNDAE to
                Row(
                    "SUNDAE",
                    6,
                    "sundaeswap",
                    "9a9693a9a37912a5097918f97918d15240c92ab729a0b7c4aa144d77.53554e444145",
                ),
            Coins.Cardano.IAG to
                Row(
                    "IAG",
                    6,
                    "iagon",
                    "5d16cc1a177b5d9ba9cfa9793b07e60f1fb70fea1f8aef064415d114.494147",
                ),
            Coins.Cardano.HOSKY to
                Row(
                    "HOSKY",
                    0,
                    "hosky",
                    "a0028f350aaabe0545fdcb56b039bfb08e4bb4d8c4d7c3c7d481c235.484f534b59",
                ),
            Coins.Cardano.WMTX to
                Row(
                    "WMTX",
                    6,
                    "world-mobile-token",
                    "e5a42a1a1d3d1da71b0449663c32798725888d2eb0843c4dabeca05a." +
                        "576f726c644d6f62696c65546f6b656e58",
                ),
        )

    @Test
    fun `each curated token carries the registry metadata`() {
        expected.forEach { (coin, row) ->
            assertEquals(row.ticker, coin.ticker)
            assertEquals(Chain.Cardano, coin.chain)
            assertEquals(row.decimals, coin.decimal, "${row.ticker} decimals")
            assertEquals(row.priceProviderID, coin.priceProviderID, "${row.ticker} price id")
            assertEquals(row.contractAddress, coin.contractAddress, "${row.ticker} asset id")
            assertFalse(coin.isNativeToken, "${row.ticker} is not the native coin")
        }
    }

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
                Coins.findCuratedByContract(Chain.Cardano, row.contractAddress),
                "${row.ticker} is not resolvable by its asset id",
            )
        }
    }

    @Test
    fun `every curated token has a bundled logo name rather than a url`() {
        expected.forEach { (coin, row) ->
            assertEquals(row.ticker.lowercase(), coin.logo, "${row.ticker} logo")
            assertFalse(coin.logo.contains("/"), "${row.ticker} logo must not be a url")
        }
    }

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
        val contractAddress: String,
    )
}
