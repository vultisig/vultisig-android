package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the `chain` query param LI.FI's price-fallback endpoint receives for every EVM chain: the
 * chain's real numeric EVM chain id, matching iOS's `chain.chainID` and reusing this repo's own
 * [com.vultisig.wallet.data.models.evmChainId]. Every non-Ethereum EVM chain used to throw instead
 * of resolving an identifier; a ticker-based identifier (e.g. "avax", "bnb") was tried first but
 * verified against the live li.quest API to 400 for 7 of the 13 chains, so the numeric id is what's
 * asserted here.
 */
class LiQuestApiImplTest {

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun apiCapturing(onRequest: (HttpRequestData) -> Unit): LiQuestApi =
        LiQuestApiImpl(
            HttpClient(
                MockEngine { request ->
                    onRequest(request)
                    respond(
                        content = """{"priceUSD":"1.23"}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            ) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        )

    @Test
    fun `every EVM chain resolves its numeric lifi chain id`() = runTest {
        val evmChainToLifiId =
            mapOf(
                Chain.Ethereum to "1",
                Chain.Avalanche to "43114",
                Chain.Base to "8453",
                Chain.Blast to "81457",
                Chain.Arbitrum to "42161",
                Chain.Polygon to "137",
                Chain.Optimism to "10",
                Chain.BscChain to "56",
                Chain.CronosChain to "25",
                Chain.ZkSync to "324",
                Chain.Mantle to "5000",
                Chain.Sei to "1329",
                Chain.Hyperliquid to "999",
            )

        for ((chain, expectedChainParam) in evmChainToLifiId) {
            var chainParam: String? = null
            val api = apiCapturing { chainParam = it.url.parameters["chain"] }

            val result = api.getLifiContractPriceUsd(chain, "0xcontract")

            assertEquals(expectedChainParam, chainParam, "unexpected lifi chain id for $chain")
            assertEquals("1.23", result.priceUsd)
        }
    }

    @Test
    fun `non-EVM chain still throws instead of silently sending a wrong identifier`() = runTest {
        val api = apiCapturing {}

        assertFailsWith<IllegalStateException> { api.getLifiContractPriceUsd(Chain.Solana, "mint") }
    }
}
