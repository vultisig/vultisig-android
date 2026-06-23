package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.utils.LiFiSwapQuoteResponseSerializerImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.assertContains
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/**
 * Pins that the LI.FI quote request always carries the integrator + (VULT-discounted) fee params,
 * including on Solana-involving routes. LI.FI rejected those params on Solana when Solana support
 * first landed (#1535); it now supports them, so the affiliate fee + discount apply uniformly.
 */
class LiFiChainApiImplRequestTest {

    // Arbitrum / Solana LI.FI (1inch-style) chain ids.
    private val arbitrumChainId = "42161"
    private val solanaChainId = "1151111081099710"

    @Test
    fun `sends integrator and fee on a Solana-destination route`() = runBlocking {
        val url = captureQuoteUrl(toChain = solanaChainId, bpsDiscount = 0)

        assertContains(url, "integrator=vultisig-android")
        // 50 bps, no discount -> fee fraction 0.005.
        assertContains(url, "fee=0.005")
    }

    @Test
    fun `applies the VULT discount to the fee on a Solana route`() = runBlocking {
        val url = captureQuoteUrl(toChain = solanaChainId, bpsDiscount = 20)

        assertContains(url, "integrator=vultisig-android")
        // 50 bps - 20 bps discount -> 30 bps -> fee fraction 0.003.
        assertContains(url, "fee=0.003")
    }

    private suspend fun captureQuoteUrl(toChain: String, bpsDiscount: Int): String {
        lateinit var requestedUrl: String
        val json = Json { ignoreUnknownKeys = true }
        val engine = MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(content = "", status = HttpStatusCode.OK)
        }
        val api =
            LiFiChainApiImpl(HttpClient(engine), LiFiSwapQuoteResponseSerializerImpl(json), json)

        api.getSwapQuote(
            fromChain = arbitrumChainId,
            toChain = toChain,
            fromToken = "0xaf88d065e77c8cC2239327C5EDb3A432268e5831",
            toToken = "SOL",
            fromAmount = "2000000",
            fromAddress = "0x552008c0f6870c2f77e5cC1d2eb9bdff03e30Ea0",
            toAddress = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM",
            bpsDiscount = bpsDiscount,
            slippageBps = null,
        )
        return requestedUrl
    }
}
