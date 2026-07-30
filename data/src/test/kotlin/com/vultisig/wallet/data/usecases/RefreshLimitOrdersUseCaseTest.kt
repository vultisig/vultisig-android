package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.ThorchainLimitSwapQueueEntry
import com.vultisig.wallet.data.api.models.thorchain.ThorchainLimitSwapQueueResponse
import com.vultisig.wallet.data.api.models.thorchain.ThorchainQueuedCoin
import com.vultisig.wallet.data.api.models.thorchain.ThorchainQueuedSwap
import com.vultisig.wallet.data.api.models.thorchain.ThorchainQueuedSwapState
import com.vultisig.wallet.data.api.models.thorchain.ThorchainQueuedSwapTx
import com.vultisig.wallet.data.api.models.thorchain.ThorchainWireAsset
import com.vultisig.wallet.data.api.txstatus.LimitOrderOutcome
import com.vultisig.wallet.data.api.txstatus.MidgardLimitOutcomeResolver
import com.vultisig.wallet.data.db.models.PendingLimitOrderEntity
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.LimitOrderStatus
import com.vultisig.wallet.data.repositories.PendingLimitOrderRepository
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The tracker's only terminal signal is an order's ABSENCE from the queue, and a load-balancing
 * gateway can fabricate that. These tests pin the corroboration rules that stop a well-formed but
 * stale response from closing every one of a vault's live orders at once.
 */
internal class RefreshLimitOrdersUseCaseTest {

    private val repository = mockk<PendingLimitOrderRepository>(relaxed = true)
    private val thorChainApi = mockk<ThorChainApi>()
    private val outcomes = mockk<MidgardLimitOutcomeResolver>()
    private val transactionStatusRepository = mockk<TransactionStatusRepository>(relaxed = true)

    private fun useCase() =
        RefreshLimitOrdersUseCase(repository, thorChainApi, outcomes, transactionStatusRepository)

    private val order =
        PendingLimitOrderEntity(
            inboundTxHash = "HASH",
            vaultId = "vault",
            sourceAsset = "THOR.RUNE",
            sourceAmount = "100000000",
            targetAsset = "BTC.BTC",
            destAddr = "bc1qxy",
            targetPrice = "0.04",
            expiryBlocks = 14_400,
            createdAt = 0L,
            status = LimitOrderStatus.Pending.raw,
            sourceChain = Chain.ThorChain.raw,
            sourceDecimals = 8,
            sourceAddress = "thor1abc",
            sourceAmount1e8 = "100000000",
            tradeTarget = "4000000",
            sourceAssetFull = "THOR.RUNE",
            targetAssetFull = "BTC.BTC",
        )

    private fun restingEntry(hash: String = "hash") =
        ThorchainLimitSwapQueueEntry(
            timeToExpiryBlocks = "13889",
            blocksSinceCreated = "511",
            swap =
                ThorchainQueuedSwap(
                    tx =
                        ThorchainQueuedSwapTx(
                            id = hash,
                            fromAddress = "thor1abc",
                            memo = "=<:BTC.BTC:bc1qxy:4000000/14400/0",
                            coins =
                                listOf(
                                    ThorchainQueuedCoin(
                                        asset = ThorchainWireAsset("THOR.RUNE"),
                                        amount = "100000000",
                                    )
                                ),
                        ),
                    state =
                        ThorchainQueuedSwapState(
                            deposit = "100000000",
                            inAmount = "40000000",
                            outAmount = "1600000",
                        ),
                    targetAsset = ThorchainWireAsset("BTC.BTC"),
                    tradeTarget = "4000000",
                ),
        )

    @Test
    fun `records the fill split and expiry countdown for a resting order`() = runTest {
        coEvery { repository.getOpenOrders("vault") } returns listOf(order)
        coEvery { thorChainApi.getLimitSwapQueue("thor1abc") } returns
            ThorchainLimitSwapQueueResponse(listOf(restingEntry()))

        useCase().invoke("vault")

        coVerify {
            repository.recordObservation(
                inboundTxHash = "HASH",
                depositAmount = "100000000",
                filledInAmount = "40000000",
                filledOutAmount = "1600000",
                observedTradeTarget = "4000000",
                observedSourceAsset = "THOR.RUNE",
                observedTargetAsset = "BTC.BTC",
                timeToExpiryBlocks = 13_889,
                observedAt = any(),
            )
        }
        // A resting observation must never write a status: it would clobber the `cancelling` a
        // local
        // cancel record put there.
        coVerify(exactly = 0) { repository.recordStatus(any(), any()) }
    }

    @Test
    fun `matches the queue's hash case-insensitively`() = runTest {
        // Hex case is not semantic, and the queue's casing need not match the broadcast hash.
        coEvery { repository.getOpenOrders("vault") } returns listOf(order)
        coEvery { thorChainApi.getLimitSwapQueue("thor1abc") } returns
            ThorchainLimitSwapQueueResponse(listOf(restingEntry(hash = "hash")))

        useCase().invoke("vault")

        coVerify {
            repository.recordObservation(
                inboundTxHash = "HASH",
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
        coVerify(exactly = 0) { repository.recordStatus(any(), any()) }
    }

    @Test
    fun `a single absent poll does not close the order`() = runTest {
        coEvery { repository.getOpenOrders("vault") } returns listOf(order)
        coEvery { thorChainApi.getLimitSwapQueue("thor1abc") } returns
            ThorchainLimitSwapQueueResponse(emptyList())

        useCase().invoke("vault")

        // A backend that is behind answers with a well-formed, genuinely empty array; nothing at
        // the
        // decoding layer can tell that from a real closure.
        coVerify(exactly = 0) { repository.recordStatus(any(), any()) }
    }

    @Test
    fun `two consecutive absent polls close the order with THORChain's own reason`() = runTest {
        coEvery { repository.getOpenOrders("vault") } returns listOf(order)
        coEvery { thorChainApi.getLimitSwapQueue("thor1abc") } returns
            ThorchainLimitSwapQueueResponse(emptyList())
        coEvery { outcomes.resolveOutcome("HASH") } returns LimitOrderOutcome.Expired

        val useCase = useCase()
        useCase("vault")
        useCase("vault")

        coVerify(exactly = 1) { repository.recordStatus("HASH", LimitOrderStatus.Expired) }
    }

    @Test
    fun `a reappearing order resets its absence streak`() = runTest {
        coEvery { repository.getOpenOrders("vault") } returns listOf(order)
        coEvery { outcomes.resolveOutcome("HASH") } returns LimitOrderOutcome.Expired

        val useCase = useCase()
        coEvery { thorChainApi.getLimitSwapQueue("thor1abc") } returns
            ThorchainLimitSwapQueueResponse(emptyList())
        useCase("vault")
        // Reappearing is the stale-backend signature itself, and the reset is the self-correcting
        // half of the guard.
        coEvery { thorChainApi.getLimitSwapQueue("thor1abc") } returns
            ThorchainLimitSwapQueueResponse(listOf(restingEntry()))
        useCase("vault")
        coEvery { thorChainApi.getLimitSwapQueue("thor1abc") } returns
            ThorchainLimitSwapQueueResponse(emptyList())
        useCase("vault")

        coVerify(exactly = 0) { repository.recordStatus(any(), any()) }
    }

    @Test
    fun `a response with no limit_swaps key is not read as an empty queue`() = runTest {
        coEvery { repository.getOpenOrders("vault") } returns listOf(order)
        coEvery { thorChainApi.getLimitSwapQueue("thor1abc") } returns
            ThorchainLimitSwapQueueResponse(limitSwaps = null)

        val useCase = useCase()
        useCase("vault")
        useCase("vault")

        // Reading an unrecognised envelope as empty would close every one of this sender's orders
        // at
        // once on the strength of a response we did not understand.
        coVerify(exactly = 0) { repository.recordStatus(any(), any()) }
    }

    @Test
    fun `an unresolvable outcome leaves the order resting`() = runTest {
        coEvery { repository.getOpenOrders("vault") } returns listOf(order)
        coEvery { thorChainApi.getLimitSwapQueue("thor1abc") } returns
            ThorchainLimitSwapQueueResponse(emptyList())
        coEvery { outcomes.resolveOutcome("HASH") } returns LimitOrderOutcome.Unresolved

        val useCase = useCase()
        useCase("vault")
        useCase("vault")

        // Almost always Midgard indexing lag. A guess here is permanent — nothing revisits a
        // terminal order.
        coVerify(exactly = 0) { repository.recordStatus(any(), any()) }
    }

    @Test
    fun `a queue failure writes nothing`() = runTest {
        coEvery { repository.getOpenOrders("vault") } returns listOf(order)
        coEvery { thorChainApi.getLimitSwapQueue("thor1abc") } throws RuntimeException("timeout")

        val useCase = useCase()
        useCase("vault")
        useCase("vault")

        coVerify(exactly = 0) { repository.recordStatus(any(), any()) }
        coVerify(exactly = 0) {
            repository.recordObservation(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `an order with no sender address is left alone`() = runTest {
        coEvery { repository.getOpenOrders("vault") } returns
            listOf(order.copy(sourceAddress = null))

        useCase().invoke("vault")

        // The queue is addressed by sender; with none there is nothing to poll, and fetching the
        // whole network's queue is not an option.
        coVerify(exactly = 0) { thorChainApi.getLimitSwapQueue(any()) }
    }

    @Test
    fun `a refund with no recognised reason is credited to a confirmed cancel inside the TTL`() =
        runTest {
            val cancelling =
                order.copy(
                    status = LimitOrderStatus.Cancelling.raw,
                    cancelBroadcastHash = "0xcancel",
                    cancelConfirmed = true,
                    createdAt = System.currentTimeMillis(),
                )
            coEvery { repository.getOpenOrders("vault") } returns listOf(cancelling)
            coEvery { thorChainApi.getLimitSwapQueue("thor1abc") } returns
                ThorchainLimitSwapQueueResponse(emptyList())
            coEvery { outcomes.resolveOutcome("HASH") } returns LimitOrderOutcome.Refunded

            val useCase = useCase()
            useCase("vault")
            useCase("vault")

            coVerify { repository.recordStatus("HASH", LimitOrderStatus.Cancelled) }
        }

    @Test
    fun `a refund is not credited to a cancel that was only broadcast`() = runTest {
        val cancelling =
            order.copy(
                status = LimitOrderStatus.Cancelling.raw,
                cancelBroadcastHash = "0xcancel",
                cancelConfirmed = false,
                createdAt = System.currentTimeMillis(),
            )
        coEvery { repository.getOpenOrders("vault") } returns listOf(cancelling)
        coEvery { thorChainApi.getLimitSwapQueue("thor1abc") } returns
            ThorchainLimitSwapQueueResponse(emptyList())
        coEvery { outcomes.resolveOutcome("HASH") } returns LimitOrderOutcome.Refunded

        val useCase = useCase()
        useCase("vault")
        useCase("vault")

        // A broadcast the chain later refuses must never let an unrelated refund read as
        // "cancelled".
        coVerify { repository.recordStatus("HASH", LimitOrderStatus.Refunded) }
    }

    @Test
    fun `a cancel the chain refused has its record withdrawn`() = runTest {
        val cancelling =
            order.copy(status = LimitOrderStatus.Cancelling.raw, cancelBroadcastHash = "0xcancel")
        coEvery { repository.getOpenOrders("vault") } returns listOf(cancelling)
        coEvery { thorChainApi.getLimitSwapQueue("thor1abc") } returns
            ThorchainLimitSwapQueueResponse(listOf(restingEntry()))
        coEvery {
            transactionStatusRepository.checkTransactionStatus("0xcancel", Chain.ThorChain)
        } returns TransactionResult.Failed("could not find matching limit swap")

        useCase().invoke("vault")

        // Kept, the record would disable the Cancel button for good on an order that is still
        // resting, and pre-label its eventual closure "cancelled".
        coVerify { repository.clearCancelBroadcast("HASH", "0xcancel") }
    }

    @Test
    fun `a cancel with no verdict yet is neither confirmed nor withdrawn`() = runTest {
        val cancelling =
            order.copy(status = LimitOrderStatus.Cancelling.raw, cancelBroadcastHash = "0xcancel")
        coEvery { repository.getOpenOrders("vault") } returns listOf(cancelling)
        coEvery { thorChainApi.getLimitSwapQueue("thor1abc") } returns
            ThorchainLimitSwapQueueResponse(listOf(restingEntry()))
        coEvery {
            transactionStatusRepository.checkTransactionStatus("0xcancel", Chain.ThorChain)
        } returns TransactionResult.Pending

        useCase().invoke("vault")

        coVerify(exactly = 0) { repository.clearCancelBroadcast(any(), any()) }
        coVerify(exactly = 0) { repository.confirmCancelBroadcast(any(), any()) }
    }

    @Test
    fun `a confirmed cancel is recorded once and not re-checked`() = runTest {
        val cancelling =
            order.copy(status = LimitOrderStatus.Cancelling.raw, cancelBroadcastHash = "0xcancel")
        coEvery { repository.getOpenOrders("vault") } returns listOf(cancelling)
        coEvery { thorChainApi.getLimitSwapQueue("thor1abc") } returns
            ThorchainLimitSwapQueueResponse(listOf(restingEntry()))
        coEvery {
            transactionStatusRepository.checkTransactionStatus("0xcancel", Chain.ThorChain)
        } returns TransactionResult.Confirmed

        val useCase = useCase()
        useCase("vault")
        useCase("vault")

        coVerify(exactly = 1) { repository.confirmCancelBroadcast("HASH", "0xcancel") }
        coVerify(exactly = 1) {
            transactionStatusRepository.checkTransactionStatus("0xcancel", Chain.ThorChain)
        }
    }
}
