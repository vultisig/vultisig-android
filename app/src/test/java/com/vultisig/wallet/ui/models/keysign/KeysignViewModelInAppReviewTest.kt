@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.AppReviewEvent
import com.vultisig.wallet.data.repositories.InAppReviewRepository
import com.vultisig.wallet.ui.models.TransactionDetailsUiModel
import com.vultisig.wallet.ui.models.sign.SignMessageTransactionUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.asUiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Covers the outbound half of the review trigger (#5700): a transaction only counts as a positive
 * moment when it actually landed on-chain, and only once, since status polling re-emits the
 * terminal state on every tick.
 */
internal class KeysignViewModelInAppReviewTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var inAppReviewRepository: InAppReviewRepository

    private val sendEvent = AppReviewEvent.ConfirmedOutboundTransaction(TX_HASH)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        inAppReviewRepository = mockk(relaxed = true)
        coEvery { inAppReviewRepository.record(any()) } returns true
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a broadcast send counts as a positive moment`() =
        runTest(testDispatcher) {
            val vm = createViewModel()

            vm.finishWith(TransactionStatus.Broadcasted)

            coVerify(exactly = 1) { inAppReviewRepository.record(sendEvent) }
            coVerify(exactly = 1) { inAppReviewRepository.requestPromptEvaluation() }
        }

    @Test
    fun `a confirmed send counts as a positive moment`() =
        runTest(testDispatcher) {
            val vm = createViewModel()

            vm.finishWith(TransactionStatus.Confirmed)

            coVerify(exactly = 1) { inAppReviewRepository.record(sendEvent) }
        }

    // The whole point of the gate: a user whose transaction failed must never be asked to rate.
    @Test
    fun `a failed transaction never counts`() =
        runTest(testDispatcher) {
            val vm = createViewModel()

            vm.finishWith(TransactionStatus.Failed("boom".asUiText()))

            coVerify(exactly = 0) { inAppReviewRepository.record(any()) }
        }

    @Test
    fun `a refunded transaction never counts`() =
        runTest(testDispatcher) {
            val vm = createViewModel()

            vm.finishWith(TransactionStatus.Refunded("paused".asUiText()))

            coVerify(exactly = 0) { inAppReviewRepository.record(any()) }
        }

    // PSBT co-signing never broadcasts, so nothing reached the chain to celebrate.
    @Test
    fun `a signed-but-not-broadcast transaction never counts`() =
        runTest(testDispatcher) {
            val vm = createViewModel()

            vm.finishWith(TransactionStatus.Signed)

            coVerify(exactly = 0) { inAppReviewRepository.record(any()) }
        }

    // Status polling re-emits KeysignFinished on every tick; counting each one would inflate the
    // event count and re-open an opportunity on every poll.
    @Test
    fun `repeated terminal emissions count only once`() =
        runTest(testDispatcher) {
            val vm = createViewModel()

            vm.finishWith(TransactionStatus.Pending)
            vm.finishWith(TransactionStatus.Confirmed)
            vm.finishWith(TransactionStatus.Confirmed)

            coVerify(exactly = 1) { inAppReviewRepository.record(any()) }
        }

    // dApp message signing produces no transaction, so it is not a "your funds moved" moment.
    @Test
    fun `message signing never counts`() =
        runTest(testDispatcher) {
            val vm =
                createViewModel(TransactionTypeUiModel.SignMessage(SignMessageTransactionUiModel()))

            vm.finishWith(TransactionStatus.Broadcasted)

            coVerify(exactly = 0) { inAppReviewRepository.record(any()) }
        }

    // An already-counted hash must not open a fresh opportunity: that is how a re-entered done
    // screen would spend a version's single ask with no new milestone behind it.
    @Test
    fun `an already counted transaction opens no opportunity`() =
        runTest(testDispatcher) {
            coEvery { inAppReviewRepository.record(any()) } returns false
            val vm = createViewModel()

            vm.finishWith(TransactionStatus.Confirmed)

            coVerify(exactly = 0) { inAppReviewRepository.requestPromptEvaluation() }
        }

    private fun KeysignViewModel.finishWith(status: TransactionStatus) {
        updateUiStateForTesting {
            it.copy(signingState = KeysignState.KeysignFinished(status), txHash = TX_HASH)
        }
    }

    private fun createViewModel(
        transactionTypeUiModel: TransactionTypeUiModel =
            TransactionTypeUiModel.Send(TransactionDetailsUiModel())
    ) =
        KeysignViewModel(
            vault = Vault(id = "v1", name = "Test Vault"),
            keysignCommittee = emptyList(),
            serverUrl = "",
            sessionId = "",
            encryptionKeyHex = "",
            messagesToSign = emptyList(),
            keyType = TssKeyType.ECDSA,
            keysignPayload = null,
            customMessagePayload = null,
            transactionTypeUiModel = transactionTypeUiModel,
            isInitiatingDevice = false,
            transactionHistoryData = null,
            thorChainApi = mockk(relaxed = true),
            evmApiFactory = mockk(relaxed = true),
            broadcastTx = mockk(relaxed = true),
            explorerLinkRepository = mockk(relaxed = true),
            navigator = mockk<Navigator<Destination>>(relaxed = true),
            sessionApi = mockk(relaxed = true),
            encryption = mockk(relaxed = true),
            featureFlagApi = mockk(relaxed = true),
            pullTssMessages = mockk(relaxed = true),
            addressBookRepository = mockk(relaxed = true),
            txStatusConfigurationProvider = mockk(relaxed = true),
            txStatusPoller = mockk(relaxed = true),
            vaultRepository = mockk(relaxed = true),
            chainAccountAddressRepository = mockk(relaxed = true),
            transactionHistoryRepository = mockk(relaxed = true),
            balanceRepository = mockk(relaxed = true),
            inAppReviewRepository = inAppReviewRepository,
            gasFeeToEstimatedFee = mockk(relaxed = true),
            pendingLimitOrderRepository = mockk(relaxed = true),
            awaitApprovalConfirmation = mockk(relaxed = true),
        )

    private companion object {
        const val TX_HASH = "0xdeadbeef"
    }
}
