package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.utils.CosmosThorChainResponseSerializerImpl
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

class CosmosApiTest {

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `getTxStatus returns null on 404 without throwing`() = runBlocking {
        val api = cosmosApi(MockEngine { respond(content = "", status = HttpStatusCode.NotFound) })

        assertNull(api.getTxStatus("ABC123"))
    }

    @Test
    fun `getTxStatus returns parsed response on 200`() = runBlocking {
        val api =
            cosmosApi(
                MockEngine {
                    respond(
                        content = """{"tx_response":{"height":"42","txhash":"ABC123","code":0}}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
            )

        val result = api.getTxStatus("ABC123")

        assertEquals("42", result?.txResponse?.height)
        assertEquals("ABC123", result?.txResponse?.txHash)
        assertEquals(0, result?.txResponse?.code)
    }

    private fun cosmosApi(engine: MockEngine): CosmosApi {
        val json = Json { ignoreUnknownKeys = true }
        return CosmosApiImp(
            HttpClient(engine) { install(ContentNegotiation) { json(json) } },
            "https://example.test",
            json,
            CosmosThorChainResponseSerializerImpl(json),
        )
    }
}
