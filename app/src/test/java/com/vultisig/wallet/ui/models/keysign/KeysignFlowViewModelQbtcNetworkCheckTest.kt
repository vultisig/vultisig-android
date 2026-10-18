@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.QbtcNetworkCheck
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.services.PushNotificationManager
import com.vultisig.wallet.data.usecases.GenerateServiceName
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.math.BigInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.CustomMessagePayload
import vultisig.keysign.v1.TransactionType

internal class KeysignFlowViewModelQbtcNetworkCheckTest {

    private val testDispatcher = StandardTestDispatcher()

    private val context: Context = mockk(relaxed = true)
    private val generateServiceName: GenerateServiceName = mockk()
    private val pushNotificationManager: PushNotificationManager = mockk()
    private val participantDiscovery: KeysignParticipantDiscovery = mockk(relaxed = true)
    private val buildKeysignMessage: BuildKeysignMessageUseCase = mockk()
    private val updateSolanaKeysignPayload: UpdateSolanaKeysignPayloadUseCase = mockk()
    private val qbtcNetworkCheck: QbtcNetworkCheck = mockk()
    private val shareViewModel: KeysignShareViewModel = mockk(relaxed = true)

    // Counted by hand: while Dispatchers is statically mocked, coVerify also records its getters.
    private var buildCalls = 0
    private var pushCalls = 0
    private var networkChecks = 0

    private val vault =
        Vault(id = "vault", name = "Main", localPartyID = "A", signers = listOf("A", "B"))

    private val qbtcSend =
        KeysignPayload(
            coin = Coins.Qbtc.QBTC,
            toAddress = "qbtc1recipient",
            toAmount = BigInteger.ONE,
            blockChainSpecific =
                BlockChainSpecific.Cosmos(
                    accountNumber = BigInteger.ONE,
                    sequence = BigInteger.ZERO,
                    gas = BigInteger.valueOf(800),
                    ibcDenomTraces = null,
                    transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                ),
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "A",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.Keysign.Keysign>() } returns
            Route.Keysign.Keysign(
                transactionId = "tx",
                password = null,
                txType = Route.Keysign.Keysign.TxType.Send,
            )
        every { generateServiceName.invoke() } returns "svc"
        coEvery { pushNotificationManager.notifyVaultDevices(any(), any()) } answers { pushCalls++ }
        every { participantDiscovery.selection } returns MutableStateFlow(listOf("A"))
        every { participantDiscovery.participants } returns MutableStateFlow(emptyList())
        coEvery { buildKeysignMessage(any(), any(), any(), any(), any(), any(), any()) } answers
            {
                buildCalls++
                "qr"
            }

        every { shareViewModel.hasAllData } returns true
        every { shareViewModel.vault } returns vault
        every { shareViewModel.amount } returns MutableStateFlow("")
        every { shareViewModel.toAmount } returns MutableStateFlow("")
        every { shareViewModel.qrBitmapPainter } returns MutableStateFlow(null)
        coEvery { shareViewModel.loadTransaction(any()) } answers {}
        coEvery { shareViewModel.loadSignMessageTx(any()) } answers {}

        // Pinned last: while this static mock is live, mockk records every Dispatchers getter,
        // which would pollute the `coEvery` blocks above.
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
            addressProvider = mockk(relaxed = true),
            context = context,
            navigator = mockk(relaxed = true),
            generateServiceName = generateServiceName,
            transactionStatusServiceManager = mockk(relaxed = true),
            pushNotificationManager = pushNotificationManager,
            snackbarFlow = mockk(relaxed = true),
            keysignViewModelFactory = mockk(relaxed = true),
            sessionCoordinator = mockk(relaxed = true),
            participantDiscovery = participantDiscovery,
            buildKeysignMessage = buildKeysignMessage,
            updateSolanaKeysignPayload = updateSolanaKeysignPayload,
            buildKeysignTransactionUiModel = mockk(relaxed = true),
            qbtcNetworkCheck = qbtcNetworkCheck,
        )

    @Test
    fun `a QBTC keysign on an outdated network stops before the QR is built`() =
        runTest(testDispatcher) {
            every { shareViewModel.keysignPayload } returns qbtcSend
            coEvery { updateSolanaKeysignPayload(qbtcSend) } returns qbtcSend
            coEvery { qbtcNetworkCheck.isAppOutdated() } answers
                {
                    networkChecks++
                    true
                }

            val vm = createViewModel()
            vm.setData(shareViewModel, context, Route.Keysign.Keysign.TxType.Send)
            runCurrent()

            vm.currentState.value shouldBe
                KeysignFlowState.Error(UiText.StringResource(R.string.qbtc_update_required_detail))
            vm.keysignMessage.value shouldBe ""
            networkChecks shouldBe 1
            buildCalls shouldBe 0
            pushCalls shouldBe 0
        }

    @Test
    fun `a keysign for another chain never queries the QBTC node`() =
        runTest(testDispatcher) {
            every { shareViewModel.keysignPayload } returns null
            every { shareViewModel.customMessagePayload } returns
                CustomMessagePayload(
                    method = "personal_sign",
                    message = "hello",
                    chain = Chain.Ethereum.raw,
                )
            coEvery { updateSolanaKeysignPayload(null) } returns null
            coEvery { qbtcNetworkCheck.isAppOutdated() } answers
                {
                    networkChecks++
                    false
                }

            val vm = createViewModel()
            vm.setData(shareViewModel, context, Route.Keysign.Keysign.TxType.Sign)
            runCurrent()

            vm.keysignMessage.value shouldEndWith "jsonData=qr"
            networkChecks shouldBe 0
        }
}
