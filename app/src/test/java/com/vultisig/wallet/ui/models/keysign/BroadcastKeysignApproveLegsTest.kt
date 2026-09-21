@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.chains.helpers.SigningHelper
import com.vultisig.wallet.data.chains.helpers.THORChainSwaps
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.ERC20ApprovePayload
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.usecases.ApprovalConfirmationResult
import com.vultisig.wallet.data.usecases.AwaitApprovalConfirmationUseCase
import com.vultisig.wallet.data.usecases.BroadcastKeysignUseCase
import com.vultisig.wallet.data.usecases.BroadcastTxUseCase
import com.vultisig.wallet.data.usecases.KeysignBroadcastResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import java.math.BigInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The approve legs a payload asks for go out in nonce order, each confirmed before the next, and
 * the swap starts at the nonce after the last one. With `resetAllowanceFirst` that is `approve(0)`,
 * `approve(amount)`, swap — a swap broadcast before both legs land would revert on the allowance,
 * and a swap signed at the wrong nonce offset would never be accepted at all.
 */
internal class BroadcastKeysignApproveLegsTest {

    private val vault = Vault(id = "v1", name = "Test Vault")

    private val reset = SignedTransactionResult(rawTransaction = "raw0", transactionHash = "hash0")
    private val approve =
        SignedTransactionResult(rawTransaction = "raw1", transactionHash = "hash1")
    private val swap = SignedTransactionResult(rawTransaction = "raw2", transactionHash = "hash2")

    private val broadcastTx: BroadcastTxUseCase = mockk()
    private val awaitApprovalConfirmation: AwaitApprovalConfirmationUseCase = mockk()
    private val evmApi: EvmApi = mockk()
    private val evmApiFactory: EvmApiFactory = mockk {
        every { createEvmApi(any()) } returns evmApi
    }

    @BeforeEach
    fun setUp() {
        mockkObject(SigningHelper)
        mockkConstructor(THORChainSwaps::class)
        coEvery { evmApi.sendTransaction("raw0") } returns "hash0"
        coEvery { evmApi.sendTransaction("raw1") } returns "hash1"
        coEvery { broadcastTx(Chain.Ethereum, swap) } returns "hash2"
    }

    @AfterEach
    fun tearDown() {
        unmockkConstructor(THORChainSwaps::class)
        unmockkObject(SigningHelper)
    }

    @Test
    fun `broadcasts approve(0) then approve(amount), each confirmed, then the swap two nonces on`() =
        runTest {
            val payload = payload(resetAllowanceFirst = true)
            every {
                anyConstructed<THORChainSwaps>()
                    .getSignedApproveTransactions(payload.approvePayload!!, payload, any())
            } returns listOf(reset, approve)
            coEvery { awaitApprovalConfirmation(Chain.Ethereum, any()) } returns
                ApprovalConfirmationResult.Confirmed
            every {
                SigningHelper.getSignedTransactions(payload, vault, any(), BigInteger.TWO)
            } returns listOf(swap)

            val result =
                createUseCase()(vault, payload, emptyMap(), isInitiatingDevice = true)
                    .shouldBeInstanceOf<KeysignBroadcastResult.Broadcasted>()

            coVerifyOrder {
                evmApi.sendTransaction("raw0")
                awaitApprovalConfirmation(Chain.Ethereum, "hash0")
                evmApi.sendTransaction("raw1")
                awaitApprovalConfirmation(Chain.Ethereum, "hash1")
                broadcastTx(Chain.Ethereum, swap)
            }
            result.txHash shouldBe "hash2"
            // The approval the user is shown is the one that granted the allowance.
            result.approveTxHash shouldBe "hash1"
        }

    @Test
    fun `a single approve leg still moves the swap one nonce on`() = runTest {
        val payload = payload(resetAllowanceFirst = false)
        every {
            anyConstructed<THORChainSwaps>()
                .getSignedApproveTransactions(payload.approvePayload!!, payload, any())
        } returns listOf(approve)
        coEvery { awaitApprovalConfirmation(Chain.Ethereum, "hash1") } returns
            ApprovalConfirmationResult.Confirmed
        every { SigningHelper.getSignedTransactions(payload, vault, any(), BigInteger.ONE) } returns
            listOf(swap)

        val result =
            createUseCase()(vault, payload, emptyMap(), isInitiatingDevice = true)
                .shouldBeInstanceOf<KeysignBroadcastResult.Broadcasted>()

        result.approveTxHash shouldBe "hash1"
        coVerify(exactly = 0) { evmApi.sendTransaction("raw0") }
    }

    // A reset that never lands leaves the allowance non-zero, so approve(amount) would revert for
    // the very reason the reset was sent: stop there, and say which leg stalled.
    @Test
    fun `an unconfirmed reset stops before the approve and the swap`() = runTest {
        val payload = payload(resetAllowanceFirst = true)
        every {
            anyConstructed<THORChainSwaps>()
                .getSignedApproveTransactions(payload.approvePayload!!, payload, any())
        } returns listOf(reset, approve)
        coEvery { awaitApprovalConfirmation(Chain.Ethereum, "hash0") } returns
            ApprovalConfirmationResult.TimedOut

        val result =
            createUseCase()(vault, payload, emptyMap(), isInitiatingDevice = true)
                .shouldBeInstanceOf<KeysignBroadcastResult.ApprovalNotConfirmed>()

        result.approveTxHash shouldBe "hash0"
        result.timedOut shouldBe true
        coVerify(exactly = 0) { evmApi.sendTransaction("raw1") }
        coVerify(exactly = 0) { broadcastTx(any(), any()) }
    }

    private fun payload(resetAllowanceFirst: Boolean) =
        KeysignPayload(
            coin =
                Coin(
                    chain = Chain.Ethereum,
                    ticker = "USDT",
                    logo = "",
                    address = "0x1234567890123456789012345678901234567890",
                    decimal = 6,
                    hexPublicKey = "",
                    priceProviderID = "",
                    contractAddress = "0xdAC17F958D2ee523a2206206994597C13D831ec7",
                    isNativeToken = false,
                ),
            toAddress = "0x111111125421ca6dc452d289314280a0f8842a65",
            toAmount = BigInteger.valueOf(5_000_000),
            blockChainSpecific =
                BlockChainSpecific.Ethereum(
                    maxFeePerGasWei = BigInteger.ONE,
                    priorityFeeWei = BigInteger.ONE,
                    nonce = BigInteger.valueOf(7),
                    gasLimit = BigInteger.valueOf(210_000),
                ),
            approvePayload =
                ERC20ApprovePayload(
                    amount = BigInteger.valueOf(5_000_000),
                    spender = "0x111111125421ca6dc452d289314280a0f8842a65",
                    resetAllowanceFirst = resetAllowanceFirst,
                ),
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = SigningLibType.GG20,
            wasmExecuteContractPayload = null,
        )

    private fun createUseCase() =
        BroadcastKeysignUseCase(
            broadcastTx = broadcastTx,
            awaitApprovalConfirmation = awaitApprovalConfirmation,
            explorerLinkRepository = mockk(relaxed = true),
            evmApiFactory = evmApiFactory,
            balanceRepository = mockk(relaxed = true),
            utxoInFlightRepository = mockk(relaxed = true),
        )
}
