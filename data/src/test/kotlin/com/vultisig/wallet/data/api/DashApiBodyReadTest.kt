package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Characterization tests for [DashApiImpl.getAddressUtxos] success path. Pins the behavior that a
 * 200 OK response with a valid RPC result is decoded via `body<DashRpcResponse>()` (or the
 * equivalent `bodyOrThrow<DashRpcResponse>()` after the refactor) and mapped to a list of
 * [com.vultisig.wallet.data.models.payload.UtxoInfo].
 */
class DashApiBodyReadTest {

    private fun newApi(status: HttpStatusCode, body: String): DashApi =
        DashApiImpl(httpClient = MockHttpClient.respondingWith(status, body))

    @Test
    fun `getAddressUtxos returns mapped UtxoInfo list on success`() = runBlocking {
        val body =
            """
            {
              "id": "vultisig",
              "result": [
                {
                  "address": "XdAUmwtig27HBG6WfYyHAzP8n6J2yxsD91",
                  "txid": "abc123txid",
                  "outputIndex": 2,
                  "script": "76a914deadbeef88ac",
                  "satoshis": 500000,
                  "height": 1800000
                }
              ],
              "error": null
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.OK, body)

        val result = api.getAddressUtxos("XdAUmwtig27HBG6WfYyHAzP8n6J2yxsD91")

        assertEquals(1, result.size)
        assertEquals("abc123txid", result[0].hash)
        assertEquals(500000L, result[0].amount)
        assertEquals(2u, result[0].index)
    }

    @Test
    fun `getAddressUtxos returns empty list when result is null`() = runBlocking {
        val body =
            """
            {
              "id": "vultisig",
              "result": null,
              "error": null
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.OK, body)

        val result = api.getAddressUtxos("XdAUmwtig27HBG6WfYyHAzP8n6J2yxsD91")

        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun `getAddressUtxos maps multiple utxos correctly`() = runBlocking {
        val body =
            """
            {
              "id": "vultisig",
              "result": [
                {
                  "address": "XdAUmwtig27HBG6WfYyHAzP8n6J2yxsD91",
                  "txid": "tx1",
                  "outputIndex": 0,
                  "script": "script1",
                  "satoshis": 100000,
                  "height": 1000000
                },
                {
                  "address": "XdAUmwtig27HBG6WfYyHAzP8n6J2yxsD91",
                  "txid": "tx2",
                  "outputIndex": 1,
                  "script": "script2",
                  "satoshis": 200000,
                  "height": 1000001
                }
              ],
              "error": null
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.OK, body)

        val result = api.getAddressUtxos("XdAUmwtig27HBG6WfYyHAzP8n6J2yxsD91")

        assertEquals(2, result.size)
        assertEquals("tx1", result[0].hash)
        assertEquals(100000L, result[0].amount)
        assertEquals(0u, result[0].index)
        assertEquals("tx2", result[1].hash)
        assertEquals(200000L, result[1].amount)
        assertEquals(1u, result[1].index)
    }
}
