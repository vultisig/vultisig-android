package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

import com.vultisig.wallet.data.utils.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class QbtcProofServiceTest {

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun service(handler: (path: String) -> Pair<HttpStatusCode, String>) =
        QbtcProofServiceImpl(
            HttpClient(
                MockEngine { request ->
                    val (status, body) = handler(request.url.encodedPath)
                    respond(content = body, status = status, headers = jsonHeaders)
                }
            ) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        )

    private val request =
        ClaimProofRequest.create(
            rHex = "01".repeat(24),
            sHex = "02".repeat(32),
            compressedPubkeyHex = "03".repeat(33),
            utxos = listOf(ClaimableUtxo(txid = "aa".repeat(32), vout = 0, amount = 1_000)),
            claimerAddress = "qbtc1abc",
            chainId = QbtcClaimConfig.CHAIN_ID,
        )

    @Test
    fun `isHealthy is true only when healthy and setup loaded`() = runTest {
        assertTrue(
            service { HttpStatusCode.OK to """{"status":"healthy","setup_loaded":true}""" }
                .isHealthy()
        )
        assertFalse(
            service { HttpStatusCode.OK to """{"status":"degraded","setup_loaded":true}""" }
                .isHealthy()
        )
        assertFalse(
            service { HttpStatusCode.OK to """{"status":"healthy","setup_loaded":false}""" }
                .isHealthy()
        )
    }

    @Test
    fun `isHealthy swallows transport failures and returns false`() = runTest {
        assertFalse(service { HttpStatusCode.InternalServerError to "boom" }.isHealthy())
    }

    @Test
    fun `generateProof decodes the prove response`() = runTest {
        val body =
            """{"proof":"ff00","message_hash":"${"bb".repeat(32)}","address_hash":"${"cc".repeat(20)}","qbtc_address_hash":"${"dd".repeat(32)}","tx_hash":"${"ab".repeat(32)}"}"""
        val response = service { HttpStatusCode.OK to body }.generateProof(request)
        assertEquals("ff00", response.proof)
        assertEquals("ab".repeat(32), response.txHash)
    }

    @Test
    fun `generateProof throws on a non-2xx response`() = runTest {
        assertThrows<NetworkException> {
            service { HttpStatusCode.BadGateway to "prover offline" }.generateProof(request)
        }
    }
}
