package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Verifies the Midgard-driven status mapping for THORChain/MayaChain inbound transactions:
 * - `type == "refund"` → [TransactionResult.Refunded] carrying the human-readable reason.
 * - `status == "success"` (non-refund) → [TransactionResult.Confirmed].
 * - empty actions array → [TransactionResult.Pending] (indexer lag).
 * - network/parse failure → [TransactionResult.Pending] (so polling keeps running).
 */
class ThorMayaChainStatusProviderTest {

    @Test
    fun `refund action returns Refunded with reason from metadata`() = runTest {
        // Real Midgard payload shape for tx 9D305...74D (paused-pool LP add-liquidity refund).
        val body =
            """
            {
              "actions": [
                {
                  "type": "refund",
                  "status": "success",
                  "metadata": {
                    "refund": {
                      "reason": "Unable to add liquidity, deposits are paused for asset (eth.usdt-0xdac17f958d2ee523a2206206994597c13d831ec7): internal error"
                    }
                  }
                }
              ]
            }
            """
                .trimIndent()
        val client = MockHttpClient.respondingWith(HttpStatusCode.OK, body)
        val provider = ThorMayaChainStatusProvider(client)

        val result = provider.checkStatus("9D305...74D", Chain.ThorChain)

        result shouldBe
            TransactionResult.Refunded(
                "Unable to add liquidity, deposits are paused for asset " +
                    "(eth.usdt-0xdac17f958d2ee523a2206206994597c13d831ec7): internal error"
            )
    }

    @Test
    fun `refund without metadata falls back to default reason`() = runTest {
        val body =
            """
            { "actions": [ { "type": "refund", "status": "success" } ] }
            """
                .trimIndent()
        val client = MockHttpClient.respondingWith(HttpStatusCode.OK, body)
        val provider = ThorMayaChainStatusProvider(client)

        val result = provider.checkStatus("hash", Chain.ThorChain)

        result shouldBe TransactionResult.Refunded("Transaction refunded")
    }

    @Test
    fun `successful swap returns Confirmed`() = runTest {
        val body =
            """
            { "actions": [ { "type": "swap", "status": "success" } ] }
            """
                .trimIndent()
        val client = MockHttpClient.respondingWith(HttpStatusCode.OK, body)
        val provider = ThorMayaChainStatusProvider(client)

        val result = provider.checkStatus("hash", Chain.ThorChain)

        result shouldBe TransactionResult.Confirmed
    }

    @Test
    fun `successful addLiquidity returns Confirmed`() = runTest {
        val body =
            """
            { "actions": [ { "type": "addLiquidity", "status": "success" } ] }
            """
                .trimIndent()
        val client = MockHttpClient.respondingWith(HttpStatusCode.OK, body)
        val provider = ThorMayaChainStatusProvider(client)

        val result = provider.checkStatus("hash", Chain.ThorChain)

        result shouldBe TransactionResult.Confirmed
    }

    @Test
    fun `pending action returns Pending`() = runTest {
        val body =
            """
            { "actions": [ { "type": "swap", "status": "pending" } ] }
            """
                .trimIndent()
        val client = MockHttpClient.respondingWith(HttpStatusCode.OK, body)
        val provider = ThorMayaChainStatusProvider(client)

        val result = provider.checkStatus("hash", Chain.ThorChain)

        result shouldBe TransactionResult.Pending
    }

    @Test
    fun `empty actions returns Pending`() = runTest {
        val client = MockHttpClient.respondingWith(HttpStatusCode.OK, """{ "actions": [] }""")
        val provider = ThorMayaChainStatusProvider(client)

        val result = provider.checkStatus("hash", Chain.ThorChain)

        result shouldBe TransactionResult.Pending
    }

    @Test
    fun `mayachain refund maps to Refunded`() = runTest {
        val body =
            """
            { "actions": [ { "type": "refund", "status": "success",
              "metadata": { "refund": { "reason": "Pool halted" } } } ] }
            """
                .trimIndent()
        val client = MockHttpClient.respondingWith(HttpStatusCode.OK, body)
        val provider = ThorMayaChainStatusProvider(client)

        val result = provider.checkStatus("hash", Chain.MayaChain)

        result shouldBe TransactionResult.Refunded("Pool halted")
    }

    @Test
    fun `unknown chain returns Failed`() = runTest {
        val client = MockHttpClient.respondingWith(HttpStatusCode.OK, """{ "actions": [] }""")
        val provider = ThorMayaChainStatusProvider(client)

        val result = provider.checkStatus("hash", Chain.Ethereum)

        result shouldBe TransactionResult.Failed("Unknown chain")
    }

    @Test
    fun `network failure returns Pending so polling continues`() = runTest {
        val client = MockHttpClient.throwingIOException(java.io.IOException("offline"))
        val provider = ThorMayaChainStatusProvider(client)

        val result = provider.checkStatus("hash", Chain.ThorChain)

        result shouldBe TransactionResult.Pending
    }

    @Test
    fun `malformed json returns Pending`() = runTest {
        val client = MockHttpClient.respondingWith(HttpStatusCode.OK, "not-json")
        val provider = ThorMayaChainStatusProvider(client)

        val result = provider.checkStatus("hash", Chain.ThorChain)

        result shouldBe TransactionResult.Pending
    }
}
