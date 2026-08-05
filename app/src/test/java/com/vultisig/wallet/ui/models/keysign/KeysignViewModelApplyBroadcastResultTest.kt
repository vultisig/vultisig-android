@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.CommonTransactionHistoryData
import com.vultisig.wallet.data.models.SendTransactionHistoryData
import com.vultisig.wallet.data.models.TransactionHistoryData
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import com.vultisig.wallet.data.services.KeysignTxStatusPoller
import com.vultisig.wallet.data.services.TxStatusPollOutcome
import com.vultisig.wallet.data.usecases.KeysignBroadcastResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TxStatusConfigurationProvider
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class KeysignViewModelApplyBroadcastResultTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val vault = Vault(id = "v1", name = "Test Vault")

    private val sendTxData =
        SendTransactionHistoryData(
            fromAddress = "0xsender",
            toAddress = "0xdest",
            amount = "1",
            token = "ETH",
            tokenLogo = "eth",
            feeEstimate = "0.001",
            memo = "",
            fiatValue = "100",
        )

    private lateinit var txStatusConfigurationProvider: TxStatusConfigurationProvider
    private lateinit var transactionHistoryRepository: TransactionHistoryRepository
    private lateinit var explorerLinkRepository: ExplorerLinkRepository
    private lateinit var txStatusPoller: KeysignTxStatusPoller

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        txStatusConfigurationProvider = mockk(relaxed = true)
        transactionHistoryRepository = mockk(relaxed = true)
        explorerLinkRepository = mockk(relaxed = true)
        txStatusPoller = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // A hashless broadcast must still reach a terminal state; otherwise signingState stays at the
    // last signing state forever (infinite spinner) and the user may force-retry → double-send.
    @Test
    fun `broadcasted with null txHash reaches the terminal broadcasted state`() =
        runTest(testDispatcher) {
            val vm = createViewModel()

            vm.applyBroadcastResult(broadcasted(txHash = null))

            vm.state.value.signingState shouldBe
                KeysignState.KeysignFinished(TransactionStatus.Broadcasted)
        }

    // Guards the refactor: the non-polling success path still lands on the same terminal state.
    @Test
    fun `broadcasted with txHash on a non-status chain reaches the terminal broadcasted state`() =
        runTest(testDispatcher) {
            every { txStatusConfigurationProvider.supportTxStatus(any()) } returns false
            val vm = createViewModel()

            vm.applyBroadcastResult(broadcasted(txHash = "0xhash"))

            vm.state.value.run {
                signingState shouldBe KeysignState.KeysignFinished(TransactionStatus.Broadcasted)
                txHash shouldBe "0xhash"
            }
        }

    // Each transaction of a batch must land in history with its own explorer link (issue #5238).
    @Test
    fun `broadcasted batch persists history for the primary and additional hashes`() =
        runTest(testDispatcher) {
            every { txStatusConfigurationProvider.supportTxStatus(any()) } returns false
            every { explorerLinkRepository.getTransactionLink(Chain.Ethereum, any()) } answers
                {
                    "https://etherscan.io/tx/${secondArg<String>()}"
                }
            val vm = createViewModel(transactionHistoryData = sendTxData)

            vm.applyBroadcastResult(
                broadcasted(txHash = "0xhash1", additionalTxHashes = listOf("0xhash2", "0xhash3"))
            )

            vm.state.value.txHash shouldBe "0xhash1"
            listOf("0xhash1", "0xhash2", "0xhash3").forEach { hash ->
                val genericData = slot<CommonTransactionHistoryData>()
                coVerify(exactly = 1) {
                    transactionHistoryRepository.recordTransaction(
                        vaultId = "v1",
                        txHash = hash,
                        txData = sendTxData,
                        genericData = capture(genericData),
                    )
                }
                genericData.captured.explorerUrl shouldBe "https://etherscan.io/tx/$hash"
            }
        }

    // The additional hashes of a batch are real broadcast transactions in their own right — a
    // missing primary hash must not skip their history rows.
    @Test
    fun `broadcasted batch with null primary hash still persists additional hashes`() =
        runTest(testDispatcher) {
            every { explorerLinkRepository.getTransactionLink(Chain.Ethereum, "0xhash2") } returns
                "https://etherscan.io/tx/0xhash2"
            val vm = createViewModel(transactionHistoryData = sendTxData)

            vm.applyBroadcastResult(
                broadcasted(txHash = null, additionalTxHashes = listOf("0xhash2"))
            )

            vm.state.value.signingState shouldBe
                KeysignState.KeysignFinished(TransactionStatus.Broadcasted)
            val genericData = slot<CommonTransactionHistoryData>()
            coVerify(exactly = 1) {
                transactionHistoryRepository.recordTransaction(
                    vaultId = "v1",
                    txHash = "0xhash2",
                    txData = sendTxData,
                    genericData = capture(genericData),
                )
            }
            genericData.captured.explorerUrl shouldBe "https://etherscan.io/tx/0xhash2"
        }

    // The status service can be refused (backgrounded app, API 31+), and nothing else will ever
    // report on the transaction — so the done screen must not keep showing the "Pending" the poller
    // emitted on its way out (issue #5510).
    @Test
    fun `a transaction nothing tracks reaches the terminal broadcasted state`() =
        runTest(testDispatcher) {
            val vm = createPollingViewModel { TxStatusPollOutcome.NotTracked }

            vm.applyBroadcastResult(broadcasted(txHash = "0xhash"))

            vm.state.value.signingState shouldBe
                KeysignState.KeysignFinished(TransactionStatus.Broadcasted)
        }

    // "There is nothing to poll" and "the poller never started" are the same thing to the user — a
    // clean broadcast with no watcher — so both must land on the same status.
    @Test
    fun `an untracked transaction lands where an unpollable one lands`() =
        runTest(testDispatcher) {
            val untracked = createPollingViewModel { TxStatusPollOutcome.NotTracked }
            untracked.applyBroadcastResult(broadcasted(txHash = "0xhash"))

            every { txStatusConfigurationProvider.supportTxStatus(any()) } returns false
            val unpollable = createViewModel()
            unpollable.applyBroadcastResult(broadcasted(txHash = "0xhash"))

            untracked.state.value.signingState shouldBe unpollable.state.value.signingState
        }

    // A SwapKit swap whose foreground budget ran out is genuinely still settling; claiming
    // "Transaction successful" would assert an outcome the app never observed.
    @Test
    fun `a handed-off transaction keeps the last observed status`() =
        runTest(testDispatcher) {
            val vm = createPollingViewModel { onStatus ->
                onStatus(TransactionResult.Pending)
                TxStatusPollOutcome.HandedOff
            }

            vm.applyBroadcastResult(broadcasted(txHash = "0xhash"))

            vm.state.value.signingState shouldBe
                KeysignState.KeysignFinished(TransactionStatus.Pending)
        }

    // The settled status the poller observed must survive: the untracked fallback may not overwrite
    // a real on-chain result.
    @Test
    fun `a settled transaction keeps the observed terminal status`() =
        runTest(testDispatcher) {
            val vm = createPollingViewModel { onStatus ->
                onStatus(TransactionResult.Confirmed)
                TxStatusPollOutcome.Terminal
            }

            vm.applyBroadcastResult(broadcasted(txHash = "0xhash"))

            vm.state.value.signingState shouldBe
                KeysignState.KeysignFinished(TransactionStatus.Confirmed)
        }

    private fun broadcasted(txHash: String?, additionalTxHashes: List<String> = emptyList()) =
        KeysignBroadcastResult.Broadcasted(
            chain = Chain.Ethereum,
            txHash = txHash,
            txLink = if (txHash != null) "https://etherscan.io/tx/$txHash" else "",
            swapProgressLink = null,
            approveTxHash = "",
            approveTxLink = "",
            additionalTxHashes = additionalTxHashes,
        )

    /** A ViewModel on a status-polling chain whose poll body is [poll]. */
    private fun createPollingViewModel(
        poll: suspend (onStatus: suspend (TransactionResult) -> Unit) -> TxStatusPollOutcome
    ): KeysignViewModel {
        val onStatus = slot<suspend (TransactionResult) -> Unit>()
        every { txStatusConfigurationProvider.supportTxStatus(any()) } returns true
        coEvery { txStatusPoller.poll(any(), any(), any(), capture(onStatus)) } coAnswers
            {
                poll(onStatus.captured)
            }
        return createViewModel()
    }

    private fun createViewModel(transactionHistoryData: TransactionHistoryData? = null) =
        KeysignViewModel(
            vault = vault,
            keysignCommittee = emptyList(),
            serverUrl = "",
            sessionId = "",
            encryptionKeyHex = "",
            messagesToSign = emptyList(),
            keyType = TssKeyType.ECDSA,
            keysignPayload = null,
            customMessagePayload = null,
            transactionTypeUiModel = null,
            isInitiatingDevice = false,
            transactionHistoryData = transactionHistoryData,
            thorChainApi = mockk(relaxed = true),
            evmApiFactory = mockk(relaxed = true),
            broadcastTx = mockk(relaxed = true),
            explorerLinkRepository = explorerLinkRepository,
            navigator = mockk<Navigator<Destination>>(relaxed = true),
            sessionApi = mockk(relaxed = true),
            encryption = mockk(relaxed = true),
            featureFlagApi = mockk(relaxed = true),
            pullTssMessages = mockk(relaxed = true),
            addressBookRepository = mockk(relaxed = true),
            txStatusConfigurationProvider = txStatusConfigurationProvider,
            txStatusPoller = txStatusPoller,
            vaultRepository = mockk(relaxed = true),
            chainAccountAddressRepository = mockk(relaxed = true),
            transactionHistoryRepository = transactionHistoryRepository,
            balanceRepository = mockk(relaxed = true),
            inAppReviewRepository = mockk(relaxed = true),
            gasFeeToEstimatedFee = mockk(relaxed = true),
            pendingLimitOrderRepository = mockk(relaxed = true),
            awaitApprovalConfirmation = mockk(relaxed = true),
        )
}
