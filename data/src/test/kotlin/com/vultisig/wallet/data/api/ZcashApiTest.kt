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

class ZcashApiTest {

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `getConsensusBranchIdHex reverses the big-endian nextblock to little-endian hex`() =
        runBlocking {
            val api =
                zcashApi(
                    MockEngine {
                        respond(
                            content =
                                """
                                {"jsonrpc":"1.0","result":{"chain":"main","blocks":3373922,
                                "consensus":{"chaintip":"5437f330","nextblock":"5437f330"}},"id":"1"}
                                """
                                    .trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = jsonHeaders,
                        )
                    }
                )

            assertEquals("30f33754", api.getConsensusBranchIdHex())
        }

    @Test
    fun `getConsensusBranchIdHex returns null when consensus block is missing`() = runBlocking {
        val api =
            zcashApi(
                MockEngine {
                    respond(
                        content = """{"jsonrpc":"1.0","result":{"chain":"main"},"id":"1"}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )

        assertNull(api.getConsensusBranchIdHex())
    }

    @Test
    fun `getConsensusBranchIdHex returns null on a malformed branch id`() = runBlocking {
        val api =
            zcashApi(
                MockEngine {
                    respond(
                        content = """{"result":{"consensus":{"nextblock":"not-hex!"}},"id":"1"}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )

        assertNull(api.getConsensusBranchIdHex())
    }

    @Test
    fun `getConsensusBranchIdHex returns null on a server error instead of throwing`() =
        runBlocking {
            val api =
                zcashApi(
                    MockEngine {
                        respond(content = "", status = HttpStatusCode.InternalServerError)
                    }
                )

            assertNull(api.getConsensusBranchIdHex())
        }

    @Test
    fun `getConsensusBranchIdHex posts to the vultisig zcash proxy`() = runBlocking {
        lateinit var requestedUrl: String
        val api =
            zcashApi(
                MockEngine { request ->
                    requestedUrl = request.url.toString()
                    respond(
                        content = """{"result":{"consensus":{"nextblock":"5437f330"}},"id":"1"}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )

        api.getConsensusBranchIdHex()
        assertEquals("https://api.vultisig.com/zcash/", requestedUrl)
    }

    @Test
    fun `getConsensusBranchIdHex caches a successful fetch and skips the second request`() =
        runBlocking {
            var calls = 0
            val api =
                zcashApi(
                    MockEngine {
                        calls++
                        respond(
                            content =
                                """{"result":{"consensus":{"nextblock":"5437f330"}},"id":"1"}""",
                            status = HttpStatusCode.OK,
                            headers = jsonHeaders,
                        )
                    }
                )

            assertEquals("30f33754", api.getConsensusBranchIdHex())
            assertEquals("30f33754", api.getConsensusBranchIdHex())
            assertEquals(1, calls)
        }

    @Test
    fun `getConsensusBranchIdHex does not cache a failure and retries on the next call`() =
        runBlocking {
            var calls = 0
            val api =
                zcashApi(
                    MockEngine {
                        calls++
                        if (calls == 1) {
                            respond(content = "", status = HttpStatusCode.InternalServerError)
                        } else {
                            respond(
                                content =
                                    """{"result":{"consensus":{"nextblock":"5437f330"}},"id":"1"}""",
                                status = HttpStatusCode.OK,
                                headers = jsonHeaders,
                            )
                        }
                    }
                )

            assertNull(api.getConsensusBranchIdHex())
            assertEquals("30f33754", api.getConsensusBranchIdHex())
            assertEquals(2, calls)
        }

    private fun zcashApi(engine: MockEngine): ZcashApi =
        ZcashApiImpl(
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        )
}
