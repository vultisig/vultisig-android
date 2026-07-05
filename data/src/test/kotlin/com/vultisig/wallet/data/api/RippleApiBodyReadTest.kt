package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Characterization tests for [RippleApiImp] methods that contain a raw `body<…>()` call:
 * - [RippleApiImp.broadcastTransaction] — line 46:
 *   `response.body<RippleBroadcastResponseResponseJson>()`
 * - [RippleApiImp.fetchAccountsInfo] — line 135: `response.body<RippleAccountInfoResponseJson>()`
 *
 * Pins the success-path behavior so it remains identical after the body-read refactor that replaces
 * `body<T>()` with `bodyOrThrow<T>()`.
 *
 * Methods NOT covered:
 * - [RippleApiImp.getTsStatus] — already uses `bodyOrThrow<RippleBroadcastSuccessResponseJson>()`,
 *   no raw `body<>()` call.
 * - [RippleApiImp.fetchServerState] — already uses `bodyOrThrow<RippleServerStateResponseJson>()`,
 *   no raw `body<>()` call.
 * - [RippleApiImp.getBalance] — delegates to `fetchAccountsInfo` and `fetchServerState`; no direct
 *   `body<>()` call.
 */
class RippleApiBodyReadTest {

    private fun newApi(status: HttpStatusCode, body: String): RippleApi =
        RippleApiImp(http = MockHttpClient.respondingWith(status, body))

    // ── broadcastTransaction ────────────────────────────────────────────────

    @Test
    fun `broadcastTransaction returns tx hash on tesSUCCESS`() = runBlocking {
        val body =
            """
            {
              "result": {
                "engine_result": "tesSUCCESS",
                "engine_result_message": "The transaction was applied.",
                "tx_json": {
                  "hash": "RIPPLEHASH01"
                }
              }
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.OK, body)

        val result = api.broadcastTransaction("signedtx")

        assertEquals("RIPPLEHASH01", result)
    }

    // A genuine rejection must throw, not return the engine result message as a fake txid — the
    // keysign would otherwise persist the rejection text as a transaction hash and show success.
    @Test
    fun `broadcastTransaction throws engine_result_message when engineResult is a genuine rejection`() {
        val body =
            """
            {
              "result": {
                "engine_result": "temBAD_FEE",
                "engine_result_message": "Fee must be positive.",
                "tx_json": {
                  "hash": ""
                }
              }
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.OK, body)

        val error =
            assertThrows<IllegalStateException> {
                runBlocking { api.broadcastTransaction("signedtx") }
            }

        assertEquals("Fee must be positive.", error.message)
    }

    @Test
    fun `broadcastTransaction recovers hash from tx_json when result message matches already-applied wording`() =
        runBlocking {
            val body =
                """
                {
                  "result": {
                    "engine_result": "tefPAST_SEQ",
                    "engine_result_message": "The transaction was applied. Only final in a validated ledger.",
                    "tx_json": {
                      "hash": "RECOVEREDHASH"
                    }
                  }
                }
                """
                    .trimIndent()
            val api = newApi(HttpStatusCode.OK, body)

            val result = api.broadcastTransaction("signedtx")

            assertEquals("RECOVEREDHASH", result)
        }

    // ── fetchAccountsInfo ───────────────────────────────────────────────────

    @Test
    fun `fetchAccountsInfo returns decoded RippleAccountInfoResponseJson on success`() =
        runBlocking {
            val body =
                """
                {
                  "result": {
                    "account_data": {
                      "Balance": "99000000",
                      "Sequence": 7,
                      "OwnerCount": 3
                    },
                    "status": "success",
                    "validated": true,
                    "ledger_current_index": 80000000
                  }
                }
                """
                    .trimIndent()
            val api = newApi(HttpStatusCode.OK, body)

            val result = api.fetchAccountsInfo("rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh")

            assertEquals("99000000", result?.result?.accountData?.balance)
            assertEquals(7, result?.result?.accountData?.sequence)
            assertEquals(3, result?.result?.accountData?.ownerCount)
            assertEquals("success", result?.result?.status)
        }

    @Test
    fun `fetchAccountsInfo returns object with null result fields when response is empty result`() =
        runBlocking {
            val body =
                """
                {
                  "result": {}
                }
                """
                    .trimIndent()
            val api = newApi(HttpStatusCode.OK, body)

            val result = api.fetchAccountsInfo("rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh")

            assertEquals(null, result?.result?.accountData)
            assertEquals(null, result?.result?.status)
        }
}
