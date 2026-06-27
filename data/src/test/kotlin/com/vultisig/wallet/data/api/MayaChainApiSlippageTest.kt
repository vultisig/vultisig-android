package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.utils.ThorChainSwapQuoteResponseJsonSerializer
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Pins the `tolerance_bps` query param sent to MayaChain, mirroring [ThorChainApiSlippageTest]. A 0
 * / Auto tolerance must be omitted so MayaChain never rejects the quote for a limit it never set; a
 * user override is sent verbatim in basis points. The request short-circuits on a deserialization
 * stub so the outgoing params — not the response — are the subject under test.
 */
class MayaChainApiSlippageTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun apiCapturing(onRequest: (HttpRequestData) -> Unit) =
        MayaChainApiImp(
            httpClient =
                HttpClient(
                    MockEngine { request ->
                        onRequest(request)
                        respond("{}", HttpStatusCode.BadRequest, jsonHeaders)
                    }
                ),
            thorChainApi = mockk<ThorChainApi>(),
            thorChainSwapQuoteResponseJsonSerializer =
                mockk<ThorChainSwapQuoteResponseJsonSerializer>(),
            json = json,
        )

    private suspend fun MayaChainApiImp.quoteWith(toleranceBps: Int?) =
        getSwapQuotes(
            address = "maya1dest",
            fromAsset = "ETH.ETH",
            toAsset = "MAYA.CACAO",
            amount = "1000",
            isAffiliate = true,
            bpsDiscount = 0,
            referralCode = "",
            toleranceBps = toleranceBps,
        )

    @Test
    fun `a 300 bps tolerance is sent verbatim`() = runTest {
        var toleranceParam: String? = null
        val api = apiCapturing { toleranceParam = it.url.parameters["tolerance_bps"] }

        api.quoteWith(toleranceBps = 300)

        assertEquals("300", toleranceParam)
    }

    @Test
    fun `auto tolerance of 0 omits the parameter`() = runTest {
        var hasTolerance = true
        val api = apiCapturing { hasTolerance = it.url.parameters.contains("tolerance_bps") }

        api.quoteWith(toleranceBps = 0)

        assertFalse(hasTolerance)
    }

    @Test
    fun `null tolerance omits the parameter`() = runTest {
        var hasTolerance = true
        val api = apiCapturing { hasTolerance = it.url.parameters.contains("tolerance_bps") }

        api.quoteWith(toleranceBps = null)

        assertFalse(hasTolerance)
    }
}
