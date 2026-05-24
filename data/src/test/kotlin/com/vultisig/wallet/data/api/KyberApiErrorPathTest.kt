package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteDeserialized
import com.vultisig.wallet.data.api.swapAggregators.KyberApi
import com.vultisig.wallet.data.api.swapAggregators.KyberApiImpl
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.KyberSwapQuoteResponseJsonSerializer
import io.ktor.http.HttpStatusCode
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Characterization test for [KyberApiImpl.getSwapQuote]'s non-2xx branch. It reads the error body
 * and surfaces [KyberSwapQuoteDeserialized.Error] with the parsed message. This pins the behavior
 * that must be preserved when the error-body read switches from `body<String>()` to the
 * non-throwing `bodyAsText()`.
 */
class KyberApiErrorPathTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun newApi(status: HttpStatusCode, body: String): KyberApi =
        KyberApiImpl(
            httpClient = MockHttpClient.respondingWith(status, body),
            kyberSwapQuoteResponseJsonSerializer = mockk<KyberSwapQuoteResponseJsonSerializer>(),
            json = json,
        )

    @Test
    fun `getSwapQuote returns Error with parsed message on non-2xx response`() = runBlocking {
        val body =
            """
            {
              "message": "insufficient liquidity"
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.BadRequest, body)

        val result =
            api.getSwapQuote(
                chain = Chain.Ethereum,
                srcTokenContractAddress = "0xsrc",
                dstTokenContractAddress = "0xdst",
                amount = "1000",
                srcAddress = "0xfrom",
                affiliateBps = 0,
            )

        assertTrue(result is KyberSwapQuoteDeserialized.Error)
        assertEquals(
            "insufficient liquidity",
            (result as KyberSwapQuoteDeserialized.Error).error.message,
        )
    }
}
