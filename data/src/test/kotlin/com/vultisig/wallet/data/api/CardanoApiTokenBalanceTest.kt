package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.http.HttpStatusCode
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Koios truncates an unpaginated `address_assets` response at 1000 rows, so an address holding more
 * distinct native assets than that would report zero for a curated token whose row falls past the
 * cut. These tests pin the paging walk in [CardanoApiImpl.getTokenBalance].
 */
class CardanoApiTokenBalanceTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val snek = Coins.Cardano.SNEK.copy(address = "addr1test")

    private val snekPolicyId = "279c909f348e533da5808898f87f9a14bb2c3dfbbacccd631d927a3f"
    private val snekAssetNameHex = "534e454b"

    private fun assetRow(policyId: String, assetName: String, quantity: String) =
        """{"policy_id":"$policyId","asset_name":"$assetName","quantity":"$quantity"}"""

    /** A page of [size] rows, the last of which is SNEK when [withSnek] holds. */
    private fun page(size: Int, withSnek: Boolean, snekQuantity: String = "0"): String {
        val rows =
            (0 until size).map { index ->
                if (withSnek && index == size - 1) {
                    assetRow(snekPolicyId, snekAssetNameHex, snekQuantity)
                } else {
                    assetRow("%056x".format(index), "%08x".format(index), "1")
                }
            }
        return rows.joinToString(prefix = "[", separator = ",", postfix = "]")
    }

    @Test
    fun `getTokenBalance sums matching rows past the first page`() = runTest {
        val capture = MockHttpClient.RequestCapture()
        val api =
            CardanoApiImpl(
                httpClient =
                    MockHttpClient.capturingRequestSequence(
                        capture,
                        HttpStatusCode.OK to page(size = 1000, withSnek = true, snekQuantity = "5"),
                        HttpStatusCode.OK to page(size = 3, withSnek = true, snekQuantity = "7"),
                        jsonFormat = json,
                    ),
                json = json,
            )

        assertEquals(BigInteger("12"), api.getTokenBalance(snek))
        assertEquals(listOf("offset=0&limit=1000", "offset=1000&limit=1000"), capture.queries)
    }

    @Test
    fun `getTokenBalance fails rather than report a partial balance at the page ceiling`() =
        runTest {
            val fullPage = page(size = 1000, withSnek = true, snekQuantity = "5")
            val calls = AtomicInteger(0)
            val api =
                CardanoApiImpl(
                    httpClient =
                        MockHttpClient.respondingWithGenerated(jsonFormat = json) {
                            calls.incrementAndGet()
                            fullPage
                        },
                    json = json,
                )

            val failure = runCatching { api.getTokenBalance(snek) }.exceptionOrNull()

            assertTrue(
                failure is IllegalStateException,
                "expected the walk to fail at the ceiling, got $failure",
            )
            assertEquals(50, calls.get())
        }

    @Test
    fun `getTokenBalance stops requesting once a page comes back short`() = runTest {
        val calls = AtomicInteger(0)
        val api =
            CardanoApiImpl(
                httpClient =
                    MockHttpClient.respondingWithGenerated(jsonFormat = json) {
                        calls.incrementAndGet()
                        page(size = 2, withSnek = true, snekQuantity = "9")
                    },
                json = json,
            )

        assertEquals(BigInteger("9"), api.getTokenBalance(snek))
        assertEquals(1, calls.get())
    }
}
