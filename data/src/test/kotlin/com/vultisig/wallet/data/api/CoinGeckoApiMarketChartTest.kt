package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.utils.BigDecimalSerializerImpl
import com.vultisig.wallet.data.utils.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.math.BigDecimal
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.junit.jupiter.api.Test

/**
 * Tests for the market-chart/markets-stats endpoints added for issue #5428, following the
 * URL-capturing pattern established in [CoinGeckoApiContractPriceChainTest].
 */
internal class CoinGeckoApiMarketChartTest {

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    // Mirrors production's Json (DataModule.provideJson): CoinMarketStatsJson has @Contextual
    // BigDecimal fields, which need this registered to deserialize at all.
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        serializersModule = SerializersModule {
            contextual(BigDecimal::class, BigDecimalSerializerImpl())
        }
    }

    private fun apiCapturingUrl(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): Pair<CoinGeckoApi, () -> String> {
        var requestedUrl = ""
        val engine = MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(content = body, status = status, headers = jsonHeaders)
        }
        val api =
            CoinGeckoApiImpl(HttpClient(engine) { install(ContentNegotiation) { json(json) } })
        return api to { requestedUrl }
    }

    @Test
    fun `getMarketChart lowercases the id and forwards currency and days`() = runTest {
        val (api, url) = apiCapturingUrl("""{"prices":[[1000,100.0],[2000,101.0]]}""")

        val result = api.getMarketChart(id = "Bitcoin", currency = "usd", days = "1")

        assertContains(url(), "/coins/bitcoin/market_chart")
        assertContains(url(), "vs_currency=usd")
        assertContains(url(), "days=1")
        assertEquals(listOf(listOf(1000.0, 100.0), listOf(2000.0, 101.0)), result.prices)
    }

    @Test
    fun `getContractMarketChart routes through the chain's CoinGecko platform id`() = runTest {
        val (api, url) = apiCapturingUrl("""{"prices":[]}""")

        api.getContractMarketChart(
            chain = Chain.Base,
            contractAddress = "0xabc123",
            currency = "usd",
            days = "max",
        )

        assertContains(url(), "/coins/base/contract/0xabc123/market_chart")
        assertContains(url(), "vs_currency=usd")
        assertContains(url(), "days=max")
    }

    @Test
    fun `getMarketStats lowercases the id and queries the markets endpoint`() = runTest {
        val (api, url) = apiCapturingUrl("""[{"market_cap":1000000}]""")

        api.getMarketStats(id = "Bitcoin", currency = "eur")

        assertContains(url(), "/coins/markets")
        assertContains(url(), "ids=bitcoin")
        assertContains(url(), "vs_currency=eur")
    }

    @Test
    fun `getMarketChart throws NetworkException on a non-2xx response`() = runTest {
        val (api, _) = apiCapturingUrl("""{"error":"coin not found"}""", HttpStatusCode.NotFound)

        assertFailsWith<NetworkException> { api.getMarketChart("unknown-coin", "usd", "1") }
    }

    @Test
    fun `getMarketStats decodes an empty array without throwing`() = runTest {
        val (api, _) = apiCapturingUrl("""[]""")

        val result = api.getMarketStats("bitcoin", "usd")

        assertEquals(emptyList(), result)
    }

    @Test
    fun `getContractMarketChart throws for a chain with no CoinGecko asset id, without reaching the network`() =
        runTest {
            var requestAttempted = false
            val engine = MockEngine {
                requestAttempted = true
                respond(
                    content = """{"prices":[]}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            }
            val api =
                CoinGeckoApiImpl(HttpClient(engine) { install(ContentNegotiation) { json(json) } })

            assertFailsWith<IllegalStateException> {
                api.getContractMarketChart(
                    chain = Chain.Sei,
                    contractAddress = "0xabc123",
                    currency = "usd",
                    days = "1",
                )
            }
            assertFalse(requestAttempted, "an unmapped chain must not reach the network")
        }
}
