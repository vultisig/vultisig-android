@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.usecases.BroadcastKeysignUseCase
import com.vultisig.wallet.data.usecases.BroadcastTxUseCase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class KeysignViewModelBroadcastRecoveryTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val signedTx =
        SignedTransactionResult(rawTransaction = "0xraw", transactionHash = "0xlocal-hash")

    private lateinit var broadcastTx: BroadcastTxUseCase

    @BeforeEach
    fun setUp() {
        broadcastTx = mockk()
    }

    @Test
    fun `joined-device broadcast failure falls back to locally computed hash`() =
        runTest(testDispatcher) {
            coEvery { broadcastTx(Chain.Polkadot, signedTx) } throws
                Exception("Error broadcasting transaction: Transaction has a bad signature")

            createUseCase()
                .broadcastOrRecover(Chain.Polkadot, signedTx, isInitiatingDevice = false) shouldBe
                signedTx.transactionHash
            coVerify(exactly = 1) { broadcastTx(Chain.Polkadot, signedTx) }
        }

    @Test
    fun `joined-device propagates failure when the signed tx has no local hash`() =
        runTest(testDispatcher) {
            val signedTxNoHash = signedTx.copy(transactionHash = "")
            coEvery { broadcastTx(Chain.Polkadot, signedTxNoHash) } throws
                Exception("broadcast failed")

            shouldThrow<Exception> {
                createUseCase()
                    .broadcastOrRecover(Chain.Polkadot, signedTxNoHash, isInitiatingDevice = false)
            }
        }

    @Test
    fun `initiator broadcast failure is never recovered`() =
        runTest(testDispatcher) {
            val failure =
                Exception("Error broadcasting transaction: Transaction has a bad signature")
            coEvery { broadcastTx(Chain.Polkadot, signedTx) } throws failure

            shouldThrow<Exception> {
                    createUseCase()
                        .broadcastOrRecover(Chain.Polkadot, signedTx, isInitiatingDevice = true)
                }
                .message shouldBe failure.message
        }

    @Test
    fun `successful broadcast returns the network-provided hash`() =
        runTest(testDispatcher) {
            coEvery { broadcastTx(Chain.Polkadot, signedTx) } returns "0xnetwork-hash"

            createUseCase()
                .broadcastOrRecover(Chain.Polkadot, signedTx, isInitiatingDevice = false) shouldBe
                "0xnetwork-hash"
        }

    private fun createUseCase() =
        BroadcastKeysignUseCase(
            broadcastTx = broadcastTx,
            awaitApprovalConfirmation = mockk(relaxed = true),
            explorerLinkRepository = mockk(relaxed = true),
            evmApiFactory = mockk(relaxed = true),
            balanceRepository = mockk(relaxed = true),
        )
}
