@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.api.errors.CosmosBroadcastException
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.usecases.BroadcastKeysignUseCase
import com.vultisig.wallet.data.usecases.BroadcastTxUseCase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.coroutines.cancellation.CancellationException
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
    fun `joined-device recovers with local hash on cosmos sequence mismatch`() =
        runTest(testDispatcher) {
            coEvery { broadcastTx(Chain.GaiaChain, signedTx) } throws sequenceMismatch()

            createUseCase()
                .broadcastOrRecover(Chain.GaiaChain, signedTx, isInitiatingDevice = false) shouldBe
                signedTx.transactionHash
            coVerify(exactly = 1) { broadcastTx(Chain.GaiaChain, signedTx) }
        }

    @Test
    fun `joined-device re-throws a non-duplicate broadcast failure`() =
        runTest(testDispatcher) {
            // A Polkadot "bad signature" that reaches this layer means the inner on-chain verify
            // already proved the extrinsic is not on chain, so it is a genuine failure.
            val failure =
                Exception("Error broadcasting transaction: Transaction has a bad signature")
            coEvery { broadcastTx(Chain.Polkadot, signedTx) } throws failure

            shouldThrow<Exception> {
                    createUseCase()
                        .broadcastOrRecover(Chain.Polkadot, signedTx, isInitiatingDevice = false)
                }
                .message shouldBe failure.message
        }

    @Test
    fun `joined-device re-throws a non-sequence-mismatch cosmos rejection`() =
        runTest(testDispatcher) {
            coEvery { broadcastTx(Chain.GaiaChain, signedTx) } throws insufficientFunds()

            shouldThrow<CosmosBroadcastException> {
                createUseCase()
                    .broadcastOrRecover(Chain.GaiaChain, signedTx, isInitiatingDevice = false)
            }
        }

    @Test
    fun `joined-device re-throws a generic evm broadcast failure`() =
        runTest(testDispatcher) {
            val failure = RuntimeException("insufficient funds for gas * price + value")
            coEvery { broadcastTx(Chain.Ethereum, signedTx) } throws failure

            shouldThrow<RuntimeException> {
                    createUseCase()
                        .broadcastOrRecover(Chain.Ethereum, signedTx, isInitiatingDevice = false)
                }
                .message shouldBe failure.message
        }

    @Test
    fun `joined-device does not recover when the signed tx has no local hash`() =
        runTest(testDispatcher) {
            val signedTxNoHash = signedTx.copy(transactionHash = "")
            coEvery { broadcastTx(Chain.GaiaChain, signedTxNoHash) } throws sequenceMismatch()

            shouldThrow<CosmosBroadcastException> {
                createUseCase()
                    .broadcastOrRecover(Chain.GaiaChain, signedTxNoHash, isInitiatingDevice = false)
            }
        }

    @Test
    fun `initiator broadcast failure is never recovered`() =
        runTest(testDispatcher) {
            val failure = sequenceMismatch()
            coEvery { broadcastTx(Chain.GaiaChain, signedTx) } throws failure

            shouldThrow<CosmosBroadcastException> {
                createUseCase()
                    .broadcastOrRecover(Chain.GaiaChain, signedTx, isInitiatingDevice = true)
            }
        }

    @Test
    fun `cancellation propagates without recovery`() =
        runTest(testDispatcher) {
            coEvery { broadcastTx(Chain.GaiaChain, signedTx) } throws CancellationException()

            shouldThrow<CancellationException> {
                createUseCase()
                    .broadcastOrRecover(Chain.GaiaChain, signedTx, isInitiatingDevice = false)
            }
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

    private fun sequenceMismatch() =
        CosmosBroadcastException.from(
            code = 32,
            codespace = "sdk",
            rawLog = "account sequence mismatch, expected 5, got 4",
            txHash = null,
        )

    private fun insufficientFunds() =
        CosmosBroadcastException.from(
            code = 5,
            codespace = "sdk",
            rawLog = "insufficient funds",
            txHash = null,
        )
}
