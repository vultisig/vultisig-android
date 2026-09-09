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
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class ZcashApiTest {

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private val address = "t1KsLcyPHqNCdKuVEVfeicPKt3vfsFDBLcT"

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

    @Test
    fun `getAddressBalance returns the zatoshi balance reported by the node`() = runBlocking {
        val api =
            zcashApi(
                MockEngine {
                    respond(
                        content =
                            """{"result":{"balance":123456789,"received":200000000},"id":"1"}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )

        assertEquals(BigInteger("123456789"), api.getAddressBalance(address))
    }

    @Test
    fun `getAddressBalance returns null on an RPC error so the caller can fall back`() =
        runBlocking {
            val api =
                zcashApi(
                    MockEngine {
                        respond(
                            content =
                                """{"result":null,"error":{"code":-32601,"message":"Method not found"},"id":"1"}""",
                            status = HttpStatusCode.OK,
                            headers = jsonHeaders,
                        )
                    }
                )

            assertNull(api.getAddressBalance(address))
        }

    @Test
    fun `getAddressBalance returns null on a server error instead of throwing`() = runBlocking {
        val api =
            zcashApi(
                MockEngine { respond(content = "", status = HttpStatusCode.InternalServerError) }
            )

        assertNull(api.getAddressBalance(address))
    }

    @Test
    fun `getAddressUtxos maps the node's outputs to UtxoInfo`() = runBlocking {
        val api =
            zcashApi(
                MockEngine {
                    respond(
                        content =
                            """
                            {"result":[
                              {"address":"$address","txid":"tx1","outputIndex":0,
                               "script":"76a914deadbeef88ac","satoshis":100000,"height":3000000},
                              {"address":"$address","txid":"tx2","outputIndex":3,
                               "script":"76a914deadbeef88ac","satoshis":250000,"height":3000001}
                            ],"id":"1"}
                            """
                                .trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )

        val utxos = api.getAddressUtxos(address)

        assertEquals(2, utxos.size)
        assertEquals("tx1", utxos[0].hash)
        assertEquals(100000L, utxos[0].amount)
        assertEquals(0u, utxos[0].index)
        assertEquals("tx2", utxos[1].hash)
        assertEquals(250000L, utxos[1].amount)
        assertEquals(3u, utxos[1].index)
    }

    @Test
    fun `getAddressUtxos returns an empty list when the address has no outputs`() = runBlocking {
        val api =
            zcashApi(
                MockEngine {
                    respond(
                        content = """{"result":[],"error":null,"id":"1"}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )

        assertEquals(emptyList(), api.getAddressUtxos(address))
    }

    /**
     * A failed read must be distinguishable from an empty one, or a send is built with no inputs.
     */
    @Test
    fun `getAddressUtxos throws on an RPC error rather than reporting no outputs`() = runBlocking {
        val api =
            zcashApi(
                MockEngine {
                    respond(
                        content =
                            """{"result":null,"error":{"code":-32601,"message":"Method not found"},"id":"1"}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )

        assertFailsWith<IllegalStateException> { api.getAddressUtxos(address) }
        Unit
    }

    private fun zcashApi(engine: MockEngine): ZcashApi =
        ZcashApiImpl(
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        )
}
