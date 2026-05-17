package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.errors.CosmosBroadcastException
import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.ThorChainSwapQuoteResponseJsonSerializer
import io.ktor.http.HttpStatusCode
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Unit tests for `MayaChainApiImp.broadcastTransaction`, mirroring `ThorChainApiImplTest`. Covers
 * the success path (code 0), the mempool-cache success path (code 19), and the rejection path that
 * surfaces a typed `CosmosBroadcastException`.
 */
class MayaChainApiImplTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun newApi(httpStatus: HttpStatusCode, body: String): MayaChainApi =
        MayaChainApiImp(
            httpClient = MockHttpClient.respondingWith(httpStatus, body),
            thorChainApi = mockk<ThorChainApi>(),
            thorChainSwapQuoteResponseJsonSerializer =
                mockk<ThorChainSwapQuoteResponseJsonSerializer>(),
            json = json,
        )

    @Test
    fun `broadcastTransaction returns txhash on success code zero`() = runBlocking {
        val body =
            """
            {
              "tx_response": {
                "txhash": "MAYA0",
                "code": 0
              }
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.OK, body)

        val result = api.broadcastTransaction(tx = "{}")

        assertEquals("MAYA0", result)
    }

    @Test
    fun `broadcastTransaction treats ErrTxInMempoolCache (code 19) as success`() = runBlocking {
        val body =
            """
            {
              "tx_response": {
                "txhash": "MAYA19",
                "code": 19
              }
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.OK, body)

        val result = api.broadcastTransaction(tx = "{}")

        assertEquals("MAYA19", result)
    }

    @Test
    fun `broadcastTransaction surfaces sequence mismatch as typed exception`() {
        val body =
            """
            {
              "tx_response": {
                "txhash": "MAYASEQ",
                "code": 32,
                "codespace": "sdk",
                "raw_log": "account sequence mismatch"
              }
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.OK, body)

        val ex =
            assertThrows(CosmosBroadcastException::class.java) {
                runBlocking { api.broadcastTransaction(tx = "{}") }
            }
        assertEquals(32, ex.code)
        assertEquals("sdk", ex.codespace)
        assertEquals("MAYASEQ", ex.txHash)
        assertEquals(
            true,
            ex.message?.startsWith(CosmosBroadcastException.SEQUENCE_MISMATCH_MARKER) == true,
        )
    }
}
