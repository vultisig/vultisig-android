package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.appendIfNameAbsent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class RpcHealthProbeImplTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun client(status: HttpStatusCode, body: String): HttpClient =
        HttpClient(
            MockEngine {
                respond(
                    content = body,
                    status = status,
                    headers =
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        ) {
            install(ContentNegotiation) { json(json, ContentType.Any) }
            install(DefaultRequest) {
                headers.appendIfNameAbsent(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                )
            }
        }

    @Test
    fun `evm matching chainId is reachable and network-verified`() = runBlocking {
        // 0x1 == 1 == Ethereum mainnet
        val probe =
            RpcHealthProbeImpl(
                client(HttpStatusCode.OK, """{"id":1,"result":"0x1","error":null}""")
            )
        val result = probe.probe(Chain.Ethereum, "https://node.example")
        assertInstanceOf(RpcHealthResult.Reachable::class.java, result)
        assertTrue((result as RpcHealthResult.Reachable).networkVerified)
    }

    @Test
    fun `evm mismatched chainId is wrong chain`() = runBlocking {
        // 0x89 == 137 (Polygon), but we ask for Ethereum
        val probe =
            RpcHealthProbeImpl(
                client(HttpStatusCode.OK, """{"id":1,"result":"0x89","error":null}""")
            )
        assertEquals(
            RpcHealthResult.WrongChain,
            probe.probe(Chain.Ethereum, "https://node.example"),
        )
    }

    @Test
    fun `evm missing result is invalid response`() = runBlocking {
        val probe =
            RpcHealthProbeImpl(client(HttpStatusCode.OK, """{"id":1,"result":null,"error":null}"""))
        assertEquals(
            RpcHealthResult.InvalidResponse,
            probe.probe(Chain.Ethereum, "https://node.example"),
        )
    }

    @Test
    fun `cosmos success is reachable but unverified`() = runBlocking {
        val probe = RpcHealthProbeImpl(client(HttpStatusCode.OK, """{"default_node_info":{}}"""))
        val result = probe.probe(Chain.GaiaChain, "https://cosmos.example")
        assertInstanceOf(RpcHealthResult.Reachable::class.java, result)
        assertTrue(!(result as RpcHealthResult.Reachable).networkVerified)
    }

    @Test
    fun `server error is unreachable`() = runBlocking {
        val probe = RpcHealthProbeImpl(client(HttpStatusCode.InternalServerError, "boom"))
        assertEquals(
            RpcHealthResult.Unreachable,
            probe.probe(Chain.GaiaChain, "https://cosmos.example"),
        )
    }

    @Test
    fun `blank url is unreachable without a request`() = runBlocking {
        val probe = RpcHealthProbeImpl(client(HttpStatusCode.OK, "{}"))
        assertEquals(RpcHealthResult.Unreachable, probe.probe(Chain.Ethereum, "   "))
    }
}
