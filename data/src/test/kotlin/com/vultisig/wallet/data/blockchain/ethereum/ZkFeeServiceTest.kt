@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.blockchain.ethereum

import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.models.ZkGasFee
import com.vultisig.wallet.data.blockchain.model.Eip1559
import com.vultisig.wallet.data.blockchain.model.Swap
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class ZkFeeServiceTest {

    private val evmApi: EvmApi = mockk()
    private val evmApiFactory: EvmApiFactory = mockk()
    private val service = ZkFeeService(evmApiFactory)

    @BeforeEach
    fun setUp() {
        every { evmApiFactory.createEvmApi(any()) } returns evmApi
        coEvery { evmApi.zkEstimateFee(any(), any(), any()) } returns
            ZkGasFee(
                gasLimit = BigInteger("21000"),
                gasPerPubdataLimit = BigInteger.ONE,
                maxFeePerGas = BigInteger("7"),
                maxPriorityFeePerGas = BigInteger("2"),
            )
    }

    @Test
    fun `calculateFees accepts swap transactions`() = runTest {
        val fee = service.calculateFees(swap()) as Eip1559

        assertEquals(BigInteger("21000"), fee.limit)
        assertEquals(BigInteger("7"), fee.maxFeePerGas)
        assertEquals(BigInteger("2"), fee.maxPriorityFeePerGas)
        coVerify(exactly = 1) { evmApi.zkEstimateFee("0xSender", "0xRecipient", "0xffffffff") }
    }

    @Test
    fun `calculateDefaultFees uses the same zk estimate path`() = runTest {
        val fee = service.calculateDefaultFees(transfer()) as Eip1559

        assertEquals(BigInteger("21000"), fee.limit)
        assertEquals(BigInteger("147000"), fee.amount)
        coVerify(exactly = 1) { evmApi.zkEstimateFee("0xSender", "0xRecipient", "0xffffffff") }
    }

    private fun transfer() =
        Transfer(coin = coin(), vault = VAULT, amount = BigInteger.ONE, to = "0xRecipient")

    private fun swap() =
        Swap(
            coin = coin(),
            vault = VAULT,
            amount = BigInteger.ONE,
            to = "0xRecipient",
            callData = "0xdeadbeef",
            approvalData = null,
        )

    private fun coin() =
        Coin(
            chain = Chain.ZkSync,
            ticker = "ETH",
            logo = "",
            address = "0xSender",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    private companion object {
        private val VAULT = VaultData(vaultHexPublicKey = "pub", vaultHexChainCode = "chain")
    }
}
