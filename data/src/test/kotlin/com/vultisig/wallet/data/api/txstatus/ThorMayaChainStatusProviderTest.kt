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
 * - `type == "failed"` with a non-blank outbound txID → [TransactionResult.Refunded] (refund
 *   observed).
 * - `type == "failed"` with no outbound tx yet → [TransactionResult.Failed] (network rejected the
 *   action; refund is a separate outbound that hasn't been observed).
 * - `status == "success"` (non-refund/failed) → [TransactionResult.Confirmed].
 * - empty actions array → native node `cosmos/tx/v1beta1/txs/{hash}` fallback (Midgard doesn't
 *   index plain transfers or rejected deposits): code 0 → Confirmed, non-zero → Failed, 404 →
 *   Pending.
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
    fun `refund with blank reason falls back to default reason`() = runTest {
        val body =
            """
            { "actions": [ { "type": "refund", "status": "success",
              "metadata": { "refund": { "reason": "   " } } } ] }
            """
                .trimIndent()
        val client = MockHttpClient.respondingWith(HttpStatusCode.OK, body)
        val provider = ThorMayaChainStatusProvider(client)

        val result = provider.checkStatus("hash", Chain.ThorChain)

        result shouldBe TransactionResult.Refunded("Transaction refunded")
    }

    @Test
    fun `failed action without observed outbound refund returns Failed`() = runTest {
        // Real Midgard payload shape for a paused-pool LP add-liquidity at the moment of
        // status check: network accepts the tx (status=success) but cannot execute it
        // (type=failed) and `out` is still empty — no outbound refund has been observed yet.
        val body =
            """
            {
              "actions": [
                {
                  "type": "failed",
                  "status": "success",
                  "out": [],
                  "metadata": {
                    "failed": {
                      "code": "99",
                      "memo": "+:ETH.USDT-…:0x…",
                      "reason": "failed to execute message; message index: 0: unable to add liquidity, deposits are paused for asset (ETH.USDT-…): internal error"
                    }
                  }
                }
              ]
            }
            """
                .trimIndent()
        val client = MockHttpClient.respondingWith(HttpStatusCode.OK, body)
        val provider = ThorMayaChainStatusProvider(client)

        val result = provider.checkStatus("hash", Chain.ThorChain)

        result shouldBe
            TransactionResult.Failed(
                "failed to execute message; message index: 0: unable to add liquidity, " +
                    "deposits are paused for asset (ETH.USDT-…): internal error"
            )
    }

    @Test
    fun `failed action with observed outbound refund returns Refunded`() = runTest {
        // Once Midgard has observed the network's refund outbound, the action's `out` array
        // carries the refund txID — at that point we promote the result to Refunded.
        val body =
            """
            {
              "actions": [
                {
                  "type": "failed",
                  "status": "success",
                  "out": [ { "txID": "REFUND_TX_ID_ABC" } ],
                  "metadata": {
                    "failed": { "reason": "deposits are paused" }
                  }
                }
              ]
            }
            """
                .trimIndent()
        val client = MockHttpClient.respondingWith(HttpStatusCode.OK, body)
        val provider = ThorMayaChainStatusProvider(client)

        val result = provider.checkStatus("hash", Chain.ThorChain)

        result shouldBe TransactionResult.Refunded("deposits are paused")
    }

    @Test
    fun `failed action with blank outbound txID stays Failed`() = runTest {
        // An `out` entry with a blank txID doesn't count as an observed refund.
        val body =
            """
            {
              "actions": [
                {
                  "type": "failed",
                  "status": "success",
                  "out": [ { "txID": "" } ],
                  "metadata": { "failed": { "reason": "deposits are paused" } }
                }
              ]
            }
            """
                .trimIndent()
        val client = MockHttpClient.respondingWith(HttpStatusCode.OK, body)
        val provider = ThorMayaChainStatusProvider(client)

        val result = provider.checkStatus("hash", Chain.ThorChain)

        result shouldBe TransactionResult.Failed("deposits are paused")
    }

    @Test
    fun `failed action without metadata falls back to default failed reason`() = runTest {
        val body =
            """
            { "actions": [ { "type": "failed", "status": "success" } ] }
            """
                .trimIndent()
        val client = MockHttpClient.respondingWith(HttpStatusCode.OK, body)
        val provider = ThorMayaChainStatusProvider(client)

        val result = provider.checkStatus("hash", Chain.ThorChain)

        result shouldBe TransactionResult.Failed("Transaction failed")
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
    fun `no midgard action falls back to native node and maps failed code to Failed`() = runTest {
        // The reported bug: a native THORChain tx (secured-asset send / rejected wasm deposit) is
        // never indexed by Midgard, so `/v2/actions` is empty. Previously this stayed Pending
        // forever; now we resolve it via the node's cosmos/tx endpoint, which reports the failure.
        val nativeFailed =
            """
            {
              "tx_response": {
                "code": 11,
                "codespace": "wasm",
                "raw_log": "Zeroamount: execute wasm contract failed"
              }
            }
            """
                .trimIndent()
        val client =
            MockHttpClient.respondingWithSequence(
                HttpStatusCode.OK to """{ "actions": [] }""",
                HttpStatusCode.OK to nativeFailed,
            )
        val provider = ThorMayaChainStatusProvider(client)

        val result = provider.checkStatus("hash", Chain.ThorChain)

        result shouldBe TransactionResult.Failed("Zeroamount: execute wasm contract failed")
    }

    @Test
    fun `no midgard action falls back to native node and maps code 0 to Confirmed`() = runTest {
        val nativeSuccess =
            """
            { "tx_response": { "code": 0, "codespace": "", "raw_log": "" } }
            """
                .trimIndent()
        val client =
            MockHttpClient.respondingWithSequence(
                HttpStatusCode.OK to """{ "actions": [] }""",
                HttpStatusCode.OK to nativeSuccess,
            )
        val provider = ThorMayaChainStatusProvider(client)

        val result = provider.checkStatus("hash", Chain.MayaChain)

        result shouldBe TransactionResult.Confirmed
    }

    @Test
    fun `no midgard action and native node 404 stays Pending`() = runTest {
        // Node returns 404 until the tx is committed in a block — keep polling.
        val client =
            MockHttpClient.respondingWithSequence(
                HttpStatusCode.OK to """{ "actions": [] }""",
                HttpStatusCode.NotFound to "",
            )
        val provider = ThorMayaChainStatusProvider(client)

        val result = provider.checkStatus("hash", Chain.ThorChain)

        result shouldBe TransactionResult.Pending
    }

    @Test
    fun `failed native code with blank raw_log falls back to default failed reason`() = runTest {
        val nativeFailed =
            """
            { "tx_response": { "code": 5, "codespace": "sdk", "raw_log": "" } }
            """
                .trimIndent()
        val client =
            MockHttpClient.respondingWithSequence(
                HttpStatusCode.OK to """{ "actions": [] }""",
                HttpStatusCode.OK to nativeFailed,
            )
        val provider = ThorMayaChainStatusProvider(client)

        val result = provider.checkStatus("hash", Chain.ThorChain)

        result shouldBe TransactionResult.Failed("Transaction failed")
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
