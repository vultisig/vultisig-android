package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.RpcPayload
import com.vultisig.wallet.data.api.models.RpcResponse
import com.vultisig.wallet.data.networkutils.HttpClientConfigurator
import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EvmApiGetNonceTest {

    @Test
    fun `getNonce prefers pending nonce over latest confirmed nonce`() = runTest {
        val expectedRequest =
            RpcPayload(
                method = "eth_getTransactionCount",
                params =
                    buildJsonArray {
                        add(ADDRESS)
                        add("pending")
                    },
            )
        var actualRequest: RpcPayload? = null
        val evmApi =
            EvmApiImp(
                http =
                    mockHttpClient { request ->
                        actualRequest = request.requireRpcPayload()
                        val nonce =
                            when (val blockTag = actualRequest.params[1].jsonPrimitive.content) {
                                "latest" -> LATEST_CONFIRMED_NONCE
                                "pending" -> PENDING_NONCE
                                else -> error("Unexpected block tag: $blockTag")
                            }

                        respond(
                            content =
                                RpcResponse(id = expectedRequest.id, result = nonce, error = null)
                                    .toJson(),
                            status = HttpStatusCode.OK,
                            headers = MockHttpClient.JSON_HEADERS,
                        )
                    },
                rpcUrl = RPC_URL,
            )

        val nonce = evmApi.getNonce(ADDRESS)

        assertEquals(BigInteger("9"), nonce)
        assertEquals(expectedRequest, actualRequest)
    }

    private fun mockHttpClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): HttpClient = HttpClient(MockEngine(handler)) { HttpClientConfigurator(json).configure(this) }

    private fun HttpRequestData.requireRpcPayload(): RpcPayload {
        val content = body as? TextContent ?: error("Expected TextContent but was ${body::class}")
        return json.decodeFromString(content.text)
    }

    private fun RpcResponse.toJson(): String = json.encodeToString(this)

    private companion object {
        const val ADDRESS = "0x123"
        const val LATEST_CONFIRMED_NONCE = "0x8"
        const val PENDING_NONCE = "0x9"
        const val RPC_URL = "https://rpc.example"

        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
