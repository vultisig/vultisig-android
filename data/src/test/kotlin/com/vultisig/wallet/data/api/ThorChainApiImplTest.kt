package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.errors.CosmosBroadcastException
import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.ThorChainSwapQuoteResponseJsonSerializer
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
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Unit tests for the two methods that had correctness bugs caught in #4244:
 * - `getTransactionDetail` previously wrote HTTP metadata into the DTO instead of parsing the
 *   Cosmos `tx_response` envelope.
 * - `broadcastTransaction` previously double-read the response body.
 */
class ThorChainApiImplTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun newApi(httpStatus: HttpStatusCode, body: String): ThorChainApi =
        ThorChainApiImpl(
            httpClient = MockHttpClient.respondingWith(httpStatus, body),
            thorChainSwapQuoteResponseJsonSerializer =
                mockk<ThorChainSwapQuoteResponseJsonSerializer>(),
            json = json,
        )

    @Test
    fun `getTransactionDetail parses tx_response code, codespace, and raw_log on success`() =
        runBlocking {
            val body =
                """
                {
                  "tx_response": {
                    "code": 0,
                    "codespace": "",
                    "raw_log": "[]"
                  }
                }
                """
                    .trimIndent()
            val api = newApi(HttpStatusCode.OK, body)

            val result = api.getTransactionDetail(tx = "ABC123")

            assertEquals(0, result.code)
            assertEquals("", result.codeSpace)
            assertEquals("[]", result.rawLog)
        }

    @Test
    fun `getTransactionDetail surfaces non-zero tx_response code and codespace`() = runBlocking {
        val body =
            """
            {
              "tx_response": {
                "code": 11,
                "codespace": "sdk",
                "raw_log": "out of gas"
              }
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.OK, body)

        val result = api.getTransactionDetail(tx = "ABC123")

        assertEquals(11, result.code)
        assertEquals("sdk", result.codeSpace)
        assertEquals("out of gas", result.rawLog)
    }

    @Test
    fun `getTransactionDetail returns pending DTO with null code on 404`() = runBlocking {
        val api = newApi(HttpStatusCode.NotFound, "tx not found yet")

        val result = api.getTransactionDetail(tx = "ABC123")

        assertNull(result.code)
        assertNull(result.codeSpace)
        assertEquals("tx not found yet", result.rawLog)
    }

    @Test
    fun `getTransactionDetail throws on non-2xx non-404 statuses`() {
        val api = newApi(HttpStatusCode.InternalServerError, "boom")

        assertThrows(IllegalStateException::class.java) {
            runBlocking { api.getTransactionDetail(tx = "ABC123") }
        }
    }

    @Test
    fun `broadcastTransaction returns txhash on success code zero`() = runBlocking {
        val body =
            """
            {
              "tx_response": {
                "txhash": "DEADBEEF",
                "code": 0
              }
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.OK, body)

        val result = api.broadcastTransaction(tx = "{}")

        assertEquals("DEADBEEF", result)
    }

    @Test
    fun `broadcastTransaction treats ErrTxInMempoolCache (code 19) as success`() = runBlocking {
        val body =
            """
            {
              "tx_response": {
                "txhash": "CAFEBABE",
                "code": 19
              }
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.OK, body)

        val result = api.broadcastTransaction(tx = "{}")

        assertEquals("CAFEBABE", result)
    }

    @Test
    fun `existsReferralCode returns false when aliases is null in API response`() = runBlocking {
        val body =
            """
            {
              "name": "maya",
              "expire_block_height": 36942828,
              "owner": "thor19d9fem39wayv4ydpp7kgufda940acvepjqmqtl",
              "preferred_asset": ".",
              "preferred_asset_swap_threshold_rune": "0",
              "affiliate_collector_rune": "0",
              "aliases": null
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.OK, body)

        val result = api.existsReferralCode("maya")

        assertEquals(false, result)
    }

    @Test
    fun `existsReferralCode returns true when aliases contains THOR chain entry`() = runBlocking {
        val body =
            """
            {
              "name": "vultisig",
              "expire_block_height": 36942828,
              "owner": "thor19d9fem39wayv4ydpp7kgufda940acvepjqmqtl",
              "preferred_asset": ".",
              "preferred_asset_swap_threshold_rune": "0",
              "affiliate_collector_rune": "0",
              "aliases": [{"chain": "THOR", "address": "thor1abc"}]
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.OK, body)

        val result = api.existsReferralCode("vultisig")

        assertEquals(true, result)
    }

    @Test
    fun `broadcastTransaction throws on non-zero non-mempool-cache code`() {
        val body =
            """
            {
              "tx_response": {
                "txhash": "BAADF00D",
                "code": 5
              }
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.OK, body)

        val ex =
            assertThrows(CosmosBroadcastException::class.java) {
                runBlocking { api.broadcastTransaction(tx = "{}") }
            }
        assertEquals(5, ex.code)
        assertEquals("BAADF00D", ex.txHash)
    }

    @Test
    fun `broadcastTransaction surfaces sequence mismatch via SEQUENCE_MISMATCH_MARKER`() {
        val body =
            """
            {
              "tx_response": {
                "txhash": "SEQ32",
                "code": 32,
                "codespace": "sdk",
                "raw_log": "account sequence mismatch, expected 7, got 6: incorrect account sequence"
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
        assertEquals("SEQ32", ex.txHash)
        // Message must start with the sequence-mismatch marker so KeysignErrorScreen routes to
        // the localized sequence-mismatch copy rather than the generic rejected branch.
        assertEquals(
            true,
            ex.message?.startsWith(CosmosBroadcastException.SEQUENCE_MISMATCH_MARKER) == true,
        )
    }

    /**
     * Builds a [ThorChainApi] whose transport routes the RUJI GraphQL stake POST and the Cosmos
     * bank balances GET to distinct bodies, so `getRujiStakeBalance` can be exercised end-to-end.
     */
    private fun newRujiApi(
        stakeBody: String,
        balancesBody: String,
        balancesStatus: HttpStatusCode = HttpStatusCode.OK,
    ): ThorChainApi {
        val client =
            HttpClient(
                MockEngine { request ->
                    if (request.url.encodedPath.contains("/balances/")) {
                        respond(balancesBody, balancesStatus, jsonHeaders)
                    } else {
                        respond(stakeBody, HttpStatusCode.OK, jsonHeaders)
                    }
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
        return ThorChainApiImpl(
            httpClient = client,
            thorChainSwapQuoteResponseJsonSerializer =
                mockk<ThorChainSwapQuoteResponseJsonSerializer>(),
            json = json,
        )
    }

    private fun rujiStakeBody(bondedAmount: String): String =
        """
        {
          "data": {
            "node": {
              "merge": null,
              "stakingV2": [
                {
                  "account": "thor1abc",
                  "bonded": { "amount": "$bondedAmount", "asset": { "metadata": { "symbol": "RUJI" } } },
                  "pendingRevenue": { "amount": "500", "asset": { "metadata": { "symbol": "USDC" } } },
                  "pool": { "mergeAsset": null, "summary": { "apr": { "value": "0.12" } } }
                }
              ]
            }
          }
        }
        """
            .trimIndent()

    /**
     * Mirrors the real Rujira response for a vault that stakes both TCY and RUJI: stakingV2 lists
     * the TCY position first (with bonded 0), then the RUJI position. `getRujiStakeBalance` must
     * pick the RUJI entry, not the first one.
     */
    private fun rujiStakeBodyWithTcyFirst(bondedRuji: String): String =
        """
        {
          "data": {
            "node": {
              "merge": null,
              "stakingV2": [
                {
                  "account": "thor1abc",
                  "bonded": { "amount": "0", "asset": { "metadata": { "symbol": "TCY" } } },
                  "pendingRevenue": { "amount": "999", "asset": { "metadata": { "symbol": "USDC" } } },
                  "pool": { "mergeAsset": null, "summary": { "apr": { "value": "0.05" } } }
                },
                {
                  "account": "thor1abc",
                  "bonded": { "amount": "$bondedRuji", "asset": { "metadata": { "symbol": "RUJI" } } },
                  "pendingRevenue": { "amount": "500", "asset": { "metadata": { "symbol": "USDC" } } },
                  "pool": { "mergeAsset": null, "summary": { "apr": { "value": "0.12" } } }
                }
              ]
            }
          }
        }
        """
            .trimIndent()

    @Test
    fun `getRujiStakeBalance prefers on-chain receipt balance over bonded when receipts are held`() =
        runBlocking {
            // bonded reads 0 (the reported bug), but the vault holds sRUJI receipts on-chain.
            val api =
                newRujiApi(
                    stakeBody = rujiStakeBody(bondedAmount = "0"),
                    balancesBody =
                        """{"balances":[{"denom":"x/staking-x/ruji","amount":"12345"}]}""",
                )

            val result = api.getRujiStakeBalance("thor1abc")

            assertEquals(BigInteger("12345"), result.stakeAmount)
            assertEquals("RUJI", result.stakeTicker)
        }

    @Test
    fun `getRujiStakeBalance keeps a successful zero receipt as zero instead of using bonded`() =
        runBlocking {
            // Balances read succeeds but holds no receipt (vault fully unstaked). Parity with
            // vultisig-windows #4337: a successful zero stays zero; do NOT fall back to a stale
            // non-zero bonded amount.
            val api =
                newRujiApi(
                    stakeBody = rujiStakeBody(bondedAmount = "777"),
                    balancesBody = """{"balances":[]}""",
                )

            val result = api.getRujiStakeBalance("thor1abc")

            assertEquals(BigInteger.ZERO, result.stakeAmount)
        }

    @Test
    fun `getRujiStakeBalance falls back to bonded only when the balances read fails`() =
        runBlocking {
            val api =
                newRujiApi(
                    stakeBody = rujiStakeBody(bondedAmount = "42"),
                    balancesBody = "boom",
                    balancesStatus = HttpStatusCode.InternalServerError,
                )

            val result = api.getRujiStakeBalance("thor1abc")

            assertEquals(BigInteger("42"), result.stakeAmount)
        }

    @Test
    fun `getRujiStakeBalance falls back to bonded when the receipt amount is unparseable`() =
        runBlocking {
            // The receipt entry exists but carries a garbage amount: this is a read failure, not a
            // genuine zero, so we fall back to the GraphQL bonded amount rather than reporting
            // zero.
            val api =
                newRujiApi(
                    stakeBody = rujiStakeBody(bondedAmount = "42"),
                    balancesBody =
                        """{"balances":[{"denom":"x/staking-x/ruji","amount":"not-a-number"}]}""",
                )

            val result = api.getRujiStakeBalance("thor1abc")

            assertEquals(BigInteger("42"), result.stakeAmount)
        }

    @Test
    fun `getRujiStakeBalance falls back to the RUJI position not TCY when the balances read fails`() =
        runBlocking {
            // Real-world shape: TCY position (bonded 0) precedes RUJI in stakingV2. When the
            // balance read fails and we fall back to bonded, it must read RUJI's amount, not TCY's
            // leading 0.
            val api =
                newRujiApi(
                    stakeBody = rujiStakeBodyWithTcyFirst(bondedRuji = "7875733"),
                    balancesBody = "boom",
                    balancesStatus = HttpStatusCode.InternalServerError,
                )

            val result = api.getRujiStakeBalance("thor1abc")

            assertEquals(BigInteger("7875733"), result.stakeAmount)
            assertEquals("RUJI", result.stakeTicker)
        }

    @Test
    fun `getRujiStakeBalance prefers receipt over RUJI bonded even with TCY listed first`() =
        runBlocking {
            val api =
                newRujiApi(
                    stakeBody = rujiStakeBodyWithTcyFirst(bondedRuji = "7875733"),
                    balancesBody =
                        """{"balances":[{"denom":"x/staking-x/ruji","amount":"41952462"}]}""",
                )

            val result = api.getRujiStakeBalance("thor1abc")

            assertEquals(BigInteger("41952462"), result.stakeAmount)
            assertEquals("RUJI", result.stakeTicker)
        }

    @Test
    fun `broadcastTransaction throws with code -1 and preserves rawBody when tx_response is null`() {
        // Node returned a well-formed envelope with no tx_response (e.g. transport-level reject).
        val body = "{}"
        val api = newApi(HttpStatusCode.OK, body)

        val ex =
            assertThrows(CosmosBroadcastException::class.java) {
                runBlocking { api.broadcastTransaction(tx = "{}") }
            }
        assertEquals(-1, ex.code)
        assertEquals(null, ex.codespace)
        assertEquals(body, ex.rawLog)
        assertEquals(null, ex.txHash)
    }
}
