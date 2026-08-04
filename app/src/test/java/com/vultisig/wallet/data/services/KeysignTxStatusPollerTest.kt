package com.vultisig.wallet.data.services

import com.vultisig.wallet.data.api.txstatus.SwapKitTrackingService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TxStatusConfiguration
import com.vultisig.wallet.data.usecases.txstatus.TxStatusConfigurationProvider
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Covers how a done-screen poll ends (#5510): a transaction the status service never picked up must
 * be reported as untracked, so the screen stops showing a status nothing will ever advance.
 */
internal class KeysignTxStatusPollerTest {

    private val txHash = "0xhash"

    private lateinit var serviceManager: TransactionStatusServiceManager
    private lateinit var swapKitTrackingService: SwapKitTrackingService
    private lateinit var configurationProvider: TxStatusConfigurationProvider
    private lateinit var transactionHistoryRepository: TransactionHistoryRepository
    private lateinit var poller: KeysignTxStatusPoller

    @BeforeEach
    fun setUp() {
        serviceManager = mockk(relaxed = true)
        swapKitTrackingService = mockk(relaxed = true)
        configurationProvider = mockk(relaxed = true)
        transactionHistoryRepository = mockk(relaxed = true)
        every { serviceManager.serviceReady } returns MutableStateFlow(true)
        every { configurationProvider.getConfigurationForChain(any()) } returns
            TxStatusConfiguration(pollIntervalSeconds = 1L, maxWaitSeconds = 60L)
        poller =
            KeysignTxStatusPoller(
                transactionStatusServiceManager = serviceManager,
                swapKitTrackingService = swapKitTrackingService,
                txStatusConfigurationProvider = configurationProvider,
                transactionHistoryRepository = transactionHistoryRepository,
            )
    }

    // A refused foreground-service start never connects, so waiting on the binding would suspend
    // forever. Emitting a status here would leave the screen on a "Pending" nothing can advance.
    @Test
    fun `a rejected binding is untracked and emits no status`() = runTest {
        every { serviceManager.startPolling(txHash, Chain.Ethereum) } returns false
        val observed = mutableListOf<TransactionResult>()

        val outcome = poll(observed)

        outcome shouldBe TxStatusPollOutcome.NotTracked
        observed shouldBe emptyList()
        verify { serviceManager.stopPolling() }
    }

    // The system can accept the start and then drop it, so onServiceConnected never fires. Waiting
    // on serviceReady would then hold the screen on "Pending" for the rest of the session.
    @Test
    fun `a binding that never connects is untracked`() = runTest {
        every { serviceManager.startPolling(txHash, Chain.Ethereum) } returns true
        every { serviceManager.serviceReady } returns MutableStateFlow(false)
        val observed = mutableListOf<TransactionResult>()

        val outcome = poll(observed)

        outcome shouldBe TxStatusPollOutcome.NotTracked
        observed shouldBe listOf(TransactionResult.Pending)
        verify { serviceManager.stopPolling() }
    }

    // A service whose own startForeground threw never starts its poll job, so its status flow sits
    // on its initial Pending forever — indistinguishable from a slow chain until the budget lapses.
    @Test
    fun `a bound service that never reports is untracked once the poll budget lapses`() = runTest {
        every { serviceManager.startPolling(txHash, Chain.Ethereum) } returns true
        every { serviceManager.getStatusFlow() } returns MutableStateFlow(TransactionResult.Pending)
        val observed = mutableListOf<TransactionResult>()

        val outcome = poll(observed)

        outcome shouldBe TxStatusPollOutcome.NotTracked
        observed shouldBe listOf(TransactionResult.Pending, TransactionResult.Pending)
        verify { serviceManager.stopPolling() }
    }

    // The binder can be gone by the time the service reports ready (disconnect, concurrent stop):
    // same outcome, the transaction has no watcher.
    @Test
    fun `a vanished binder is untracked`() = runTest {
        every { serviceManager.startPolling(txHash, Chain.Ethereum) } returns true
        every { serviceManager.getStatusFlow() } returns null
        val observed = mutableListOf<TransactionResult>()

        val outcome = poll(observed)

        outcome shouldBe TxStatusPollOutcome.NotTracked
        observed shouldBe listOf(TransactionResult.Pending)
    }

    @Test
    fun `a settled transaction is terminal and every status is emitted and persisted`() = runTest {
        every { serviceManager.startPolling(txHash, Chain.Ethereum) } returns true
        every { serviceManager.getStatusFlow() } returns
            flowOf(TransactionResult.Pending, TransactionResult.Confirmed)
        val observed = mutableListOf<TransactionResult>()

        val outcome = poll(observed)

        outcome shouldBe TxStatusPollOutcome.Terminal
        observed shouldBe
            listOf(
                TransactionResult.Pending,
                TransactionResult.Pending,
                TransactionResult.Confirmed,
            )
        coVerify {
            transactionHistoryRepository.updateTransactionStatus(
                chain = Chain.Ethereum.raw,
                txHash = txHash,
                result = TransactionResult.Confirmed,
            )
        }
        verify { serviceManager.stopPolling() }
    }

    // A SwapKit swap settles on its destination leg, tracked without the foreground service.
    @Test
    fun `a settled SwapKit swap is terminal`() = runTest {
        every { swapKitTrackingService.canTrack(Chain.Ethereum) } returns true
        coEvery { swapKitTrackingService.checkSettlementStatus(txHash, Chain.Ethereum) } returns
            TransactionResult.Confirmed
        val observed = mutableListOf<TransactionResult>()

        val outcome = poll(observed, isSwapKitSwap = true)

        outcome shouldBe TxStatusPollOutcome.Terminal
        observed shouldBe listOf(TransactionResult.Pending, TransactionResult.Confirmed)
        verify(exactly = 0) { serviceManager.startPolling(any(), any()) }
    }

    // Only SwapKit can see a SwapKit swap's destination leg; a chain it can't track falls back to
    // the source-chain status service.
    @Test
    fun `a SwapKit swap on an untrackable chain falls back to the status service`() = runTest {
        every { swapKitTrackingService.canTrack(Chain.Ethereum) } returns false
        every { serviceManager.startPolling(txHash, Chain.Ethereum) } returns false

        val outcome = poll(mutableListOf(), isSwapKitSwap = true)

        outcome shouldBe TxStatusPollOutcome.NotTracked
        verify { serviceManager.startPolling(txHash, Chain.Ethereum) }
    }

    private suspend fun poll(
        observed: MutableList<TransactionResult>,
        isSwapKitSwap: Boolean = false,
    ) =
        poller.poll(
            txHash = txHash,
            chain = Chain.Ethereum,
            isSwapKitSwap = isSwapKitSwap,
            onStatus = { observed += it },
        )
}
