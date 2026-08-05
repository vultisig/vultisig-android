package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.assertContains
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Regression test for #5406: `coinGeckoAssetId` threw for Mantle before the request was ever sent,
 * so `getContractsPrice` silently degraded to an empty map. Pins that Mantle now resolves to the
 * "mantle" CoinGecko platform id (matching iOS's `coinGeckoPlatform`), and that a chain the map
 * still doesn't cover keeps degrading gracefully instead of crashing.
 */
class CoinGeckoApiContractPriceChainTest {

    @Test
    fun `getContractsPrice resolves Mantle to the mantle CoinGecko platform id`() = runTest {
        lateinit var requestedUrl: String
        val engine = MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(content = "{}", status = HttpStatusCode.OK)
        }
        val api = CoinGeckoApiImpl(HttpClient(engine) { install(ContentNegotiation) { json() } })

        api.getContractsPrice(Chain.Mantle, listOf("0xabc"), listOf("usd"))

        assertContains(requestedUrl, "/token_price/mantle")
        assertContains(requestedUrl, "contract_addresses=0xabc")
        assertContains(requestedUrl, "vs_currencies=usd")
    }

    @Test
    fun `getContractsPrice resolves Sui to the sui CoinGecko platform id`() = runTest {
        lateinit var requestedUrl: String
        val engine = MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(content = "{}", status = HttpStatusCode.OK)
        }
        val api = CoinGeckoApiImpl(HttpClient(engine) { install(ContentNegotiation) { json() } })

        api.getContractsPrice(
            Chain.Sui,
            listOf(
                "0xdba34672e30cb065b1f93e3ab55318768fd6fef66c15942c9f7cb846e2f900e7::usdc::USDC"
            ),
            listOf("usd"),
        )

        assertContains(requestedUrl, "/token_price/sui")
        assertContains(requestedUrl, "vs_currencies=usd")
    }

    @Test
    fun `getContractsPrice still degrades to an empty map for a chain without a mapping`() =
        runTest {
            var requestAttempted = false
            val engine = MockEngine {
                requestAttempted = true
                respond(content = "{}", status = HttpStatusCode.OK)
            }
            val api =
                CoinGeckoApiImpl(HttpClient(engine) { install(ContentNegotiation) { json() } })

            val result = api.getContractsPrice(Chain.Sei, listOf("0xabc"), listOf("usd"))

            assertFalse(requestAttempted, "an unmapped chain must not reach the network")
            assertEquals(emptyMap<String, CurrencyToPrice>(), result)
        }
}
