@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.usecases.BroadcastTxUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
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

internal class KeysignViewModelBroadcastRecoveryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val vault = Vault(id = "v1", name = "Test Vault")

    private val signedTx =
        SignedTransactionResult(rawTransaction = "0xraw", transactionHash = "0xlocal-hash")

    private lateinit var broadcastTx: BroadcastTxUseCase

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        broadcastTx = mockk()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `joined-device broadcast failure falls back to locally computed hash`() =
        runTest(testDispatcher) {
            coEvery { broadcastTx(Chain.Polkadot, signedTx) } throws
                Exception("Error broadcasting transaction: Transaction has a bad signature")

            val vm = createViewModel(isInitiating = false)

            vm.broadcastOrRecover(Chain.Polkadot, signedTx) shouldBe signedTx.transactionHash
            coVerify(exactly = 1) { broadcastTx(Chain.Polkadot, signedTx) }
        }

    @Test
    fun `joined-device propagates failure when the signed tx has no local hash`() =
        runTest(testDispatcher) {
            val signedTxNoHash = signedTx.copy(transactionHash = "")
            coEvery { broadcastTx(Chain.Polkadot, signedTxNoHash) } throws
                Exception("broadcast failed")

            val vm = createViewModel(isInitiating = false)

            shouldThrow<Exception> { vm.broadcastOrRecover(Chain.Polkadot, signedTxNoHash) }
        }

    @Test
    fun `initiator broadcast failure is never recovered`() =
        runTest(testDispatcher) {
            val failure =
                Exception("Error broadcasting transaction: Transaction has a bad signature")
            coEvery { broadcastTx(Chain.Polkadot, signedTx) } throws failure

            val vm = createViewModel(isInitiating = true)

            shouldThrow<Exception> { vm.broadcastOrRecover(Chain.Polkadot, signedTx) }
                .message shouldBe failure.message
        }

    @Test
    fun `successful broadcast returns the network-provided hash`() =
        runTest(testDispatcher) {
            coEvery { broadcastTx(Chain.Polkadot, signedTx) } returns "0xnetwork-hash"

            val vm = createViewModel(isInitiating = false)

            vm.broadcastOrRecover(Chain.Polkadot, signedTx) shouldBe "0xnetwork-hash"
        }

    private fun createViewModel(isInitiating: Boolean) =
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
            isInitiatingDevice = isInitiating,
            transactionHistoryData = null,
            thorChainApi = mockk(relaxed = true),
            evmApiFactory = mockk(relaxed = true),
            broadcastTx = broadcastTx,
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
            transactionHistoryRepository = mockk(relaxed = true),
            balanceRepository = mockk(relaxed = true),
            gasFeeToEstimatedFee = mockk(relaxed = true),
            awaitApprovalConfirmation = mockk(relaxed = true),
        )
}
