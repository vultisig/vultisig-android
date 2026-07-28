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
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Covers the in-app review trigger (#5427): it must fire only for a transaction that actually
 * landed on-chain, and only once, since status polling re-emits the terminal state on every tick.
 */
internal class KeysignViewModelInAppReviewTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var inAppReviewRepository: InAppReviewRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        inAppReviewRepository = mockk(relaxed = true)
        coEvery { inAppReviewRepository.onTransactionSucceeded() } returns true
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a broadcast send asks for a review`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            val requests = mutableListOf<Unit>()
            val job = launch { vm.inAppReviewRequests.toList(requests) }

            vm.finishWith(TransactionStatus.Broadcasted)

            requests.size shouldBe 1
            job.cancel()
        }

    @Test
    fun `a confirmed send asks for a review`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            val requests = mutableListOf<Unit>()
            val job = launch { vm.inAppReviewRequests.toList(requests) }

            vm.finishWith(TransactionStatus.Confirmed)

            requests.size shouldBe 1
            job.cancel()
        }

    // The whole point of #5427: a user whose transaction failed must never be asked to rate.
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

    // Status polling re-emits KeysignFinished on every tick; counting each one would inflate the
    // success counter and re-ask on every poll.
    @Test
    fun `repeated terminal emissions ask only once`() =
        runTest(testDispatcher) {
            val vm = createViewModel()

            vm.finishWith(TransactionStatus.Pending)
            vm.finishWith(TransactionStatus.Confirmed)
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

    @Test
    fun `no review is requested while the throttle blocks it`() =
        runTest(testDispatcher) {
            coEvery { inAppReviewRepository.onTransactionSucceeded() } returns false
            val vm = createViewModel()
            val requests = mutableListOf<Unit>()
            val job = launch { vm.inAppReviewRequests.toList(requests) }

            vm.finishWith(TransactionStatus.Confirmed)

            requests.shouldBe(emptyList())
            job.cancel()
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
            awaitApprovalConfirmation = mockk(relaxed = true),
        )
}
