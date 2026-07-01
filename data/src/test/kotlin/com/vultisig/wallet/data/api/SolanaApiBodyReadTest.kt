package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.BigIntegerSerializerImpl
import com.vultisig.wallet.data.utils.SplTokenResponseJsonSerializerImpl
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/**
 * Characterization tests for every `.body<...>()` call in [SolanaApiImp], including
 * [SolanaApiImp.getJupiterTokens]. Each test uses a 200 OK mock response and asserts the exact
 * value extracted by the method, pinning the success-path behavior so it survives the `body<T>()` →
 * `bodyOrThrow<T>()` migration.
 */
class SolanaApiBodyReadTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        serializersModule = SerializersModule { contextual(BigIntegerSerializerImpl()) }
    }

    private fun newApi(body: String): SolanaApi =
        SolanaApiImp(
            json = json,
            httpClient = MockHttpClient.respondingWith(HttpStatusCode.OK, body, json),
            splTokenSerializer = SplTokenResponseJsonSerializerImpl(json),
        )

    // ── getBalance ──────────────────────────────────────────────────────────────

    @Test
    fun `getBalance returns value from SolanaBalanceJson result`() = runTest {
        val body =
            """
            {
              "result": { "value": 123456789 },
              "error": null
            }
            """
                .trimIndent()
        val api = newApi(body)

        val result = api.getBalance("SomeAddress1111111")

        assertEquals(123456789.toBigInteger(), result)
    }

    // ── getRecentBlockHash ───────────────────────────────────────────────────────

    @Test
    fun `getRecentBlockHash returns blockhash string from RecentBlockHashResponseJson`() = runTest {
        val body =
            """
            {
              "result": {
                "value": {
                  "blockhash": "AbCdEfGhIjKlMnOpQrStUvWxYz12345678901234567"
                }
              }
            }
            """
                .trimIndent()
        val api = newApi(body)

        val result = api.getRecentBlockHash()

        assertEquals("AbCdEfGhIjKlMnOpQrStUvWxYz12345678901234567", result)
    }

    // ── broadcastTransaction ────────────────────────────────────────────────────

    @Test
    fun `broadcastTransaction returns result string from BroadcastTransactionRespJson`() = runTest {
        val body =
            """
            {
              "result": "5xFakeTransactionHashABCDE1234",
              "error": null
            }
            """
                .trimIndent()
        val api = newApi(body)

        val result = api.broadcastTransaction("fakeTxBase64Encoded")

        assertEquals("5xFakeTransactionHashABCDE1234", result)
    }

    @Test
    fun `broadcastTransaction sends preflightCommitment confirmed to match the blockhash commitment`() =
        runTest {
            val capture = MockHttpClient.RequestCapture()
            val api =
                SolanaApiImp(
                    json = json,
                    httpClient =
                        MockHttpClient.capturingRequest(
                            HttpStatusCode.OK,
                            """{ "result": "sig", "error": null }""",
                            capture,
                            json,
                        ),
                    splTokenSerializer = SplTokenResponseJsonSerializerImpl(json),
                )

            api.broadcastTransaction("sometx")

            assertEquals(true, capture.lastBody.contains("\"preflightCommitment\":\"confirmed\""))
        }

    @Test
    fun `broadcastTransaction surfaces an expired blockhash as SolanaBlockhashExpiredException`() =
        runTest {
            val body =
                """
                {
                  "error": { "code": -32002, "message": "Transaction simulation failed: Block height exceeded" },
                  "result": null
                }
                """
                    .trimIndent()
            val api = newApi(body)

            val error = runCatching { api.broadcastTransaction("tx") }.exceptionOrNull()

            assertInstanceOf(SolanaBlockhashExpiredException::class.java, error)
        }

    @Test
    fun `broadcastTransaction rethrows a non-recoverable rpc error`() = runTest {
        val body =
            """
            {
              "error": { "code": -32002, "message": "Transaction simulation failed: insufficient funds for rent" },
              "result": null
            }
            """
                .trimIndent()
        val api = newApi(body)

        val error = runCatching { api.broadcastTransaction("tx") }.exceptionOrNull()

        assertInstanceOf(IllegalStateException::class.java, error)
    }

    // ── getSPLTokensInfo2 ───────────────────────────────────────────────────────
    // Uses .body<List<SplTokenInfo>>() for each token in parallel; respondingWith returns the same
    // body for every request.

    @Test
    fun `getSPLTokensInfo2 returns first SplTokenInfo element per token`() = runTest {
        val body =
            """
            [
              {
                "id": "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                "name": "USD Coin",
                "symbol": "USDC",
                "decimals": 6,
                "icon": null,
                "extensions": null
              }
            ]
            """
                .trimIndent()
        val api = newApi(body)

        val result = api.getSPLTokensInfo2(listOf("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"))

        assertEquals(1, result.size)
        assertEquals("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", result[0].address)
        assertEquals("USDC", result[0].symbol)
        assertEquals(6, result[0].decimals)
    }

    // ── getSPLTokens ─────────────────────────────────────────────────────────────
    // Makes two parallel requests; respondingWith returns the same SplResponseJson for both,
    // so the result is the combined list (two copies of the single account).

    @Test
    fun `getSPLTokens flattens accounts from both parallel SplResponseJson responses`() = runTest {
        val body =
            """
            {
              "result": {
                "value": [
                  {
                    "account": {
                      "data": {
                        "parsed": {
                          "info": {
                            "mint": "MintAddressXYZ",
                            "tokenAmount": { "amount": "1000" }
                          }
                        }
                      }
                    }
                  }
                ]
              },
              "error": null
            }
            """
                .trimIndent()
        val api = newApi(body)

        val result = api.getSPLTokens("WalletAddress111")

        // Two requests each return one account → combined list has two elements
        val nonNullResult = requireNotNull(result)
        assertEquals(2, nonNullResult.size)
        assertEquals("MintAddressXYZ", nonNullResult[0].account.data.parsed.info.mint)
    }

    // ── getJupiterTokens ─────────────────────────────────────────────────────────

    @Test
    fun `getJupiterTokens returns list of JupiterTokenResponseJson`() = runTest {
        val body =
            """
            [
              {
                "id": "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                "symbol": "USDC",
                "decimals": 6,
                "icon": null,
                "extensions": null
              }
            ]
            """
                .trimIndent()
        val api = newApi(body)

        val result = api.getJupiterTokens()

        assertEquals(1, result.size)
        assertEquals("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", result[0].contractAddress)
        assertEquals("USDC", result[0].ticker)
        assertEquals(6, result[0].decimals)
    }

    // ── getSPLTokenBalance ───────────────────────────────────────────────────────

    @Test
    fun `getSPLTokenBalance returns token amount from SplAmountRpcResponseJson`() = runTest {
        val body =
            """
            {
              "result": {
                "value": [
                  {
                    "pubkey": "PubKeyABC",
                    "account": {
                      "data": {
                        "parsed": {
                          "info": {
                            "tokenAmount": { "amount": "999888777" }
                          }
                        }
                      },
                      "owner": "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
                    }
                  }
                ]
              },
              "error": null
            }
            """
                .trimIndent()
        val api = newApi(body)

        val result =
            api.getSPLTokenBalance(
                walletAddress = "WalletAddress111",
                coinAddress = "CoinAddressABC",
            )

        assertEquals("999888777", result)
    }
}
