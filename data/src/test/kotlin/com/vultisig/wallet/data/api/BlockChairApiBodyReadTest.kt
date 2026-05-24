package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.BigIntegerSerializerImpl
import com.vultisig.wallet.data.utils.UTXOStatusResponseSerializerImpl
import io.ktor.http.HttpStatusCode
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Characterization tests for [BlockChairApiImp] methods that call `response.body<T>()`. They pin
 * the success-path (HTTP 200) behavior so it is preserved when call sites are migrated to
 * `bodyOrThrow<T>()`.
 *
 * Methods covered:
 * - [BlockChairApi.getAddressInfo] — `body<BlockChairInfoJson>()`
 * - [BlockChairApi.getBlockChairStats] — `body<SuggestedTransactionFeeDataJson>().data.value`
 * - [BlockChairApi.broadcastTransaction] (non-Bitcoin) —
 *   `body<TransactionHashDataJson>().data.value`
 *
 * [BlockChairApi.getTsStatus] does NOT contain a `body<...>()` call — it uses `bodyAsText()` fed
 * into a custom serializer — and is therefore excluded per the skip rule.
 * [BlockChairApiImp.broadcastTransactionMempool] (Bitcoin path) uses `bodyAsText()` only; the
 * `body<...>()` call is exclusively on the non-Bitcoin branch.
 */
class BlockChairApiBodyReadTest {

    // SuggestedTransactionFeeDataJson.data.value is @Contextual BigInteger, so
    // we register BigIntegerSerializerImpl contextually in the Json used to build
    // the production-shaped HttpClient as well as in the Json passed to BlockChairApiImp.
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        serializersModule = SerializersModule { contextual(BigIntegerSerializerImpl()) }
    }

    private fun newApi(body: String): BlockChairApiImp =
        BlockChairApiImp(
            json = json,
            httpClient = MockHttpClient.respondingWith(HttpStatusCode.OK, body, json),
            utxoStatusResponseSerializer = UTXOStatusResponseSerializerImpl(json),
        )

    // -------------------------------------------------------------------------
    // getAddressInfo — body<BlockChairInfoJson>()
    // -------------------------------------------------------------------------

    @Test
    fun `getAddressInfo returns BlockChairInfo for the requested address`() = runTest {
        val address = "LVntExdyM3yaVcFa4QHrkqmEKMBpR5bepZ"
        val body =
            """
            {
              "data": {
                "$address": {
                  "address": {
                    "balance": 100000,
                    "unspent_output_count": 3
                  },
                  "utxo": [
                    {
                      "transaction_hash": "tx1",
                      "index": 0,
                      "value": 50000,
                      "block_id": 800000
                    }
                  ]
                }
              }
            }
            """
                .trimIndent()
        val api = newApi(body)

        val result = api.getAddressInfo(chain = Chain.Litecoin, address = address)

        assertEquals(100_000L, result?.address?.balance)
        assertEquals(3, result?.address?.unspentOutputCount)
        assertEquals(1, result?.utxos?.size)
        assertEquals("tx1", result?.utxos?.first()?.transactionHash)
    }

    @Test
    fun `getAddressInfo returns null when address key is absent from response data`() = runTest {
        // Server returns a data map that does not contain the requested address.
        val body = """{ "data": {} }"""
        val api = newApi(body)

        val result = api.getAddressInfo(chain = Chain.Litecoin, address = "missingAddress")

        assertNull(result)
    }

    // -------------------------------------------------------------------------
    // getBlockChairStats — body<SuggestedTransactionFeeDataJson>().data.value  (BigInteger)
    // -------------------------------------------------------------------------

    @Test
    fun `getBlockChairStats returns suggested fee as BigInteger`() = runTest {
        val body =
            """
            {
              "data": {
                "suggested_transaction_fee_per_byte_sat": 42
              }
            }
            """
                .trimIndent()
        val api = newApi(body)

        val result = api.getBlockChairStats(chain = Chain.Litecoin)

        assertEquals(BigInteger.valueOf(42L), result)
    }

    // -------------------------------------------------------------------------
    // broadcastTransaction (non-Bitcoin) — body<TransactionHashDataJson>().data.value
    // Chain.Bitcoin is routed to broadcastTransactionMempool (bodyAsText only),
    // so we use Chain.Litecoin to exercise the body<TransactionHashDataJson>() branch.
    // -------------------------------------------------------------------------

    @Test
    fun `broadcastTransaction returns transaction hash for non-Bitcoin chain`() = runTest {
        val body =
            """
            {
              "data": {
                "transaction_hash": "deadbeef1234"
              }
            }
            """
                .trimIndent()
        val api = newApi(body)

        val result = api.broadcastTransaction(chain = Chain.Litecoin, signedTransaction = "0102")

        assertEquals("deadbeef1234", result)
    }
}
