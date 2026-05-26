package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Characterization tests for [FourByteApiImpl.decodeFunction] success path. Pins the behavior that
 * a 200 OK response is decoded via `body<FourByteResponseJson>()` (or the equivalent
 * `bodyOrThrow<FourByteResponseJson>()` after the refactor) and the first element's `textSignature`
 * is returned (or `null` when the list is empty).
 */
class FourByteApiBodyReadTest {

    private fun newApi(status: HttpStatusCode, body: String): FourByteApi =
        FourByteApiImpl(httpClient = MockHttpClient.respondingWith(status, body))

    @Test
    fun `decodeFunction returns textSignature of first result on success`() = runTest {
        val body =
            """
            {
              "results": [
                {
                  "id": 1,
                  "created_at": "2020-01-01T00:00:00.000000Z",
                  "text_signature": "transfer(address,uint256)",
                  "hex_signature": "0xa9059cbb"
                }
              ]
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.OK, body)

        val result = api.decodeFunction("0xa9059cbb")

        assertEquals("transfer(address,uint256)", result)
    }

    @Test
    fun `decodeFunction returns null when results list is empty`() = runTest {
        val body =
            """
            {
              "results": []
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.OK, body)

        val result = api.decodeFunction("0xdeadbeef")

        assertNull(result)
    }

    @Test
    fun `decodeFunction returns first textSignature when multiple results exist`() = runTest {
        val body =
            """
            {
              "results": [
                {
                  "id": 1,
                  "created_at": "2020-01-01T00:00:00.000000Z",
                  "text_signature": "approve(address,uint256)",
                  "hex_signature": "0x095ea7b3"
                },
                {
                  "id": 2,
                  "created_at": "2020-02-01T00:00:00.000000Z",
                  "text_signature": "other(address,uint256)",
                  "hex_signature": "0x095ea7b3"
                }
              ]
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.OK, body)

        val result = api.decodeFunction("0x095ea7b3")

        assertEquals("approve(address,uint256)", result)
    }
}
