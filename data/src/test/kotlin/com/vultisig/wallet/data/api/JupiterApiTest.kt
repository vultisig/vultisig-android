package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.errors.SwapException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.appendIfNameAbsent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class JupiterApiTest {

    @Test
    fun `quote request pins the default slippage when on Auto`() {
        val (api, captured) = quoteOnlyApi()

        assertThrows(SwapException.RateLimitExceeded::class.java) {
            runBlocking {
                api.getSwapQuote(QUOTE_AMOUNT, INPUT_MINT, OUTPUT_MINT, WALLET, null, null)
            }
        }

        assertEquals("50", captured.slippageBps)
    }

    @Test
    fun `quote request forwards the user-set slippage override`() {
        val (api, captured) = quoteOnlyApi()

        assertThrows(SwapException.RateLimitExceeded::class.java) {
            runBlocking {
                api.getSwapQuote(QUOTE_AMOUNT, INPUT_MINT, OUTPUT_MINT, WALLET, 250, null)
            }
        }

        assertEquals("250", captured.slippageBps)
    }

    @Test
    fun `positive affiliate bps with a quoted fee sends platformFeeBps and feeAccount`() {
        val service = FakeFeeAtaService(feeAccount = FEE_ACCOUNT)
        val (api, captured) = feeApi(service, quotedFeeAmount = "36341")

        assertThrows(SwapException.RateLimitExceeded::class.java) {
            runBlocking {
                api.getSwapQuote(QUOTE_AMOUNT, INPUT_MINT, OUTPUT_MINT, WALLET, null, 45)
            }
        }

        assertEquals("45", captured.platformFeeBps)
        assertTrue(service.resolveCalled, "fee account must be resolved when a fee is quoted")
        assertTrue(
            captured.swapBody!!.contains("\"feeAccount\":\"${FEE_ACCOUNT.feeAccount}\""),
            "swap body must carry the resolved fee account: ${captured.swapBody}",
        )
    }

    @Test
    fun `a null affiliate bps charges no fee`() {
        val service = FakeFeeAtaService(feeAccount = FEE_ACCOUNT)
        val (api, captured) = feeApi(service, quotedFeeAmount = null)

        assertThrows(SwapException.RateLimitExceeded::class.java) {
            runBlocking {
                api.getSwapQuote(QUOTE_AMOUNT, INPUT_MINT, OUTPUT_MINT, WALLET, null, null)
            }
        }

        assertNull(captured.platformFeeBps)
        assertFalse(service.resolveCalled)
        assertFalse(captured.swapBody!!.contains("feeAccount"))
    }

    @Test
    fun `a fee floored to zero by Jupiter sends platformFeeBps but no feeAccount`() {
        // The quote asks for a fee (bps > 0) but Jupiter floors platformFee.amount to 0; we must
        // not
        // derive a fee account or prepend a create-ATA for a zero fee.
        val service = FakeFeeAtaService(feeAccount = FEE_ACCOUNT)
        val (api, captured) = feeApi(service, quotedFeeAmount = "0")

        assertThrows(SwapException.RateLimitExceeded::class.java) {
            runBlocking {
                api.getSwapQuote(QUOTE_AMOUNT, INPUT_MINT, OUTPUT_MINT, WALLET, null, 50)
            }
        }

        assertEquals("50", captured.platformFeeBps)
        assertFalse(service.resolveCalled, "no fee account for a zero-amount fee")
        assertFalse(captured.swapBody!!.contains("feeAccount"))
    }

    @Test
    fun `an unresolvable fee mint fails the quote`() {
        // resolveFeeAccount throwing (missing/unsupported mint or RPC failure) must propagate so
        // the
        // Jupiter quote fails and the picker falls back to another provider.
        val service = FakeFeeAtaService(feeAccount = null)
        val (api, _) = feeApi(service, quotedFeeAmount = "36341")

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                api.getSwapQuote(QUOTE_AMOUNT, INPUT_MINT, OUTPUT_MINT, WALLET, null, 50)
            }
        }
    }

    /**
     * The quote GET returns 429 so [JupiterApi.getSwapQuote] short-circuits before the swap POST
     * and its WalletCore compute-budget step, keeping the slippage tests on the request they assert
     * and free of the native JNI.
     */
    private fun quoteOnlyApi(): Pair<JupiterApi, Captured> {
        val captured = Captured()
        val api =
            jupiterApiImpl(
                service = FakeFeeAtaService(feeAccount = FEE_ACCOUNT),
                engine =
                    MockEngine { request ->
                        captured.slippageBps = request.url.parameters["slippageBps"]
                        respond(content = "", status = HttpStatusCode.TooManyRequests)
                    },
            )
        return api to captured
    }

    /**
     * Lets the quote GET succeed (capturing `platformFeeBps`, returning a route with the given
     * [quotedFeeAmount] platform fee) so the impl proceeds to build and POST the swap request; the
     * swap POST then returns 429, capturing the swap body (with any `feeAccount`) and
     * short-circuiting before the native compute-budget step and the fee-ATA prepend.
     */
    private fun feeApi(
        service: FakeFeeAtaService,
        quotedFeeAmount: String?,
    ): Pair<JupiterApi, Captured> {
        val captured = Captured()
        val api =
            jupiterApiImpl(
                service = service,
                engine =
                    MockEngine { request ->
                        if (request.url.encodedPath.endsWith("/quote")) {
                            captured.platformFeeBps = request.url.parameters["platformFeeBps"]
                            respond(
                                content = routeResponseJson(quotedFeeAmount),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        } else {
                            captured.swapBody = (request.body as? TextContent)?.text
                            respond(content = "", status = HttpStatusCode.TooManyRequests)
                        }
                    },
            )
        return api to captured
    }

    private fun jupiterApiImpl(service: JupiterFeeAtaService, engine: MockEngine): JupiterApi =
        JupiterApiImpl(
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                // Mirror the production client's default JSON content type so the swap POST's
                // JsonObject body serializes; without it ktor cannot pick a converter.
                install(DefaultRequest) {
                    headers.appendIfNameAbsent(
                        HttpHeaders.ContentType,
                        ContentType.Application.Json.toString(),
                    )
                }
            },
            Json { ignoreUnknownKeys = true },
            service,
        )

    private class Captured(
        var slippageBps: String? = null,
        var platformFeeBps: String? = null,
        var swapBody: String? = null,
    )

    /** Fake service: returns [feeAccount] from resolve, or throws when it is null. */
    private class FakeFeeAtaService(private val feeAccount: JupiterFeeAccount?) :
        JupiterFeeAtaService {
        var resolveCalled = false
            private set

        override suspend fun resolveFeeAccount(outputMint: String): JupiterFeeAccount {
            resolveCalled = true
            return feeAccount ?: error("cannot resolve fee account for $outputMint")
        }

        override fun prependCreateFeeAta(
            txData: String,
            fee: JupiterFeeAccount,
            feePayer: String,
        ): String = txData
    }

    private fun routeResponseJson(platformFeeAmount: String?): String {
        val platformFee =
            if (platformFeeAmount == null) ""
            else """"platformFee": { "amount": "$platformFeeAmount", "feeBps": 50 },"""
        return """
            {
              "inputMint": "$INPUT_MINT",
              "inAmount": "1000",
              "outputMint": "$OUTPUT_MINT",
              "outAmount": "1500000",
              "otherAmountThreshold": "0",
              "swapMode": "ExactIn",
              "slippageBps": 50,
              "priceImpactPct": "0",
              $platformFee
              "routePlan": [],
              "contextSlot": 0,
              "timeTaken": 0.0
            }
            """
            .trimIndent()
    }

    private companion object {
        const val QUOTE_AMOUNT = "1000"
        const val INPUT_MINT = "So11111111111111111111111111111111111111112"
        const val OUTPUT_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
        const val WALLET = "Wallet"

        val FEE_ACCOUNT =
            JupiterFeeAccount(
                feeAccount = "tigMQDjwCNAzNndtiX93ZK1p71XaKTTRrQ8mfyp39LS",
                mint = OUTPUT_MINT,
                owner = "8iqhrtBzMcYLR6c6FkzeoMHibedYDkHvLKnX2ArNie5z",
                tokenProgramId = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
            )
    }
}
