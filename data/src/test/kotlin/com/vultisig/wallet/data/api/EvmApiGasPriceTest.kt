package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.appendIfNameAbsent
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Behavioural tests for [EvmApiImp.getGasPrice] around the RPC-error-swallow fix (issue #5399): a
 * failed `eth_gasPrice` read must propagate instead of collapsing into a fake `0`, which would let
 * BSC's legacy-gas signing path (the only [com.vultisig.wallet.data.models.supportsLegacyGas]
 * chain) sign a transaction with `gasPrice = 0`.
 */
class EvmApiGasPriceTest {

    private fun api(client: io.ktor.client.HttpClient) =
        EvmApiImp(client, "https://api.vultisig.com/bsc/", Chain.BscChain)

    @Test
    fun `getGasPrice propagates an RPC failure instead of swallowing it into zero`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":null,"error":{"code":-32005,"message":"rate limited"}}""",
            )

        assertFailsWith<NetworkException> { api(client).getGasPrice() }
    }

    @Test
    fun `getGasPrice returns the parsed price on success`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":"0x3b9aca00","error":null}""",
            )

        assertEquals(BigInteger.valueOf(1_000_000_000L), api(client).getGasPrice())
    }

    // The eth_estimateGas call's gasPrice field only sizes the estimate, never a signed value
    // (iOS's equivalent doesn't send it at all) — a gas-price RPC failure here must not block
    // gas-limit estimation for ERC-20 transfers on every EVM chain, and must fall back to the
    // zero gasPrice the estimate call previously always saw.
    @Test
    fun `estimateGasForERC20Transfer sends a zero gasPrice fallback when the RPC call fails`() =
        runTest {
            val responses =
                listOf(
                    """{"id":1,"result":"0x1","error":null}""", // nonce
                    """{"id":1,"result":null,"error":{"code":-32005,"message":"boom"}}""", // gas
                    // price
                    """{"id":1,"result":"0x5208","error":null}""", // estimate
                )
            val requestBodies = mutableListOf<String>()
            var callIndex = 0
            val client =
                HttpClient(
                    MockEngine { request ->
                        requestBodies.add(request.body.toByteArray().decodeToString())
                        respond(
                            content = responses[callIndex++],
                            status = HttpStatusCode.OK,
                            headers = MockHttpClient.JSON_HEADERS,
                        )
                    }
                ) {
                    install(ContentNegotiation) { json() }
                    install(DefaultRequest) {
                        headers.appendIfNameAbsent(
                            HttpHeaders.ContentType,
                            ContentType.Application.Json.toString(),
                        )
                    }
                }

            val limit =
                api(client)
                    .estimateGasForERC20Transfer(
                        senderAddress = "0x1111111111111111111111111111111111111111",
                        contractAddress = "0x2222222222222222222222222222222222222222",
                        recipientAddress = "0x3333333333333333333333333333333333333333",
                        value = BigInteger.TEN,
                    )

            assertEquals(BigInteger.valueOf(21_000L), limit)
            assertTrue(
                requestBodies[2].contains(""""gasPrice":"0x0""""),
                "expected the estimate request to fall back to gasPrice 0x0, got ${requestBodies[2]}",
            )
        }
}
