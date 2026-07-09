package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.utils.CosmosThorChainResponseSerializerImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class CosmosApiTest {

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `getTxStatus returns null on 404 without throwing`() = runBlocking {
        val api = cosmosApi(MockEngine { respond(content = "", status = HttpStatusCode.NotFound) })

        assertNull(api.getTxStatus("ABC123"))
    }

    @Test
    fun `getTxStatus returns parsed response on 200`() = runBlocking {
        val api =
            cosmosApi(
                MockEngine {
                    respond(
                        content = """{"tx_response":{"height":"42","txhash":"ABC123","code":0}}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )

        val result = api.getTxStatus("ABC123")

        assertEquals("42", result?.txResponse?.height)
        assertEquals("ABC123", result?.txResponse?.txHash)
        assertEquals(0, result?.txResponse?.code)
    }

    @Test
    fun `getDenomMetadata returns parsed metadata for a known denom`() = runTest {
        val api =
            cosmosApi(
                MockEngine { request ->
                    assertEquals(
                        "/cosmos/bank/v1beta1/denoms_metadata/uluna",
                        request.url.encodedPath,
                    )
                    respond(
                        content =
                            """{"metadata":{"base":"uluna","symbol":"LUNA","display":"luna",""" +
                                """"denom_units":[{"denom":"uluna","exponent":0},""" +
                                """{"denom":"luna","exponent":6}]}}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )

        val metadata = api.getDenomMetadata("uluna")

        assertEquals("uluna", metadata?.base)
        assertEquals("LUNA", metadata?.symbol)
        assertEquals(6, metadata?.denomUnits?.last()?.exponent)
    }

    @Test
    fun `getDenomMetadata percent-encodes denoms with reserved characters`() = runTest {
        var capturedPath: String? = null
        val api =
            cosmosApi(
                MockEngine { request ->
                    capturedPath = request.url.encodedPath
                    respond(
                        content = """{"metadata":null}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )

        api.getDenomMetadata("ibc/ABC123")

        assertEquals("/cosmos/bank/v1beta1/denoms_metadata/ibc%2FABC123", capturedPath)
    }

    @Test
    fun `getDenomMetadata returns null on 404 without throwing`() = runTest {
        val api = cosmosApi(MockEngine { respond(content = "", status = HttpStatusCode.NotFound) })

        assertNull(api.getDenomMetadata("ibc/UNKNOWN"))
    }

    @Test
    fun `getDenomMetadata returns null on 5xx without throwing`() = runTest {
        val api =
            cosmosApi(
                MockEngine {
                    respond(content = "boom", status = HttpStatusCode.InternalServerError)
                }
            )

        assertNull(api.getDenomMetadata("uluna"))
    }

    @Test
    fun `getTerraClassicBurnTaxRate parses burn_tax_rate from x-tax params`() = runTest {
        var capturedPath: String? = null
        val api =
            cosmosApi(
                MockEngine { request ->
                    capturedPath = request.url.encodedPath
                    respond(
                        content =
                            """{"params":{"burn_tax_rate":"0.005000000000000000",""" +
                                """"gas_prices":[]}}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )

        val rate = api.getTerraClassicBurnTaxRate()

        assertEquals("/terra/tax/v1beta1/params", capturedPath)
        assertEquals("0.005000000000000000", rate)
    }

    @Test
    fun `getTerraClassicBurnTaxRate returns null on failure so caller can fall back`() = runTest {
        val api =
            cosmosApi(
                MockEngine {
                    respond(content = "boom", status = HttpStatusCode.InternalServerError)
                }
            )

        assertNull(api.getTerraClassicBurnTaxRate())
    }

    @Test
    fun `getBalance coalesces repeat calls for one address into a single request`() = runTest {
        val requests = AtomicInteger()
        val api =
            cosmosApi(
                MockEngine {
                    requests.incrementAndGet()
                    respond(
                        content = """{"balances":[{"denom":"uatom","amount":"100"}]}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )

        repeat(10) { assertEquals("uatom", api.getBalance("cosmos1abc").single().denom) }

        assertEquals(1, requests.get())
    }

    @Test
    fun `getBalance shares one in-flight request across concurrent callers`() = runTest {
        val requests = AtomicInteger()
        val api =
            cosmosApi(
                MockEngine {
                    requests.incrementAndGet()
                    respond(
                        content = """{"balances":[{"denom":"uatom","amount":"100"}]}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )

        val results = (1..10).map { async { api.getBalance("cosmos1abc") } }.awaitAll()

        assertEquals(1, requests.get())
        assertEquals(10, results.count { it.single().denom == "uatom" })
    }

    @Test
    fun `getBalance keys the cache by address so different addresses each fetch`() = runTest {
        val requests = AtomicInteger()
        val api =
            cosmosApi(
                MockEngine {
                    requests.incrementAndGet()
                    respond(
                        content = """{"balances":[{"denom":"uatom","amount":"100"}]}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )

        api.getBalance("cosmos1aaa")
        api.getBalance("cosmos1bbb")

        assertEquals(2, requests.get())
    }

    @Test
    fun `getBalance refetches once the cache entry expires`() = runTest {
        val requests = AtomicInteger()
        val api =
            cosmosApi(
                engine =
                    MockEngine {
                        requests.incrementAndGet()
                        respond(
                            content = """{"balances":[{"denom":"uatom","amount":"100"}]}""",
                            status = HttpStatusCode.OK,
                            headers = jsonHeaders,
                        )
                    },
                // ttl 0 => every entry is already expired, so caching never masks a stale balance.
                balanceCache = CosmosBalanceCache(ttlMs = 0),
            )

        api.getBalance("cosmos1abc")
        api.getBalance("cosmos1abc")

        assertEquals(2, requests.get())
    }

    private fun cosmosApi(
        engine: MockEngine,
        balanceCache: CosmosBalanceCache = CosmosBalanceCache(),
    ): CosmosApi {
        val json = Json { ignoreUnknownKeys = true }
        return CosmosApiImp(
            HttpClient(engine) { install(ContentNegotiation) { json(json) } },
            "https://example.test",
            json,
            CosmosThorChainResponseSerializerImpl(json),
            balanceCache,
        )
    }
}
