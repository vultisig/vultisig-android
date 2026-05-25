package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.CosmosThorChainResponseSerializerImpl
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Characterization tests for every `.body<...>()` call in [CosmosApiImp]. Each test uses a 200 OK
 * mock response and asserts the exact value extracted by the method, pinning the success-path
 * behavior so it survives the `body<T>()` → `bodyOrThrow<T>()` migration.
 */
class CosmosApiBodyReadTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun newApi(body: String): CosmosApi =
        CosmosApiImp(
            httpClient = MockHttpClient.respondingWith(HttpStatusCode.OK, body),
            rpcEndpoint = "https://example.test",
            json = json,
            cosmosThorChainResponseSerializer = CosmosThorChainResponseSerializerImpl(json),
        )

    // ── getBalance ──────────────────────────────────────────────────────────────
    // Uses response.body<CosmosBalanceResponse>(); returns resp.balances.

    @Test
    fun `getBalance returns list of CosmosBalance from CosmosBalanceResponse`() = runTest {
        val body =
            """
            {
              "balances": [
                { "denom": "uatom", "amount": "500000" }
              ]
            }
            """
                .trimIndent()
        val api = newApi(body)

        val result = api.getBalance("cosmos1abc123")

        assertEquals(1, result.size)
        assertEquals("uatom", result[0].denom)
        assertEquals("500000", result[0].amount)
    }

    // ── getWasmTokenBalance ─────────────────────────────────────────────────────
    // Uses .body<JsonObject>()["data"]?.jsonObject?.get("balance")?.jsonPrimitive?.content.
    // Returns a CosmosBalance with denom = contractAddress and amount from JSON.

    @Test
    fun `getWasmTokenBalance extracts balance string from data JsonObject`() = runTest {
        val body =
            """
            {
              "data": {
                "balance": "42000000"
              }
            }
            """
                .trimIndent()
        val contractAddress = "cosmos1contractXYZ"
        val api = newApi(body)

        val result = api.getWasmTokenBalance("cosmos1owner", contractAddress)

        assertEquals(contractAddress, result.denom)
        assertEquals("42000000", result.amount)
    }

    // ── getIbcDenomTraces ───────────────────────────────────────────────────────
    // Uses .body<CosmosIbcDenomTraceJson>().denomTrace!!

    @Test
    fun `getIbcDenomTraces returns denom_trace path and base_denom`() = runTest {
        val body =
            """
            {
              "denom_trace": {
                "path": "transfer/channel-0",
                "base_denom": "uosmo"
              }
            }
            """
                .trimIndent()
        // Pass a real ibc/ address; the impl strips the "ibc/" prefix before building the URL
        val api = newApi(body)

        val result = api.getIbcDenomTraces("ibc/AAABBBCCC111")

        assertEquals("transfer/channel-0", result.path)
        assertEquals("uosmo", result.baseDenom)
    }

    // ── getLatestBlock ──────────────────────────────────────────────────────────
    // Uses .body<JsonObject>() then drills: .jsonObject["block"]?.jsonObject?.get("header")
    //   ?.jsonObject?.get("height")?.jsonPrimitive?.content

    @Test
    fun `getLatestBlock extracts height string from nested block header`() = runTest {
        val body =
            """
            {
              "block": {
                "header": {
                  "height": "1234567"
                }
              }
            }
            """
                .trimIndent()
        val api = newApi(body)

        val result = api.getLatestBlock()

        assertEquals("1234567", result)
    }
}
