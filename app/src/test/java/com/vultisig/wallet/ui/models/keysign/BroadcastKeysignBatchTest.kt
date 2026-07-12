@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.chains.helpers.SigningHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.usecases.BroadcastKeysignUseCase
import com.vultisig.wallet.data.usecases.BroadcastTxUseCase
import com.vultisig.wallet.data.usecases.KeysignBroadcastResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.math.BigInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.SignSolana

/**
 * A Solana dApp `signAndSendAllTransactions` batch assembles one signed transaction per raw
 * transaction of the payload; the broadcast tail must submit every one of them in payload order
 * instead of failing after the ceremony (issue #5238).
 */
internal class BroadcastKeysignBatchTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val vault = Vault(id = "v1", name = "Test Vault")

    private val tx1 = SignedTransactionResult(rawTransaction = "raw1", transactionHash = "hash1")
    private val tx2 = SignedTransactionResult(rawTransaction = "raw2", transactionHash = "hash2")
    private val tx3 = SignedTransactionResult(rawTransaction = "raw3", transactionHash = "hash3")

    private val payload =
        KeysignPayload(
            coin =
                Coin(
                    chain = Chain.Solana,
                    ticker = "SOL",
                    logo = "",
                    address = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM",
                    decimal = 9,
                    hexPublicKey = "",
                    priceProviderID = "",
                    contractAddress = "",
                    isNativeToken = true,
                ),
            toAddress = "3xM8c79mk7fvcz5ENZgMbChPJGWZAjFqwdDzZp4R2gHR",
            toAmount = BigInteger.ZERO,
            blockChainSpecific =
                BlockChainSpecific.Solana(
                    recentBlockHash = "",
                    priorityFee = BigInteger.ZERO,
                    priorityLimit = BigInteger.ZERO,
                    fromAddressPubKey = null,
                    toAddressPubKey = null,
                    programId = false,
                ),
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = SigningLibType.GG20,
            wasmExecuteContractPayload = null,
            signSolana = SignSolana(rawTransactions = listOf("AAA=", "BBB=", "CCC=")),
        )

    private lateinit var broadcastTx: BroadcastTxUseCase

    @BeforeEach
    fun setUp() {
        broadcastTx = mockk()
        mockkObject(SigningHelper)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(SigningHelper)
    }

    @Test
    fun `batch broadcasts every signed transaction in payload order`() =
        runTest(testDispatcher) {
            every { SigningHelper.getSignedTransactions(payload, vault, any(), any()) } returns
                listOf(tx1, tx2, tx3)
            coEvery { broadcastTx(Chain.Solana, tx1) } returns "hash1"
            coEvery { broadcastTx(Chain.Solana, tx2) } returns "hash2"
            coEvery { broadcastTx(Chain.Solana, tx3) } returns "hash3"

            val result =
                createUseCase()(
                    vault = vault,
                    payload = payload,
                    signatures = emptyMap(),
                    isInitiatingDevice = false,
                )

            val broadcasted = result.shouldBeInstanceOf<KeysignBroadcastResult.Broadcasted>()
            broadcasted.txHash shouldBe "hash1"
            broadcasted.additionalTxHashes shouldBe listOf("hash2", "hash3")
            coVerifyOrder {
                broadcastTx(Chain.Solana, tx1)
                broadcastTx(Chain.Solana, tx2)
                broadcastTx(Chain.Solana, tx3)
            }
        }

    @Test
    fun `single transaction yields no additional hashes`() =
        runTest(testDispatcher) {
            every { SigningHelper.getSignedTransactions(payload, vault, any(), any()) } returns
                listOf(tx1)
            coEvery { broadcastTx(Chain.Solana, tx1) } returns "hash1"

            val result =
                createUseCase()(
                    vault = vault,
                    payload = payload,
                    signatures = emptyMap(),
                    isInitiatingDevice = false,
                )

            val broadcasted = result.shouldBeInstanceOf<KeysignBroadcastResult.Broadcasted>()
            broadcasted.txHash shouldBe "hash1"
            broadcasted.additionalTxHashes shouldBe emptyList()
        }

    /**
     * Mirrors the extension's sequential fail-fast broadcast: a rejected transaction surfaces as an
     * error instead of being silently skipped, and the remaining transactions are not sent.
     */
    @Test
    fun `failing broadcast mid-batch propagates and skips the remaining transactions`() =
        runTest(testDispatcher) {
            every { SigningHelper.getSignedTransactions(payload, vault, any(), any()) } returns
                listOf(tx1, tx2, tx3)
            coEvery { broadcastTx(Chain.Solana, tx1) } returns "hash1"
            val failure = RuntimeException("Transaction simulation failed")
            coEvery { broadcastTx(Chain.Solana, tx2) } throws failure

            shouldThrow<RuntimeException> {
                    createUseCase()(
                        vault = vault,
                        payload = payload,
                        signatures = emptyMap(),
                        isInitiatingDevice = true,
                    )
                }
                .message shouldBe failure.message

            coVerify(exactly = 1) { broadcastTx(Chain.Solana, tx1) }
            coVerify(exactly = 0) { broadcastTx(Chain.Solana, tx3) }
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
