@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.errors.CosmosBroadcastException
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class BroadcastKeysignUseCaseTest {

    @Test
    fun `returns broadcast hash on success`() = runTest {
        val useCase = createUseCase {
            coEvery { it(Chain.Ethereum, signedTx()) } returns BROADCAST_HASH
        }

        val result =
            useCase.broadcastOrRecover(Chain.Ethereum, signedTx(), isInitiatingDevice = false)

        assertEquals(BROADCAST_HASH, result)
    }

    @Test
    fun `joined device recovers with local hash on cosmos sequence mismatch`() = runTest {
        val useCase = createUseCase {
            coEvery { it(Chain.GaiaChain, signedTx()) } throws sequenceMismatch()
        }

        val result =
            useCase.broadcastOrRecover(Chain.GaiaChain, signedTx(), isInitiatingDevice = false)

        assertEquals(KNOWN_HASH, result)
    }

    @Test
    fun `joined device rethrows non-sequence-mismatch cosmos rejection`() = runTest {
        val useCase = createUseCase {
            coEvery { it(Chain.GaiaChain, signedTx()) } throws insufficientFunds()
        }

        assertFailsWith<CosmosBroadcastException> {
            useCase.broadcastOrRecover(Chain.GaiaChain, signedTx(), isInitiatingDevice = false)
        }
    }

    @Test
    fun `joined device rethrows generic evm broadcast failure`() = runTest {
        val error = RuntimeException("insufficient funds for gas * price + value")
        val useCase = createUseCase { coEvery { it(Chain.Ethereum, signedTx()) } throws error }

        val thrown =
            assertFailsWith<RuntimeException> {
                useCase.broadcastOrRecover(Chain.Ethereum, signedTx(), isInitiatingDevice = false)
            }
        assertEquals(error, thrown)
    }

    @Test
    fun `initiating device never recovers even on sequence mismatch`() = runTest {
        val useCase = createUseCase {
            coEvery { it(Chain.GaiaChain, signedTx()) } throws sequenceMismatch()
        }

        assertFailsWith<CosmosBroadcastException> {
            useCase.broadcastOrRecover(Chain.GaiaChain, signedTx(), isInitiatingDevice = true)
        }
    }

    @Test
    fun `does not recover when local hash is blank`() = runTest {
        val useCase = createUseCase {
            coEvery { it(Chain.GaiaChain, signedTx(hash = "")) } throws sequenceMismatch()
        }

        assertFailsWith<CosmosBroadcastException> {
            useCase.broadcastOrRecover(
                Chain.GaiaChain,
                signedTx(hash = ""),
                isInitiatingDevice = false,
            )
        }
    }

    @Test
    fun `cancellation propagates without recovery`() = runTest {
        val useCase = createUseCase {
            coEvery { it(Chain.GaiaChain, signedTx()) } throws CancellationException()
        }

        assertFailsWith<CancellationException> {
            useCase.broadcastOrRecover(Chain.GaiaChain, signedTx(), isInitiatingDevice = false)
        }
    }

    private fun createUseCase(
        configureBroadcast: (BroadcastTxUseCase) -> Unit
    ): BroadcastKeysignUseCase {
        val broadcastTx = mockk<BroadcastTxUseCase>().also(configureBroadcast)
        return BroadcastKeysignUseCase(
            broadcastTx = broadcastTx,
            awaitApprovalConfirmation = mockk(),
            explorerLinkRepository = mockk<ExplorerLinkRepository>(),
            evmApiFactory = mockk<EvmApiFactory>(),
            balanceRepository = mockk<BalanceRepository>(),
        )
    }

    private fun signedTx(hash: String = KNOWN_HASH) =
        SignedTransactionResult(rawTransaction = RAW_TRANSACTION, transactionHash = hash)

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

    private companion object {
        const val RAW_TRANSACTION = "signed-transaction"
        const val KNOWN_HASH = "known-transaction-hash"
        const val BROADCAST_HASH = "broadcast-hash"
    }
}
