@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.InAppReviewRepository
import com.vultisig.wallet.ui.models.TransactionDetailsUiModel
import com.vultisig.wallet.ui.models.sign.SignMessageTransactionUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.asUiText
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
 * Covers the in-app review moment: it must be recorded only for a transaction that actually landed
 * on-chain, and only once, since status polling re-emits the terminal state on every tick. Whether
 * the card is then asked for is [InAppReviewRepository]'s decision, not this screen's.
 */
internal class KeysignViewModelInAppReviewTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var inAppReviewRepository: InAppReviewRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        inAppReviewRepository = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a broadcast send is a review moment`() =
        runTest(testDispatcher) {
            val vm = createViewModel()

            vm.finishWith(TransactionStatus.Broadcasted)

            coVerify(exactly = 1) { inAppReviewRepository.onTransactionSucceeded() }
        }

    @Test
    fun `a confirmed send is a review moment`() =
        runTest(testDispatcher) {
            val vm = createViewModel()

            vm.finishWith(TransactionStatus.Confirmed)

            coVerify(exactly = 1) { inAppReviewRepository.onTransactionSucceeded() }
        }

    // A user whose transaction failed must never be asked to rate.
    @Test
    fun `a failed transaction never asks for a review`() =
        runTest(testDispatcher) {
            val vm = createViewModel()

            vm.finishWith(TransactionStatus.Failed("boom".asUiText()))

            coVerify(exactly = 0) { inAppReviewRepository.onTransactionSucceeded() }
        }

    @Test
    fun `a refunded transaction never asks for a review`() =
        runTest(testDispatcher) {
            val vm = createViewModel()

            vm.finishWith(TransactionStatus.Refunded("paused".asUiText()))

            coVerify(exactly = 0) { inAppReviewRepository.onTransactionSucceeded() }
        }

    // PSBT co-signing never broadcasts, so nothing reached the chain to celebrate.
    @Test
    fun `a signed-but-not-broadcast transaction never asks for a review`() =
        runTest(testDispatcher) {
            val vm = createViewModel()

            vm.finishWith(TransactionStatus.Signed)

            coVerify(exactly = 0) { inAppReviewRepository.onTransactionSucceeded() }
        }

    // Status polling re-emits KeysignFinished on every tick, and a transaction that broadcasts and
    // then confirms passes through two successful statuses; recording each would re-arm the card.
    // The statuses have to differ for this to test anything: two identical ones carry equal state,
    // which the StateFlow conflates into a single emission the collector never sees twice.
    @Test
    fun `repeated terminal emissions record only once`() =
        runTest(testDispatcher) {
            val vm = createViewModel()

            vm.finishWith(TransactionStatus.Pending)
            vm.finishWith(TransactionStatus.Broadcasted)
            vm.finishWith(TransactionStatus.Confirmed)

            coVerify(exactly = 1) { inAppReviewRepository.onTransactionSucceeded() }
        }

    // dApp message signing produces no transaction, so it is not a "your funds moved" moment.
    @Test
    fun `message signing never asks for a review`() =
        runTest(testDispatcher) {
            val vm =
                createViewModel(TransactionTypeUiModel.SignMessage(SignMessageTransactionUiModel()))

            vm.finishWith(TransactionStatus.Broadcasted)

            coVerify(exactly = 0) { inAppReviewRepository.onTransactionSucceeded() }
        }

    private fun KeysignViewModel.finishWith(status: TransactionStatus) {
        updateUiStateForTesting { it.copy(signingState = KeysignState.KeysignFinished(status)) }
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
            doneTransactionPresentation = mockk(relaxed = true),
            awaitApprovalConfirmation = mockk(relaxed = true),
        )
}
