package com.vultisig.wallet.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the `slippage` query param sent to LI.FI. LI.FI takes slippage as a decimal fraction, so the
 * basis-point value must be rendered as a plain decimal — tight tolerances (1–9 bps) must not be
 * stringified in scientific notation (e.g. `1.0E-4`), which LI.FI rejects as non-numeric.
 */
class LiFiChainApiSlippageTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun apiCapturing(onRequest: (HttpRequestData) -> Unit) =
        LiFiChainApiImpl(
            httpClient =
                HttpClient(
                    MockEngine { request ->
                        onRequest(request)
                        respond("""{"message":"stop"}""", HttpStatusCode.BadRequest, jsonHeaders)
                    }
                ),
            liFiSwapQuoteResponseSerializer = mockk(),
            json = json,
        )

    private suspend fun LiFiChainApiImpl.quoteWith(slippageBps: Int?) =
        getSwapQuote(
            fromChain = "1",
            toChain = "1",
            fromToken = "0xsrc",
            toToken = "0xdst",
            fromAmount = "1000",
            fromAddress = "0xfrom",
            toAddress = "0xto",
            bpsDiscount = 0,
            slippageBps = slippageBps,
        )

    @Test
    fun `tight 1 bps slippage is sent as a plain decimal, not scientific notation`() = runTest {
        var slippageParam: String? = null
        val api = apiCapturing { slippageParam = it.url.parameters["slippage"] }

        runCatching { api.quoteWith(slippageBps = 1) }

        assertEquals("0.0001", slippageParam)
    }

    @Test
    fun `1 percent slippage is sent as 0,01`() = runTest {
        var slippageParam: String? = null
        val api = apiCapturing { slippageParam = it.url.parameters["slippage"] }

        runCatching { api.quoteWith(slippageBps = 100) }

        assertEquals("0.01", slippageParam)
    }

    @Test
    fun `auto slippage omits the parameter`() = runTest {
        var hasSlippage = true
        val api = apiCapturing { hasSlippage = it.url.parameters.contains("slippage") }

        runCatching { api.quoteWith(slippageBps = null) }

        assertEquals(false, hasSlippage)
    }
}
