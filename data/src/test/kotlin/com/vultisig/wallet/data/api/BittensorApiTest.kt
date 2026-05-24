package com.vultisig.wallet.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class BittensorApiTest {

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `getTxStatus returns null on 404 without throwing`() = runBlocking {
        val api =
            bittensorApi(MockEngine { respond(content = "", status = HttpStatusCode.NotFound) })

        assertNull(api.getTxStatus("0xabc"))
    }

    @Test
    fun `getTxStatus returns parsed extrinsic on 200`() = runBlocking {
        val api =
            bittensorApi(
                MockEngine {
                    respond(
                        content = """{"data":[{"success":true,"block_number":42}]}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )

        val result = api.getTxStatus("0xabc")

        assertEquals(true, result?.success)
        assertEquals(42, result?.blockNumber)
    }

    @Test
    fun `getTxStatus returns null when 200 response has empty data array`() = runBlocking {
        val api =
            bittensorApi(
                MockEngine {
                    respond(
                        content = """{"data":[]}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )

        assertNull(api.getTxStatus("0xabc"))
    }

    @Test
    fun `getTxStatus hits the tao-tx v1 proxy path with the hash query`() = runBlocking {
        lateinit var requestedUrl: String
        val api =
            bittensorApi(
                MockEngine { request ->
                    requestedUrl = request.url.toString()
                    respond(
                        content = """{"data":[]}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )

        api.getTxStatus("0xabc")
        assertEquals("https://api.vultisig.com/tao-tx/v1?hash=0xabc", requestedUrl)
    }

    @Test
    fun `getTxStatus parses the live proxy payload shape and ignores extra fields`() = runBlocking {
        val api =
            bittensorApi(
                MockEngine {
                    respond(
                        content =
                            """
                            {
                              "pagination": {"current_page": 1, "per_page": 50, "total_items": 1},
                              "data": [{
                                "timestamp": "2026-05-24T10:36:48Z",
                                "block_number": 8253395,
                                "hash": "0x75c804b9",
                                "id": "8253395-0009",
                                "index": 9,
                                "success": true,
                                "error": null
                              }]
                            }
                            """
                                .trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )

        val result = api.getTxStatus("0x75c804b9")

        assertEquals(true, result?.success)
        assertEquals(8253395, result?.blockNumber)
    }

    @Test
    fun `getTxStatus surfaces success false for a failed extrinsic`() = runBlocking {
        val api =
            bittensorApi(
                MockEngine {
                    respond(
                        content = """{"data":[{"success":false,"block_number":8253395}]}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )

        assertEquals(false, api.getTxStatus("0xabc")?.success)
    }

    private fun bittensorApi(engine: MockEngine): BittensorApi =
        BittensorApiImp(
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        )
}
