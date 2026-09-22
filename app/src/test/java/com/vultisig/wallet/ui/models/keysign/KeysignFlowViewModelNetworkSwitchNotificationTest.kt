@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.services.PushNotificationManager
import com.vultisig.wallet.data.services.TransactionStatusServiceManager
import com.vultisig.wallet.data.usecases.GenerateServiceName
import com.vultisig.wallet.ui.models.AddressProvider
import com.vultisig.wallet.ui.models.peer.NetworkOption
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.SnackbarFlow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldEndWith
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.CustomMessagePayload

internal class KeysignFlowViewModelNetworkSwitchNotificationTest {

    private val testDispatcher = StandardTestDispatcher()

    private val context: Context = mockk(relaxed = true)
    private val navigator: Navigator<Destination> = mockk(relaxed = true)
    private val addressProvider: AddressProvider = mockk(relaxed = true)
    private val generateServiceName: GenerateServiceName = mockk()
    private val transactionStatusServiceManager: TransactionStatusServiceManager =
        mockk(relaxed = true)
    private val pushNotificationManager: PushNotificationManager = mockk()
    private val snackbarFlow: SnackbarFlow = mockk(relaxed = true)
    private val keysignViewModelFactory: KeysignViewModel.Factory = mockk(relaxed = true)
    private val sessionCoordinator: KeysignSessionCoordinator = mockk(relaxed = true)
    private val participantDiscovery: KeysignParticipantDiscovery = mockk(relaxed = true)
    private val buildKeysignMessage: BuildKeysignMessageUseCase = mockk()
    private val updateSolanaKeysignPayload: UpdateSolanaKeysignPayloadUseCase = mockk()
    private val buildKeysignTransactionUiModel: BuildKeysignTransactionUiModelUseCase =
        mockk(relaxed = true)
    private val shareViewModel: KeysignShareViewModel = mockk(relaxed = true)

    private val vault =
        Vault(id = "vault", name = "Main", localPartyID = "A", signers = listOf("A", "B"))

    /** Every QR handed to the push service, in order. */
    private val pushed = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.Keysign.Keysign>() } returns
            Route.Keysign.Keysign(
                transactionId = "tx",
                password = null,
                txType = Route.Keysign.Keysign.TxType.Sign,
            )
        every { generateServiceName.invoke() } returns "svc"
        coEvery { pushNotificationManager.notifyVaultDevices(vault, capture(pushed)) } just runs
        coEvery { updateSolanaKeysignPayload(null) } returns null
        every { participantDiscovery.selection } returns MutableStateFlow(listOf("A"))
        every { participantDiscovery.participants } returns MutableStateFlow(emptyList())
        coEvery { buildKeysignMessage(any(), any(), any(), any(), any(), any(), any()) } answers
            {
                "server=${args[5]};relay=${args[6]}"
            }

        every { shareViewModel.hasAllData } returns true
        every { shareViewModel.vault } returns vault
        every { shareViewModel.keysignPayload } returns null
        every { shareViewModel.customMessagePayload } returns
            CustomMessagePayload(
                method = "personal_sign",
                message = "hello",
                chain = Chain.Ethereum.raw,
            )
        every { shareViewModel.amount } returns MutableStateFlow("")
        every { shareViewModel.toAmount } returns MutableStateFlow("")
        every { shareViewModel.qrBitmapPainter } returns MutableStateFlow(null)
        coEvery { shareViewModel.loadSignMessageTx(any()) } just runs

        // The payload rebuild runs under `withContext(Dispatchers.IO)`; a real thread pool would
        // let `runTest` burn through the cooldown's virtual-time delays while the body waits on it.
        // Pinned last: while this static mock is live, mockk records every Dispatchers getter,
        // which
        // would pollute the `coEvery` blocks above.
        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns testDispatcher
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        unmockkStatic(Dispatchers::class)
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        KeysignFlowViewModel(
            savedStateHandle = SavedStateHandle(),
            addressProvider = addressProvider,
            context = context,
            navigator = navigator,
            generateServiceName = generateServiceName,
            transactionStatusServiceManager = transactionStatusServiceManager,
            pushNotificationManager = pushNotificationManager,
            snackbarFlow = snackbarFlow,
            keysignViewModelFactory = keysignViewModelFactory,
            sessionCoordinator = sessionCoordinator,
            participantDiscovery = participantDiscovery,
            buildKeysignMessage = buildKeysignMessage,
            updateSolanaKeysignPayload = updateSolanaKeysignPayload,
            buildKeysignTransactionUiModel = buildKeysignTransactionUiModel,
        )

    @Test
    fun `opening the screen pushes the relay QR and starts the cooldown`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.setData(shareViewModel, context, Route.Keysign.Keysign.TxType.Sign)
            runCurrent()

            val relayQr = vm.keysignMessage.value
            relayQr shouldEndWith "jsonData=server=https://api.vultisig.com/router;relay=true"
            pushed shouldBe listOf(relayQr)
            vm.uiState.value.resendCooldownSeconds shouldBe 30
            vm.uiState.value.isNotificationPending shouldBe false
        }

    @Test
    fun `switching network inside the cooldown holds the rebuilt QR until the countdown ends`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.setData(shareViewModel, context, Route.Keysign.Keysign.TxType.Sign)
            runCurrent()
            val relayQr = vm.keysignMessage.value

            advanceTimeBy(5_000)
            runCurrent()
            vm.uiState.value.resendCooldownSeconds shouldBe 25

            vm.changeNetworkPromptOption(NetworkOption.Local, context)
            runCurrent()

            val localQr = vm.keysignMessage.value
            localQr shouldNotBe relayQr
            localQr shouldEndWith "jsonData=server=http://127.0.0.1:18080;relay=false"
            // The server would drop a push inside its window and still answer 200, so nothing is
            // sent yet: the countdown keeps running and the screen says the peers are next.
            pushed shouldBe listOf(relayQr)
            vm.uiState.value.isNotificationPending shouldBe true
            vm.uiState.value.resendCooldownSeconds shouldBe 25

            advanceTimeBy(25_000)
            runCurrent()

            pushed shouldBe listOf(relayQr, localQr)
            vm.uiState.value.isNotificationPending shouldBe false
            vm.uiState.value.resendCooldownSeconds shouldBe 30
        }

    @Test
    fun `switching back before the countdown ends cancels the hold`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.setData(shareViewModel, context, Route.Keysign.Keysign.TxType.Sign)
            runCurrent()
            val relayQr = vm.keysignMessage.value

            advanceTimeBy(5_000)
            vm.changeNetworkPromptOption(NetworkOption.Local, context)
            runCurrent()
            vm.uiState.value.isNotificationPending shouldBe true

            advanceTimeBy(5_000)
            vm.changeNetworkPromptOption(NetworkOption.Internet, context)
            runCurrent()

            // The peers already hold this QR, so there is nothing left to deliver.
            vm.keysignMessage.value shouldBe relayQr
            vm.uiState.value.isNotificationPending shouldBe false

            advanceTimeBy(21_000)
            runCurrent()
            pushed shouldBe listOf(relayQr)
            vm.uiState.value.resendCooldownSeconds shouldBe 0
        }

    @Test
    fun `switching network after the cooldown pushes the rebuilt QR at once`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.setData(shareViewModel, context, Route.Keysign.Keysign.TxType.Sign)
            runCurrent()
            val relayQr = vm.keysignMessage.value

            advanceTimeBy(31_000)
            runCurrent()
            vm.uiState.value.resendCooldownSeconds shouldBe 0

            vm.changeNetworkPromptOption(NetworkOption.Local, context)
            runCurrent()

            val localQr = vm.keysignMessage.value
            localQr shouldNotBe relayQr
            pushed shouldBe listOf(relayQr, localQr)
            vm.uiState.value.isNotificationPending shouldBe false
            vm.uiState.value.resendCooldownSeconds shouldBe 30
        }

    @Test
    fun `switching network while a push is in flight waits for that push's cooldown`() =
        runTest(testDispatcher) {
            coEvery { pushNotificationManager.notifyVaultDevices(vault, capture(pushed)) } coAnswers
                {
                    delay(2_000)
                }
            val vm = createViewModel()
            vm.setData(shareViewModel, context, Route.Keysign.Keysign.TxType.Sign)
            runCurrent()
            val relayQr = vm.keysignMessage.value
            pushed shouldBe listOf(relayQr)
            vm.uiState.value.resendCooldownSeconds shouldBe 0

            // The server's window opens when it receives the first push, not when it answers, so a
            // rebuild during the round-trip must not be fired into it.
            vm.changeNetworkPromptOption(NetworkOption.Local, context)
            runCurrent()
            val localQr = vm.keysignMessage.value
            pushed shouldBe listOf(relayQr)
            vm.uiState.value.isNotificationPending shouldBe true

            advanceTimeBy(2_000)
            runCurrent()
            vm.uiState.value.resendCooldownSeconds shouldBe 30

            advanceTimeBy(30_000)
            runCurrent()
            pushed shouldBe listOf(relayQr, localQr)
            vm.uiState.value.isNotificationPending shouldBe false
        }

    @Test
    fun `manual resend inside the cooldown is still dropped`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.setData(shareViewModel, context, Route.Keysign.Keysign.TxType.Sign)
            runCurrent()

            advanceTimeBy(5_000)
            vm.sendNotification()
            runCurrent()

            pushed shouldBe listOf(vm.keysignMessage.value)
        }
}
