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
import com.vultisig.wallet.data.usecases.KeysignBroadcastResult
import com.vultisig.wallet.data.usecases.txstatus.TxStatusConfigurationProvider
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import io.kotest.matchers.shouldBe
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

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        txStatusConfigurationProvider = mockk(relaxed = true)
        transactionHistoryRepository = mockk(relaxed = true)
        explorerLinkRepository = mockk(relaxed = true)
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

    // A Solana dApp batch broadcasts several transactions; each one must land in history with its
    // own explorer link, with the first hash staying the primary one driving the done screen
    // (issue #5238).
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
            txStatusPoller = mockk(relaxed = true),
            vaultRepository = mockk(relaxed = true),
            chainAccountAddressRepository = mockk(relaxed = true),
            transactionHistoryRepository = transactionHistoryRepository,
            balanceRepository = mockk(relaxed = true),
            gasFeeToEstimatedFee = mockk(relaxed = true),
            awaitApprovalConfirmation = mockk(relaxed = true),
        )
}
