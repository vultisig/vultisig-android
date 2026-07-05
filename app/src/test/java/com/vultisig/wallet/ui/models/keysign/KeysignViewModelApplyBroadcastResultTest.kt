@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.usecases.KeysignBroadcastResult
import com.vultisig.wallet.data.usecases.txstatus.TxStatusConfigurationProvider
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import io.kotest.matchers.shouldBe
import io.mockk.every
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

internal class KeysignViewModelApplyBroadcastResultTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val vault = Vault(id = "v1", name = "Test Vault")

    private lateinit var txStatusConfigurationProvider: TxStatusConfigurationProvider

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        txStatusConfigurationProvider = mockk(relaxed = true)
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

    private fun broadcasted(txHash: String?) =
        KeysignBroadcastResult.Broadcasted(
            chain = Chain.Ethereum,
            txHash = txHash,
            txLink = if (txHash != null) "https://etherscan.io/tx/$txHash" else "",
            swapProgressLink = null,
            approveTxHash = "",
            approveTxLink = "",
        )

    private fun createViewModel() =
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
            txStatusConfigurationProvider = txStatusConfigurationProvider,
            txStatusPoller = mockk(relaxed = true),
            vaultRepository = mockk(relaxed = true),
            transactionHistoryRepository = mockk(relaxed = true),
            balanceRepository = mockk(relaxed = true),
            gasFeeToEstimatedFee = mockk(relaxed = true),
            awaitApprovalConfirmation = mockk(relaxed = true),
        )
}
