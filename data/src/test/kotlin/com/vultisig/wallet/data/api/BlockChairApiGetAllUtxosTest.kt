package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.NetworkException
import com.vultisig.wallet.data.utils.UTXOStatusResponseSerializerImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * [BlockChairApi.getAllUtxos] paginates past Blockchair's ~100-entry default page until the full
 * `unspent_output_count` is retrieved, and fails loudly rather than silently returning a truncated
 * view. Regression coverage for #5433 (a large BTC send built from a stale, partial UTXO snapshot
 * got rejected by the network because the app only ever saw the newest 100 of 696 UTXOs).
 */
class BlockChairApiGetAllUtxosTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun pageBody(address: String, unspentOutputCount: Int, utxoValues: List<Long>): String {
        // Hash and output index both derive from the value (stable identity independent of a
        // page-local position), so a repeated value across two pages is a genuine duplicate
        // outpoint rather than an artifact of list position.
        val utxoJson =
            utxoValues.joinToString(",") { value ->
                """{"transaction_hash":"tx$value","index":0,"value":$value,"block_id":800000}"""
            }
        return """
            {
              "data": {
                "$address": {
                  "address": { "balance": 0, "unspent_output_count": $unspentOutputCount },
                  "utxo": [$utxoJson]
                }
              },
              "context": { "state": 900000 }
            }
            """
            .trimIndent()
    }

    /** Records each request's `offset` query param and serves [bodies] one per call, in order. */
    private fun recordingApi(
        vararg bodies: Pair<HttpStatusCode, String>
    ): Pair<BlockChairApiImp, MutableList<String?>> {
        val requestedOffsets = mutableListOf<String?>()
        var call = 0
        val engine = MockEngine { request ->
            requestedOffsets += request.url.parameters["offset"]
            val (status, body) = bodies[minOf(call, bodies.size - 1)]
            call++
            respond(content = body, status = status, headers = MockHttpClient.JSON_HEADERS)
        }
        val client =
            HttpClient(engine) { install(ContentNegotiation) { json(json, ContentType.Any) } }
        val api =
            BlockChairApiImp(
                json = json,
                httpClient = client,
                utxoStatusResponseSerializer = UTXOStatusResponseSerializerImpl(json),
            )
        return api to requestedOffsets
    }

    @Test
    fun `single page under page size fetches all utxos in one request`() = runTest {
        val (api, offsets) =
            recordingApi(
                HttpStatusCode.OK to
                    pageBody("addr", unspentOutputCount = 3, utxoValues = listOf(1L, 2L, 3L))
            )

        val result = api.getAllUtxos(Chain.Bitcoin, "addr")

        assertEquals(3, result.utxos.size)
        assertEquals(1, offsets.size)
        assertEquals("0,0", offsets.single())
    }

    @Test
    fun `paginates past the page size until unspent_output_count is reached`() = runTest {
        val page0 =
            pageBody("addr", unspentOutputCount = 1200, utxoValues = List(1000) { it.toLong() })
        val page1 =
            pageBody(
                "addr",
                unspentOutputCount = 1200,
                utxoValues = List(200) { (1000 + it).toLong() },
            )
        val (api, offsets) = recordingApi(HttpStatusCode.OK to page0, HttpStatusCode.OK to page1)

        val result = api.getAllUtxos(Chain.Bitcoin, "addr")

        assertEquals(1200, result.utxos.size)
        assertEquals(listOf("0,0", "0,1000"), offsets)
        // every UTXO from both pages made it into the merged result, not just the first page's
        assertTrue(result.utxos.any { it.transactionHash == "tx0" })
        assertTrue(result.utxos.any { it.transactionHash == "tx1199" })
    }

    @Test
    fun `does not stop early just because a full page matches a stale unspent_output_count`() =
        runTest {
            // unspent_output_count reads 1000 on page 0 (stale by 200) but the true total is
            // 1200 — a full first page must never be trusted as complete on count alone, since a
            // stale count could coincidentally match it and hide the rest.
            val page0 =
                pageBody("addr", unspentOutputCount = 1000, utxoValues = List(1000) { it.toLong() })
            val page1 =
                pageBody(
                    "addr",
                    unspentOutputCount = 1000,
                    utxoValues = List(200) { (1000 + it).toLong() },
                )
            val (api, offsets) =
                recordingApi(HttpStatusCode.OK to page0, HttpStatusCode.OK to page1)

            val result = api.getAllUtxos(Chain.Bitcoin, "addr")

            assertEquals(1200, result.utxos.size)
            assertEquals(listOf("0,0", "0,1000"), offsets)
        }

    @Test
    fun `throws when the reported unspent_output_count changes between pages`() = runTest {
        // The count moving mid-walk means the list shifted underneath the offsets already used
        // — a removed entry slides everything after it into a position already fetched, opening
        // a gap that merging pages can never detect on its own.
        val page0 =
            pageBody("addr", unspentOutputCount = 1200, utxoValues = List(1000) { it.toLong() })
        val page1 =
            pageBody(
                "addr",
                unspentOutputCount = 1199,
                utxoValues = List(199) { (1000 + it).toLong() },
            )
        val (api, _) = recordingApi(HttpStatusCode.OK to page0, HttpStatusCode.OK to page1)

        val exception =
            assertThrows<IllegalStateException> { api.getAllUtxos(Chain.Bitcoin, "addr") }
        assertTrue(exception.message!!.contains("1200"))
        assertTrue(exception.message!!.contains("1199"))
    }

    @Test
    fun `deduplicates a utxo returned on more than one page`() = runTest {
        // A newest-first list that shifts between two requests can hand the same outpoint back
        // on both pages; without de-duplication it would be double-counted and could later be
        // selected as two separate inputs for the same outpoint, which the network would reject.
        val page0 =
            pageBody("addr", unspentOutputCount = 1000, utxoValues = List(1000) { it.toLong() })
        val page1 =
            pageBody(
                "addr",
                unspentOutputCount = 1000,
                utxoValues = List(1) { 999L }, // tx999 repeated from page0
            )
        val (api, _) = recordingApi(HttpStatusCode.OK to page0, HttpStatusCode.OK to page1)

        val result = api.getAllUtxos(Chain.Bitcoin, "addr")

        assertEquals(1000, result.utxos.size)
        assertEquals(1, result.utxos.count { it.transactionHash == "tx999" })
    }

    @Test
    fun `throws instead of paging forever when every page comes back full`() = runTest {
        val fullPage =
            pageBody("addr", unspentOutputCount = 100_000, utxoValues = List(1000) { it.toLong() })
        val (api, offsets) = recordingApi(HttpStatusCode.OK to fullPage)

        assertThrows<IllegalStateException> { api.getAllUtxos(Chain.Bitcoin, "addr") }
        assertEquals(20, offsets.size)
    }

    @Test
    fun `throws instead of returning a truncated view when a page comes back short`() = runTest {
        // Reports 500 UTXOs but only ever returns 200 across two pages before going empty —
        // exactly the "stale/inconsistent snapshot" failure mode from #5433.
        val page0 =
            pageBody("addr", unspentOutputCount = 500, utxoValues = List(200) { it.toLong() })
        val page1 = pageBody("addr", unspentOutputCount = 500, utxoValues = emptyList())
        val (api, _) = recordingApi(HttpStatusCode.OK to page0, HttpStatusCode.OK to page1)

        val exception =
            assertThrows<IllegalStateException> { api.getAllUtxos(Chain.Bitcoin, "addr") }
        assertTrue(exception.message!!.contains("200"))
        assertTrue(exception.message!!.contains("500"))
    }

    @Test
    fun `empty wallet returns an empty utxo list without throwing`() = runTest {
        val (api, offsets) =
            recordingApi(
                HttpStatusCode.OK to
                    pageBody("addr", unspentOutputCount = 0, utxoValues = emptyList())
            )

        val result = api.getAllUtxos(Chain.Bitcoin, "addr")

        assertEquals(emptyList<Any>(), result.utxos)
        assertEquals(1, offsets.size)
    }

    @Test
    fun `propagates a network error instead of silently returning null`() = runTest {
        val (api, _) = recordingApi(HttpStatusCode.InternalServerError to """{"error":"boom"}""")

        assertThrows<NetworkException> { api.getAllUtxos(Chain.Bitcoin, "addr") }
    }
}
