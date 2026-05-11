@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.CommonTransactionHistoryData
import com.vultisig.wallet.data.models.SendTransactionHistoryData
import com.vultisig.wallet.data.models.SwapTransactionHistoryData
import com.vultisig.wallet.data.models.TransactionHistoryData
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class KeysignViewModelSaveTransactionHistoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val vault = Vault(id = "v1", name = "Test Vault")

    private val swapTxData =
        SwapTransactionHistoryData(
            fromToken = "BTC",
            fromAmount = "1",
            fromChain = "bitcoin",
            fromTokenLogo = "btc",
            toToken = "ETH",
            toAmount = "10",
            toChain = "ethereum",
            toTokenLogo = "eth",
            provider = "thorchain",
            fiatValue = "100",
        )

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

    private lateinit var transactionHistoryRepository: TransactionHistoryRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        transactionHistoryRepository = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(transactionHistoryData: TransactionHistoryData?) =
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
            explorerLinkRepository = mockk(relaxed = true),
            navigator = mockk<Navigator<Destination>>(relaxed = true),
            sessionApi = mockk(relaxed = true),
            encryption = mockk(relaxed = true),
            featureFlagApi = mockk(relaxed = true),
            pullTssMessages = mockk(relaxed = true),
            addressBookRepository = mockk(relaxed = true),
            txStatusConfigurationProvider = mockk(relaxed = true),
            transactionStatusServiceManager = mockk(relaxed = true),
            vaultRepository = mockk(relaxed = true),
            transactionHistoryRepository = transactionHistoryRepository,
            balanceRepository = mockk(relaxed = true),
            gasFeeToEstimatedFee = mockk(relaxed = true),
        )

    @Test
    fun `swap tx prefers swapProgressLink over txLink for explorerUrl`() =
        runTest(testDispatcher) {
            val vm = createViewModel(swapTxData)
            vm.txLink.value = "https://blockstream.info/tx/0xabc"
            vm.swapProgressLink.value = "https://thorchain.net/tx/0xabc"

            val captured = slot<CommonTransactionHistoryData>()
            vm.saveTransactionHistory(txHash = "0xabc", chain = Chain.Bitcoin)
            advanceUntilIdle()

            coVerify {
                transactionHistoryRepository.recordTransaction(
                    vaultId = "v1",
                    txHash = "0xabc",
                    txData = swapTxData,
                    genericData = capture(captured),
                )
            }
            assertEquals("https://thorchain.net/tx/0xabc", captured.captured.explorerUrl)
        }

    @Test
    fun `swap tx falls back to txLink when swapProgressLink is null`() =
        runTest(testDispatcher) {
            val vm = createViewModel(swapTxData)
            vm.txLink.value = "https://blockstream.info/tx/0xabc"
            vm.swapProgressLink.value = null

            val captured = slot<CommonTransactionHistoryData>()
            vm.saveTransactionHistory(txHash = "0xabc", chain = Chain.Bitcoin)
            advanceUntilIdle()

            coVerify {
                transactionHistoryRepository.recordTransaction(
                    vaultId = any(),
                    txHash = any(),
                    txData = any(),
                    genericData = capture(captured),
                )
            }
            assertEquals("https://blockstream.info/tx/0xabc", captured.captured.explorerUrl)
        }

    @Test
    fun `send tx uses txLink when swapProgressLink is null`() =
        runTest(testDispatcher) {
            val vm = createViewModel(sendTxData)
            vm.txLink.value = "https://etherscan.io/tx/0xabc"
            vm.swapProgressLink.value = null

            val captured = slot<CommonTransactionHistoryData>()
            vm.saveTransactionHistory(txHash = "0xabc", chain = Chain.Ethereum)
            advanceUntilIdle()

            coVerify {
                transactionHistoryRepository.recordTransaction(
                    vaultId = any(),
                    txHash = any(),
                    txData = any(),
                    genericData = capture(captured),
                )
            }
            assertEquals("https://etherscan.io/tx/0xabc", captured.captured.explorerUrl)
        }
}
