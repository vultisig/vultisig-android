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
 * Pins the `chain` query param LI.FI's price-fallback endpoint receives for every EVM chain,
 * matching iOS's `chain.ticker.lowercased()`. Every non-Ethereum EVM chain used to throw instead of
 * resolving a real identifier.
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
    fun `every EVM chain resolves a lifi chain id matching iOS ticker lowercased`() = runTest {
        val evmChainToLifiId =
            mapOf(
                Chain.Ethereum to "eth",
                Chain.Avalanche to "avax",
                Chain.Base to "base",
                Chain.Blast to "blast",
                Chain.Arbitrum to "arb",
                Chain.Polygon to "pol",
                Chain.Optimism to "op",
                Chain.BscChain to "bnb",
                Chain.CronosChain to "cro",
                Chain.ZkSync to "zk",
                Chain.Mantle to "mnt",
                Chain.Sei to "sei",
                Chain.Hyperliquid to "hype",
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
