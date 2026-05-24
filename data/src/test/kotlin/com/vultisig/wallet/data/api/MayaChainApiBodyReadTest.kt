package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.ThorChainSwapQuoteResponseJsonSerializer
import io.ktor.http.HttpStatusCode
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Characterization tests that pin the SUCCESS-path (HTTP 200) behavior of every method in
 * [MayaChainApiImp] that uses `.body<T>()` to deserialize the response.
 *
 * Skipped methods:
 * - `getSwapQuotes` — uses `body<String>()` fed into a custom
 *   [ThorChainSwapQuoteResponseJsonSerializer]; excluded per task rules.
 * - `broadcastTransaction` — already covered by [MayaChainApiImplTest].
 */
class MayaChainApiBodyReadTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun newApi(body: String): MayaChainApi =
        MayaChainApiImp(
            httpClient = MockHttpClient.respondingWith(HttpStatusCode.OK, body),
            thorChainApi = mockk<ThorChainApi>(),
            thorChainSwapQuoteResponseJsonSerializer =
                mockk<ThorChainSwapQuoteResponseJsonSerializer>(),
            json = json,
        )

    // -------------------------------------------------------------------------
    // getBalance
    // -------------------------------------------------------------------------

    @Test
    fun `getBalance returns list of balances from CosmosBalanceResponse`() = runBlocking {
        val body =
            """
            {
              "balances": [
                { "denom": "cacao", "amount": "1000000" },
                { "denom": "maya", "amount": "500" }
              ]
            }
            """
                .trimIndent()
        val result = newApi(body).getBalance("maya1abc")

        assertEquals(2, result.size)
        assertEquals("cacao", result[0].denom)
        assertEquals("1000000", result[0].amount)
        assertEquals("maya", result[1].denom)
        assertEquals("500", result[1].amount)
    }

    @Test
    fun `getBalance returns empty list when balances field is null`() = runBlocking {
        val body = """{ "balances": null }""".trimIndent()
        val result = newApi(body).getBalance("maya1abc")
        assertEquals(emptyList<Any>(), result)
    }

    // -------------------------------------------------------------------------
    // getUnStakeCacaoBalance
    // -------------------------------------------------------------------------

    @Test
    fun `getUnStakeCacaoBalance returns cacaoDeposit from first element`() = runBlocking {
        val body =
            """
            [
              { "cacaoDeposit": "9876543" },
              { "cacaoDeposit": "111" }
            ]
            """
                .trimIndent()
        val result = newApi(body).getUnStakeCacaoBalance("maya1abc")
        assertEquals("9876543", result)
    }

    @Test
    fun `getUnStakeCacaoBalance returns null when list is empty`() = runBlocking {
        val result = newApi("[]").getUnStakeCacaoBalance("maya1abc")
        assertNull(result)
    }

    // -------------------------------------------------------------------------
    // getAccountNumber
    // -------------------------------------------------------------------------

    @Test
    fun `getAccountNumber returns THORChainAccountValue from nested result-value`() = runBlocking {
        val body =
            """
            {
              "result": {
                "value": {
                  "address": "maya1xyz",
                  "account_number": "42",
                  "sequence": "7"
                }
              }
            }
            """
                .trimIndent()
        val result = newApi(body).getAccountNumber("maya1xyz")

        assertEquals("maya1xyz", result.address)
        assertEquals("42", result.accountNumber)
        assertEquals("7", result.sequence)
    }

    // -------------------------------------------------------------------------
    // getLatestBlock
    // -------------------------------------------------------------------------

    @Test
    fun `getLatestBlock returns MayaLatestBlockInfoResponse with correct height`() = runBlocking {
        val body =
            """
            {
              "block_id": {
                "hash": "ABCD1234",
                "parts": { "total": 1, "hash": "PART_HASH" }
              },
              "block": {
                "header": {
                  "version": { "block": "11" },
                  "chain_id": "mayachain-mainnet-v1",
                  "height": "9999",
                  "time": "2024-01-01T00:00:00Z",
                  "last_block_id": {
                    "hash": "PREV_HASH",
                    "parts": { "total": 1, "hash": "PREV_PART" }
                  },
                  "last_commit_hash": "LC",
                  "data_hash": "DH",
                  "validators_hash": "VH",
                  "next_validators_hash": "NVH",
                  "consensus_hash": "CH",
                  "app_hash": "AH",
                  "last_results_hash": "LRH",
                  "evidence_hash": "EH",
                  "proposer_address": "PROP"
                },
                "data": { "txs": null }
              }
            }
            """
                .trimIndent()
        val result = newApi(body).getLatestBlock()

        assertEquals("9999", result.block.header.height)
        assertEquals("mayachain-mainnet-v1", result.block.header.chainId)
        assertEquals("ABCD1234", result.blockId.hash)
    }

    // -------------------------------------------------------------------------
    // getCacaoProvider
    // -------------------------------------------------------------------------

    @Test
    fun `getCacaoProvider returns CacaoProviderResponse with all fields`() = runBlocking {
        val body =
            """
            {
              "cacao_address": "maya1provider",
              "units": "100000",
              "value": "200000",
              "pnl": "5000",
              "deposit_amount": "150000",
              "withdraw_amount": "50000",
              "last_deposit_height": 12345,
              "last_withdraw_height": 12400
            }
            """
                .trimIndent()
        val result = newApi(body).getCacaoProvider("maya1provider")

        assertEquals("maya1provider", result.cacaoAddress)
        assertEquals("100000", result.units)
        assertEquals("200000", result.value)
        assertEquals("5000", result.pnl)
        assertEquals("150000", result.depositAmount)
        assertEquals("50000", result.withdrawAmount)
        assertEquals(12345L, result.lastDepositHeight)
        assertEquals(12400L, result.lastWithdrawHeight)
    }

    // -------------------------------------------------------------------------
    // getMayaConstants
    // -------------------------------------------------------------------------

    @Test
    fun `getMayaConstants returns map of string to long`() = runBlocking {
        val body =
            """
            {
              "NATIVETRANSACTIONFEE": 2000000,
              "OUTBOUNDTRANSACTIONFEE": 1000000
            }
            """
                .trimIndent()
        val result = newApi(body).getMayaConstants()

        assertEquals(2, result.size)
        assertEquals(2_000_000L, result["NATIVETRANSACTIONFEE"])
        assertEquals(1_000_000L, result["OUTBOUNDTRANSACTIONFEE"])
    }
}
