package com.vultisig.wallet.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class PolkadotApiTest {

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `isExtrinsicInChain matches the blake2b-256 hash of an extrinsic in the head block`() =
        runTest {
            var requests = 0
            val api =
                polkadotApi(
                    MockEngine {
                        requests++
                        respond(
                            content = blockResponse(parentHash = PARENT, extrinsics = listOf(EXT)),
                            status = HttpStatusCode.OK,
                            headers = jsonHeaders,
                        )
                    }
                )

            assertTrue(api.isExtrinsicInChain(EXT_HASH, depth = 5))
            // Found at the head, so it must not walk further back.
            assertEquals(1, requests)
        }

    @Test
    fun `isExtrinsicInChain walks back to a parent block via parentHash`() = runTest {
        var requests = 0
        val api =
            polkadotApi(
                MockEngine {
                    requests++
                    val body =
                        if (requests == 1) {
                            blockResponse(parentHash = PARENT, extrinsics = emptyList())
                        } else {
                            blockResponse(parentHash = GENESIS, extrinsics = listOf(EXT))
                        }
                    respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders)
                }
            )

        assertTrue(api.isExtrinsicInChain(EXT_HASH, depth = 5))
        assertEquals(2, requests)
    }

    @Test
    fun `isExtrinsicInChain returns false and stops after depth blocks when not found`() = runTest {
        var requests = 0
        val api =
            polkadotApi(
                MockEngine {
                    requests++
                    respond(
                        content = blockResponse(parentHash = PARENT, extrinsics = emptyList()),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )

        assertEquals(false, api.isExtrinsicInChain(EXT_HASH, depth = 3))
        assertEquals(3, requests)
    }

    private fun blockResponse(parentHash: String, extrinsics: List<String>): String {
        val ext = extrinsics.joinToString(",") { "\"$it\"" }
        return """
            {"jsonrpc":"2.0","id":1,"result":{"block":{
              "header":{"parentHash":"$parentHash","number":"0x1"},
              "extrinsics":[$ext]
            }}}
        """
            .trimIndent()
    }

    private fun polkadotApi(engine: MockEngine): PolkadotApi =
        PolkadotApiImp(
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                install(DefaultRequest) { contentType(ContentType.Application.Json) }
            }
        )

    private companion object {
        // A real Asset Hub inherent extrinsic and its canonical blake2b-256 hash.
        const val EXT = "0x280503000b605dfd869e01"
        const val EXT_HASH = "0xd3b70ba9181914a6c2132283204e411dd826b130dc5bb239f718938e788dee7e"
        const val PARENT = "0x98a821471c5f40bbaf9fd7798f89419ae81b9e6c4434dd16a2adecd55a82c9c2"
        const val GENESIS = "0x91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
    }
}
