package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.CustomRpcRepository
import com.vultisig.wallet.data.utils.CosmosThorChainResponseSerializerImpl
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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the #4787 resolution funnel: the EVM / Cosmos API factories must route requests at the
 * custom RPC override when one is set, and stay byte-identical on the default host when unset.
 */
internal class CustomRpcFunnelTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun capturingClient(captured: MutableList<String>, body: String): HttpClient =
        HttpClient(
            MockEngine { request ->
                captured.add(request.url.toString())
                respond(
                    content = body,
                    status = HttpStatusCode.OK,
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

    private fun repoReturning(url: String?): CustomRpcRepository =
        mockk<CustomRpcRepository>().apply { every { urlFor(any()) } returns url }

    @Test
    fun `evm factory routes to the override host when set`() = runTest {
        val urls = mutableListOf<String>()
        val client =
            capturingClient(urls, """{"jsonrpc":"2.0","id":1,"result":"0x1","error":null}""")
        val factory = EvmApiFactoryImp(client, repoReturning("https://my-node.example/"))

        factory.createEvmApi(Chain.Ethereum).getGasPrice()

        assertTrue(
            urls.isNotEmpty() && urls.all { it.startsWith("https://my-node.example/") },
            "expected override host, got $urls",
        )
    }

    @Test
    fun `evm factory keeps the default host when no override is set`() = runTest {
        val urls = mutableListOf<String>()
        val client =
            capturingClient(urls, """{"jsonrpc":"2.0","id":1,"result":"0x1","error":null}""")
        val factory = EvmApiFactoryImp(client, repoReturning(null))

        factory.createEvmApi(Chain.Ethereum).getGasPrice()

        assertTrue(
            urls.isNotEmpty() && urls.all { it.startsWith("https://api.vultisig.com/eth/") },
            "expected default host, got $urls",
        )
    }

    @Test
    fun `cosmos factory routes to the override host when set`() = runTest {
        val urls = mutableListOf<String>()
        val client = capturingClient(urls, """{"balances":[]}""")
        val factory =
            CosmosApiFactoryImp(
                client,
                json,
                CosmosThorChainResponseSerializerImpl(json),
                repoReturning("https://my-cosmos.example"),
            )

        factory.createCosmosApi(Chain.GaiaChain).getBalance("cosmos1abc")

        assertTrue(
            urls.isNotEmpty() && urls.all { it.startsWith("https://my-cosmos.example") },
            "expected override host, got $urls",
        )
    }

    @Test
    fun `cosmos factory keeps the default host when no override is set`() = runTest {
        val urls = mutableListOf<String>()
        val client = capturingClient(urls, """{"balances":[]}""")
        val factory =
            CosmosApiFactoryImp(
                client,
                json,
                CosmosThorChainResponseSerializerImpl(json),
                repoReturning(null),
            )

        factory.createCosmosApi(Chain.GaiaChain).getBalance("cosmos1abc")

        assertTrue(
            urls.isNotEmpty() && urls.all { it.startsWith("https://cosmos-rest.publicnode.com") },
            "expected default host, got $urls",
        )
    }
}
