@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.blockchain.tron

import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.models.TronAccountJson
import com.vultisig.wallet.data.api.models.TronAccountResourceJson
import com.vultisig.wallet.data.api.models.TronChainParameterJson
import com.vultisig.wallet.data.api.models.TronChainParametersJson
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class TronFeeServiceTest {

    private val tronApi: TronApi = mockk(relaxed = true)
    private val service = TronFeeService(tronApi)

    @Test
    fun `concurrent fee calculations fetch chain parameters once`() = runTest {
        // Hold every caller at the chain-parameter fetch until they have all arrived, so the
        // singleton's shared cache is exercised under genuine concurrency.
        val gate = CompletableDeferred<TronChainParametersJson>()
        coEvery { tronApi.getChainParameters() } coAnswers { gate.await() }
        coEvery { tronApi.getAccountResource(any()) } returns accountResource()
        coEvery { tronApi.getAccount(any()) } returns existingAccount()

        val calls = List(8) { async { service.calculateFees(nativeTransfer()) } }
        advanceUntilIdle()
        gate.complete(chainParameters())
        advanceUntilIdle()
        calls.awaitAll()

        coVerify(exactly = 1) { tronApi.getChainParameters() }
    }

    @Test
    fun `cached chain parameters are reused across sequential fee calculations`() = runTest {
        coEvery { tronApi.getChainParameters() } returns chainParameters()
        coEvery { tronApi.getAccountResource(any()) } returns accountResource()
        coEvery { tronApi.getAccount(any()) } returns existingAccount()

        service.calculateFees(nativeTransfer())
        service.calculateFees(nativeTransfer())

        coVerify(exactly = 1) { tronApi.getChainParameters() }
    }

    @Test
    fun `freeze and unfreeze are not charged the memo fee their contract never carries`() =
        runTest {
            stubHealthyApi()

            for (operation in TronStakingOperation.entries) {
                for (resource in TronResourceType.entries) {
                    val memo = tronStakingMemo(operation, resource)
                    val fees = service.calculateFees(stakingTransfer(memo))

                    assertEquals(
                        BigInteger.ZERO,
                        fees.amount,
                        "$memo must cost nothing beyond the free bandwidth quota",
                    )
                }
            }
        }

    @Test
    fun `a genuine memo on a native transfer still costs the memo fee`() = runTest {
        stubHealthyApi()

        val fees = service.calculateFees(nativeTransfer(memo = "thanks for lunch"))

        assertEquals(MEMO_FEE, fees.amount)
    }

    @Test
    fun `default fees classify the staking memo the same way the live estimate does`() = runTest {
        // FeeServiceComposite falls back to calculateDefaultFees whenever TronGrid errors, so a
        // degraded network must not resurrect the memo fee the live path just dropped.
        coEvery { tronApi.getAccount(any()) } returns existingAccount()

        val staking =
            service.calculateDefaultFees(
                stakingTransfer(
                    tronStakingMemo(TronStakingOperation.FREEZE, TronResourceType.ENERGY)
                )
            )
        val memoed = service.calculateDefaultFees(nativeTransfer(memo = "thanks for lunch"))

        assertEquals(MEMO_FEE, memoed.amount - staking.amount)
    }

    private fun stubHealthyApi() {
        coEvery { tronApi.getChainParameters() } returns chainParameters()
        // Free bandwidth covers the whole transfer and the destination is already activated, so the
        // memo fee is the only term left that can move the total off zero.
        coEvery { tronApi.getAccountResource(any()) } returns accountResource()
        coEvery { tronApi.getAccount(any()) } returns existingAccount()
    }

    private fun stakingTransfer(memo: String) = nativeTransfer(to = SENDER, memo = memo)

    private fun nativeTransfer(to: String = RECIPIENT, memo: String? = null) =
        Transfer(
            coin =
                Coin(
                    chain = Chain.Tron,
                    ticker = "TRX",
                    logo = "",
                    address = SENDER,
                    decimal = 6,
                    hexPublicKey = "pub",
                    priceProviderID = "",
                    contractAddress = "",
                    isNativeToken = true,
                ),
            vault = VaultData(vaultHexPublicKey = "pub", vaultHexChainCode = "chain"),
            amount = BigInteger("1000000"),
            to = to,
            memo = memo,
        )

    private fun chainParameters() =
        TronChainParametersJson(
            listOf(
                TronChainParameterJson("getTransactionFee", 1000L),
                TronChainParameterJson("getCreateAccountFee", 100000L),
                TronChainParameterJson("getCreateNewAccountFeeInSystemContract", 1000000L),
                TronChainParameterJson("getMemoFee", 1000000L),
                TronChainParameterJson("getEnergyFee", 280L),
                TronChainParameterJson("getDynamicEnergyMaxFactor", 1200L),
            )
        )

    private fun accountResource() = TronAccountResourceJson(freeNetLimit = 5000L)

    private fun existingAccount() = TronAccountJson(address = RECIPIENT)

    private companion object {
        const val SENDER = "TSenderAddressBase58"
        const val RECIPIENT = "TRecipientAddressBase58"
        val MEMO_FEE: BigInteger = BigInteger.valueOf(1_000_000L)
    }
}
