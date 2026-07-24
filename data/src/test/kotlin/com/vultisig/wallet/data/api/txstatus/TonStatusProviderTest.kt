package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.chains.ton.TonActionPhaseJson
import com.vultisig.wallet.data.api.chains.ton.TonApi
import com.vultisig.wallet.data.api.chains.ton.TonComputePhaseJson
import com.vultisig.wallet.data.api.chains.ton.TonStatusResult
import com.vultisig.wallet.data.api.chains.ton.TonTransactionDescriptionJson
import com.vultisig.wallet.data.api.chains.ton.TransactionJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class TonStatusProviderTest {

    private val tonApi = mockk<TonApi>()
    private val provider = TonStatusProvider(tonApi)

    @Test
    fun `empty transactions returns NotFound`() = runTest {
        coEvery { tonApi.getTsStatus(any()) } returns TonStatusResult(transactions = emptyList())

        val result = provider.checkStatus("hash", Chain.Ton)

        assertEquals(TransactionResult.NotFound, result)
    }

    @Test
    fun `transaction with aborted true returns Failed`() = runTest {
        coEvery { tonApi.getTsStatus(any()) } returns
            TonStatusResult(
                transactions =
                    listOf(
                        TransactionJson(description = TonTransactionDescriptionJson(aborted = true))
                    )
            )

        val result = provider.checkStatus("hash", Chain.Ton)

        assertEquals(TransactionResult.Failed("Transaction aborted"), result)
    }

    @Test
    fun `transaction with aborted false returns Confirmed`() = runTest {
        coEvery { tonApi.getTsStatus(any()) } returns
            TonStatusResult(
                transactions =
                    listOf(
                        TransactionJson(
                            description = TonTransactionDescriptionJson(aborted = false)
                        )
                    )
            )

        val result = provider.checkStatus("hash", Chain.Ton)

        assertEquals(TransactionResult.Confirmed, result)
    }

    @Test
    fun `transaction with null description returns Pending`() = runTest {
        coEvery { tonApi.getTsStatus(any()) } returns
            TonStatusResult(transactions = listOf(TransactionJson(description = null)))

        val result = provider.checkStatus("hash", Chain.Ton)

        assertEquals(TransactionResult.Pending, result)
    }

    @Test
    fun `transaction present without aborted field returns Confirmed`() = runTest {
        coEvery { tonApi.getTsStatus(any()) } returns
            TonStatusResult(
                transactions =
                    listOf(
                        TransactionJson(description = TonTransactionDescriptionJson(aborted = null))
                    )
            )

        val result = provider.checkStatus("hash", Chain.Ton)

        assertEquals(TransactionResult.Confirmed, result)
    }

    @Test
    fun `compute phase exit code 1 returns Confirmed`() = runTest {
        // TVM treats exit codes 0 and 1 as success.
        coEvery { tonApi.getTsStatus(any()) } returns
            statusWith(
                TonTransactionDescriptionJson(
                    aborted = false,
                    computePhase = TonComputePhaseJson(exitCode = 1),
                )
            )

        assertEquals(TransactionResult.Confirmed, provider.checkStatus("hash", Chain.Ton))
    }

    @Test
    fun `compute phase revert returns Failed`() = runTest {
        coEvery { tonApi.getTsStatus(any()) } returns
            statusWith(
                TonTransactionDescriptionJson(
                    aborted = false,
                    computePhase = TonComputePhaseJson(exitCode = 37),
                )
            )

        val result = provider.checkStatus("hash", Chain.Ton)

        assertEquals(TransactionResult.Failed("Compute phase reverted (exit code 37)"), result)
    }

    @Test
    fun `action phase out of funds returns Failed even when not aborted`() = runTest {
        // The core bug: IGNORE_ACTION_PHASE_ERRORS leaves the tx un-aborted while the
        // transfer action is silently skipped for lack of funds — no funds actually moved.
        coEvery { tonApi.getTsStatus(any()) } returns
            statusWith(
                TonTransactionDescriptionJson(
                    aborted = false,
                    computePhase = TonComputePhaseJson(exitCode = 0),
                    actionPhase = TonActionPhaseJson(success = true, noFunds = true),
                )
            )

        val result = provider.checkStatus("hash", Chain.Ton)

        assertEquals(
            TransactionResult.Failed("Action phase failed — transfer not executed"),
            result,
        )
    }

    @Test
    fun `action phase with skipped actions returns Failed`() = runTest {
        coEvery { tonApi.getTsStatus(any()) } returns
            statusWith(
                TonTransactionDescriptionJson(
                    aborted = false,
                    actionPhase = TonActionPhaseJson(success = true, skippedActions = 1),
                )
            )

        assertEquals(
            TransactionResult.Failed("Action phase failed — transfer not executed"),
            provider.checkStatus("hash", Chain.Ton),
        )
    }

    @Test
    fun `action phase not successful returns Failed`() = runTest {
        coEvery { tonApi.getTsStatus(any()) } returns
            statusWith(
                TonTransactionDescriptionJson(
                    aborted = false,
                    actionPhase = TonActionPhaseJson(success = false),
                )
            )

        assertEquals(
            TransactionResult.Failed("Action phase failed — transfer not executed"),
            provider.checkStatus("hash", Chain.Ton),
        )
    }

    @Test
    fun `successful compute and action phases return Confirmed`() = runTest {
        coEvery { tonApi.getTsStatus(any()) } returns
            statusWith(
                TonTransactionDescriptionJson(
                    aborted = false,
                    computePhase = TonComputePhaseJson(exitCode = 0),
                    actionPhase =
                        TonActionPhaseJson(
                            success = true,
                            noFunds = false,
                            resultCode = 0,
                            skippedActions = 0,
                        ),
                )
            )

        assertEquals(TransactionResult.Confirmed, provider.checkStatus("hash", Chain.Ton))
    }

    @Test
    fun `api exception returns Pending`() = runTest {
        coEvery { tonApi.getTsStatus(any()) } throws RuntimeException("network error")

        val result = provider.checkStatus("hash", Chain.Ton)

        assertEquals(TransactionResult.Pending, result)
    }

    @Test
    fun `deserializes a real toncenter response and confirms a successful tx`() = runTest {
        // Verbatim shape from api.vultisig.com/ton v3/transactions (extra fields included) to prove
        // the compute_ph/action @SerialName mappings match production JSON and that unknown keys
        // are
        // tolerated the same way the ktor client tolerates them.
        val realJson =
            """
            {
              "transactions": [
                {
                  "hash": "ly7MV9j/7YxLIJECJyURsKthTLOJKtoYP6sMJLi/H8E=",
                  "description": {
                    "type": "ord",
                    "aborted": false,
                    "destroyed": false,
                    "storage_ph": { "storage_fees_collected": "0", "status_change": "unchanged" },
                    "compute_ph": {
                      "skipped": false, "success": true, "gas_used": "0",
                      "mode": 0, "exit_code": 0, "vm_steps": 66
                    },
                    "action": {
                      "success": true, "valid": true, "no_funds": false,
                      "status_change": "unchanged", "result_code": 0, "tot_actions": 1,
                      "skipped_actions": 0, "msgs_created": 1
                    }
                  }
                }
              ]
            }
            """
                .trimIndent()
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<TonStatusResult>(realJson)
        coEvery { tonApi.getTsStatus(any()) } returns decoded

        assertEquals(TransactionResult.Confirmed, provider.checkStatus("hash", Chain.Ton))
    }

    private fun statusWith(description: TonTransactionDescriptionJson) =
        TonStatusResult(transactions = listOf(TransactionJson(description = description)))
}
