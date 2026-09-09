package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.testutils.MockHttpClient
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The discovery read on top of `address_assets`, and the single walk it shares with every per-token
 * balance read of the same address.
 *
 * Sharing is not an optimisation: `AccountsRepository` fans a non-EVM chain's balances out with one
 * concurrent request per coin, so without it a wallet holding N discovered tokens would fire N
 * identical paginated walks at Koios at once.
 */
class CardanoApiAddressAssetsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun assetRow(policyId: String, assetName: String, quantity: String, decimals: Int) =
        """{"policy_id":"$policyId","asset_name":"$assetName","quantity":"$quantity","decimals":$decimals}"""

    @Test
    fun `getAddressAssets returns every row across pages`() = runTest {
        val firstPage =
            (0 until 1000).joinToString(prefix = "[", separator = ",", postfix = "]") {
                assetRow("%056x".format(it), "%08x".format(it), "1", 0)
            }
        val secondPage =
            listOf(assetRow(SNEK_POLICY_ID, SNEK_ASSET_NAME_HEX, "5", 0))
                .joinToString(prefix = "[", separator = ",", postfix = "]")
        val capture = MockHttpClient.RequestCapture()
        val api =
            CardanoApiImpl(
                httpClient =
                    MockHttpClient.capturingRequestSequence(
                        capture,
                        io.ktor.http.HttpStatusCode.OK to firstPage,
                        io.ktor.http.HttpStatusCode.OK to secondPage,
                        jsonFormat = json,
                    ),
                json = json,
            )

        val assets = api.getAddressAssets(ADDRESS)

        assertEquals(1001, assets.size)
        assertEquals(listOf("offset=0&limit=1000", "offset=1000&limit=1000"), capture.queries)
    }

    // Koios reports the registry's decimals on this endpoint; discovery reads them straight off it.
    @Test
    fun `getAddressAssets carries the registry decimals through`() = runTest {
        val api = apiRespondingWith(assetRow(MELD_POLICY_ID, MELD_ASSET_NAME_HEX, "1500", 6))

        assertEquals(6, api.getAddressAssets(ADDRESS).single().decimals)
    }

    // The concurrent fan-out is the case that matters: every caller must land on one walk.
    @Test
    fun `concurrent reads of one address share a single walk`() = runTest {
        val calls = AtomicInteger(0)
        val page =
            listOf(
                    assetRow(SNEK_POLICY_ID, SNEK_ASSET_NAME_HEX, "5", 0),
                    assetRow(MELD_POLICY_ID, MELD_ASSET_NAME_HEX, "1500", 6),
                )
                .joinToString(prefix = "[", separator = ",", postfix = "]")
        val api =
            CardanoApiImpl(
                httpClient =
                    MockHttpClient.respondingWithGenerated(jsonFormat = json) {
                        calls.incrementAndGet()
                        page
                    },
                json = json,
            )
        val snek = Coins.Cardano.SNEK.copy(address = ADDRESS)

        val results = coroutineScope {
            listOf(
                    async { api.getTokenBalance(snek) },
                    async { api.getTokenBalance(snek) },
                    async { api.getAddressAssets(ADDRESS).size.toBigInteger() },
                )
                .awaitAll()
        }

        assertEquals(listOf(BigInteger("5"), BigInteger("5"), BigInteger("2")), results)
        assertEquals(1, calls.get())
    }

    // Two vaults, or a vault and a watch-only address, must not read each other's holdings.
    @Test
    fun `a different address is walked separately`() = runTest {
        val calls = AtomicInteger(0)
        val api =
            CardanoApiImpl(
                httpClient =
                    MockHttpClient.respondingWithGenerated(jsonFormat = json) {
                        calls.incrementAndGet()
                        "[]"
                    },
                json = json,
            )

        api.getAddressAssets(ADDRESS)
        api.getAddressAssets(OTHER_ADDRESS)

        assertEquals(2, calls.get())
    }

    // The ceiling guard has to survive the extraction: a walk that never proves it read the whole
    // holding must fail rather than hand discovery a truncated asset list.
    @Test
    fun `getAddressAssets fails rather than return a truncated list at the page ceiling`() =
        runTest {
            val fullPage =
                (0 until 1000).joinToString(prefix = "[", separator = ",", postfix = "]") {
                    assetRow("%056x".format(it), "%08x".format(it), "1", 0)
                }
            val api =
                CardanoApiImpl(
                    httpClient =
                        MockHttpClient.respondingWithGenerated(jsonFormat = json) { fullPage },
                    json = json,
                )

            val failure = runCatching { api.getAddressAssets(ADDRESS) }.exceptionOrNull()

            assertTrue(
                failure is IllegalStateException,
                "expected the walk to fail at the ceiling, got $failure",
            )
        }

    private fun apiRespondingWith(vararg rows: String): CardanoApiImpl =
        CardanoApiImpl(
            httpClient =
                MockHttpClient.respondingWithGenerated(jsonFormat = json) {
                    rows.joinToString(prefix = "[", separator = ",", postfix = "]")
                },
            json = json,
        )

    private companion object {
        const val ADDRESS = "addr1test"
        const val OTHER_ADDRESS = "addr1other"
        const val SNEK_POLICY_ID = "279c909f348e533da5808898f87f9a14bb2c3dfbbacccd631d927a3f"
        const val SNEK_ASSET_NAME_HEX = "534e454b"
        const val MELD_POLICY_ID = "6ac8ef33b510ec004fe11585f7c5a9f0c07f0c23428ab4f29c1d7d10"
        const val MELD_ASSET_NAME_HEX = "4d454c44"
    }
}
