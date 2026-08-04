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

    /**
     * The pool's APR object, defaulting to the shape the live endpoint returns: a 12-decimal bigint
     * scalar (`11623890337` = 1.16%) alongside its publication status.
     */
    private fun aprJson(value: String? = "11623890337", status: String? = "AVAILABLE"): String =
        listOfNotNull(value?.let { """"value": "$it"""" }, status?.let { """"status": "$it"""" })
            .joinToString(", ")

    private fun rujiStakeBody(
        bondedAmount: String,
        liquidSize: String? = "0",
        liquidShares: String? = "0",
        apr: String = aprJson(),
    ): String =
        """
        {
          "data": {
            "node": {
              "merge": null,
              "stakingV2": [
                {
                  "account": "thor1abc",
                  "bonded": { "amount": "$bondedAmount", "asset": { "metadata": { "symbol": "RUJI" } } },
                  ${liquidSize?.let { """"liquidSize": { "amount": "$it" },""" } ?: ""}
                  ${liquidShares?.let { """"liquidShares": { "amount": "$it" },""" } ?: ""}
                  "pendingRevenue": { "amount": "500", "asset": { "metadata": { "symbol": "USDC" } } },
                  "pool": { "mergeAsset": null, "summary": { "apr": { $apr } } }
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
                  "liquidSize": { "amount": "0" },
                  "liquidShares": { "amount": "0" },
                  "pendingRevenue": { "amount": "999", "asset": { "metadata": { "symbol": "USDC" } } },
                  "pool": { "mergeAsset": null, "summary": { "apr": { "value": "0.05" } } }
                },
                {
                  "account": "thor1abc",
                  "bonded": { "amount": "$bondedRuji", "asset": { "metadata": { "symbol": "RUJI" } } },
                  "liquidSize": { "amount": "0" },
                  "liquidShares": { "amount": "0" },
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
    fun `getRujiStakeBalance reports a bonded-only position instead of the receipt's zero`() =
        runBlocking {
            // The "Standard" pool holds no receipt token at all, so an sRUJI balance of zero is the
            // normal state for a bonded staker — it must not erase the bonded amount (#5419).
            val api =
                newRujiApi(
                    stakeBody = rujiStakeBody(bondedAmount = "7875733"),
                    balancesBody = """{"balances":[]}""",
                )

            val result = api.getRujiStakeBalance("thor1abc")

            assertEquals(BigInteger("7875733"), result.stakeAmount)
            assertEquals(BigInteger.ZERO, result.autoCompoundAmount)
            assertEquals("RUJI", result.stakeTicker)
        }

    @Test
    fun `getRujiStakeBalance values the auto-compounding position in RUJI not in receipt shares`() =
        runBlocking {
            // liquidSize is the receipt priced at the pool's share price, which sits above 1 and
            // rises: rendering the raw share count understates the position by the accrued yield.
            val api =
                newRujiApi(
                    stakeBody =
                        rujiStakeBody(
                            bondedAmount = "0",
                            liquidSize = "1406486651509",
                            liquidShares = "1385594365632",
                        ),
                    balancesBody = """{"balances":[]}""",
                )

            val result = api.getRujiStakeBalance("thor1abc")

            assertEquals(BigInteger.ZERO, result.stakeAmount)
            assertEquals(BigInteger("1406486651509"), result.autoCompoundAmount)
            assertEquals(BigInteger("1385594365632"), result.autoCompoundShares)
        }

    @Test
    fun `getRujiStakeBalance reports both positions when an account holds each`() = runBlocking {
        // Live shape of thor1qvmeavyusxyet7szr2azjzut7tamw4ycfg08ss: bonded and auto-compounding at
        // the same time. Neither may substitute for or suppress the other.
        val api =
            newRujiApi(
                stakeBody =
                    rujiStakeBody(
                        bondedAmount = "1638238990000",
                        liquidSize = "1406486651509",
                        liquidShares = "1385594365632",
                    ),
                balancesBody = """{"balances":[]}""",
            )

        val result = api.getRujiStakeBalance("thor1abc")

        assertEquals(BigInteger("1638238990000"), result.stakeAmount)
        assertEquals(BigInteger("1406486651509"), result.autoCompoundAmount)
    }

    @Test
    fun `getRujiStakeBalance falls back to the on-chain receipt for shares when liquidShares is absent`() =
        runBlocking {
            // liquidShares equals the on-chain receipt by construction, so the bank balance is a
            // safe stand-in when a partial response omits the field — without it the unbond, which
            // is funded in shares, would be silently unavailable.
            val api =
                newRujiApi(
                    stakeBody =
                        rujiStakeBody(
                            bondedAmount = "0",
                            liquidSize = "1406486651509",
                            liquidShares = null,
                        ),
                    balancesBody =
                        """{"balances":[{"denom":"x/staking-x/ruji","amount":"1385594365632"}]}""",
                )

            val result = api.getRujiStakeBalance("thor1abc")

            assertEquals(BigInteger("1385594365632"), result.autoCompoundShares)
        }

    @Test
    fun `getRujiStakeBalance reports unreadable shares as unknown without losing the amounts`() =
        runBlocking {
            // Both share sources unavailable on a live position. Reporting zero shares would read
            // downstream as "insufficient balance" and silently disable the unbond, so the count is
            // reported as unknown — but the share read is a separate bank query, so failing it must
            // not discard the two displayable amounts that did arrive.
            val api =
                newRujiApi(
                    stakeBody =
                        rujiStakeBody(
                            bondedAmount = "7875733",
                            liquidSize = "1406486651509",
                            liquidShares = null,
                        ),
                    balancesBody = "",
                    balancesStatus = HttpStatusCode.InternalServerError,
                )

            val result = api.getRujiStakeBalance("thor1abc")

            assertEquals(BigInteger("7875733"), result.stakeAmount)
            assertEquals(BigInteger("1406486651509"), result.autoCompoundAmount)
            assertNull(result.autoCompoundShares)
        }

    @Test
    fun `getRujiStakeBalance skips the receipt read when there is no auto-compounding position`() =
        runBlocking {
            // A bonded-only staker has no shares to size, so a failing bank read must not sink the
            // bonded amount — nor should the request be made at all.
            val api =
                newRujiApi(
                    stakeBody =
                        rujiStakeBody(
                            bondedAmount = "7875733",
                            liquidSize = "0",
                            liquidShares = null,
                        ),
                    balancesBody = "",
                    balancesStatus = HttpStatusCode.InternalServerError,
                )

            val result = api.getRujiStakeBalance("thor1abc")

            assertEquals(BigInteger("7875733"), result.stakeAmount)
            assertEquals(BigInteger.ZERO, result.autoCompoundShares)
        }

    @Test
    fun `getRujiStakeBalance throws rather than zeroing an amount a present position omits`() =
        runBlocking {
            // The Rujira schema marks these amounts non-null, so a position that reports one
            // without the other is a degraded response, not an empty position: zeroing it would
            // persist a false zero over a live bond and disable its Unstake.
            val api =
                newRujiApi(
                    stakeBody = rujiStakeBody(bondedAmount = "7875733", liquidSize = null),
                    balancesBody = """{"balances":[]}""",
                )

            assertThrows(IllegalStateException::class.java) {
                runBlocking { api.getRujiStakeBalance("thor1abc") }
            }
            Unit
        }

    @Test
    fun `getRujiStakeBalance throws rather than reporting an unparseable bonded amount as zero`() =
        runBlocking {
            // Coercing a malformed amount to zero is exactly the failure this issue is about: it
            // renders a funded account as empty. Failing closed lets the caller keep its cache.
            val api =
                newRujiApi(
                    stakeBody = rujiStakeBody(bondedAmount = "not-a-number"),
                    balancesBody = """{"balances":[]}""",
                )

            assertThrows(IllegalStateException::class.java) {
                runBlocking { api.getRujiStakeBalance("thor1abc") }
            }
            Unit
        }

    @Test
    fun `getRujiStakeBalance throws rather than reporting an unparseable liquidSize as zero`() =
        runBlocking {
            val api =
                newRujiApi(
                    stakeBody = rujiStakeBody(bondedAmount = "0", liquidSize = "garbage"),
                    balancesBody = """{"balances":[]}""",
                )

            assertThrows(IllegalStateException::class.java) {
                runBlocking { api.getRujiStakeBalance("thor1abc") }
            }
            Unit
        }

    @Test
    fun `getRujiStakeBalance reports zeros when the account holds no RUJI position at all`() =
        runBlocking {
            // An absent position is a genuine zero, not a partial response — it must not throw.
            val api =
                newRujiApi(
                    stakeBody = """{"data":{"node":{"merge":null,"stakingV2":[]}}}""",
                    balancesBody = """{"balances":[]}""",
                )

            val result = api.getRujiStakeBalance("thor1abc")

            assertEquals(BigInteger.ZERO, result.stakeAmount)
            assertEquals(BigInteger.ZERO, result.autoCompoundAmount)
        }

    @Test
    fun `getRujiStakeBalance reads the RUJI position not TCY when TCY is listed first`() =
        runBlocking {
            // Real-world shape: the TCY position (bonded 0) precedes RUJI in stakingV2, so taking
            // the first entry would report RUJI's amount, APR and rewards from an unrelated pool.
            val api =
                newRujiApi(
                    stakeBody = rujiStakeBodyWithTcyFirst(bondedRuji = "7875733"),
                    balancesBody = """{"balances":[]}""",
                )

            val result = api.getRujiStakeBalance("thor1abc")

            assertEquals(BigInteger("7875733"), result.stakeAmount)
            assertEquals("RUJI", result.stakeTicker)
        }

    @Test
    fun `getRujiStakeBalance scales the APR out of its 12-decimal bigint`() = runBlocking {
        // "11623890337" is 1.16%, not 1162389033.7%: the scalar carries 12 decimal places, so a
        // bare toDouble() would reach formatPercentage() already multiplied by 1e12 (#5498).
        val api =
            newRujiApi(
                stakeBody = rujiStakeBody(bondedAmount = "7875733", apr = aprJson("11623890337")),
                balancesBody = """{"balances":[]}""",
            )

        val result = api.getRujiStakeBalance("thor1abc")

        assertEquals(0.011623890337, result.apr!!, 1e-15)
    }

    @Test
    fun `getRujiStakeBalance publishes no APR while the pool is not AVAILABLE`() = runBlocking {
        // A SOON/NOT_APPLICABLE pool carries a placeholder rate that must not be rendered as if it
        // were live; null hides the row, where zero would claim the position earns nothing.
        val api =
            newRujiApi(
                stakeBody =
                    rujiStakeBody(
                        bondedAmount = "7875733",
                        apr = aprJson("11623890337", status = "SOON"),
                    ),
                balancesBody = """{"balances":[]}""",
            )

        val result = api.getRujiStakeBalance("thor1abc")

        assertNull(result.apr)
    }

    @Test
    fun `getRujiStakeBalance keeps the balances when the APR is missing or unreadable`() =
        runBlocking {
            // The rate decorates an otherwise complete card, so losing it must not fail the read.
            val api =
                newRujiApi(
                    stakeBody =
                        rujiStakeBody(
                            bondedAmount = "7875733",
                            apr = aprJson(value = "not-a-rate"),
                        ),
                    balancesBody = """{"balances":[]}""",
                )

            val result = api.getRujiStakeBalance("thor1abc")

            assertNull(result.apr)
            assertEquals(BigInteger("7875733"), result.stakeAmount)
        }

    @Test
    fun `getRujiStakeBalance reads the APR of a pool that omits its status`() = runBlocking {
        // The field is optional in the schema; absent means unqualified, not unavailable.
        val api =
            newRujiApi(
                stakeBody =
                    rujiStakeBody(
                        bondedAmount = "7875733",
                        apr = aprJson("11623890337", status = null),
                    ),
                balancesBody = """{"balances":[]}""",
            )

        val result = api.getRujiStakeBalance("thor1abc")

        assertEquals(0.011623890337, result.apr!!, 1e-15)
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
