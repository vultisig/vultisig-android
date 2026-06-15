@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.blockchain.utxo.UtxoFeeService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.usecases.ConvertGweiToWeiUseCase
import com.vultisig.wallet.data.usecases.ConvertWeiToGweiUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class GasSettingsViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val weiPerGwei = BigDecimal(1_000_000_000L)
    private val convertWeiToGwei =
        object : ConvertWeiToGweiUseCase {
            override fun invoke(wei: BigInteger): BigDecimal = wei.toBigDecimal().divide(weiPerGwei)
        }
    private val convertGweiToWei =
        object : ConvertGweiToWeiUseCase {
            override fun invoke(gwei: BigDecimal): BigDecimal = gwei.multiply(weiPerGwei)
        }

    private val evmApi: EvmApi = mockk(relaxed = true)
    private val evmApiFactory: EvmApiFactory = mockk()
    private val utxoFeeService: UtxoFeeService = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        every { evmApiFactory.createEvmApi(any()) } returns evmApi
        coEvery { evmApi.getBaseFee() } returns BigInteger("3000000000")
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        GasSettingsViewModel(
            evmApiFactory = evmApiFactory,
            utxoFeeService = utxoFeeService,
            convertWeiToGwei = convertWeiToGwei,
            convertGweiToWei = convertGweiToWei,
        )

    private fun ethSpec(priorityFeeWei: BigInteger, gasLimit: BigInteger = BigInteger("21000")) =
        BlockChainSpecificAndUtxo(
            blockChainSpecific =
                BlockChainSpecific.Ethereum(
                    maxFeePerGasWei = BigInteger.ZERO,
                    priorityFeeWei = priorityFeeWei,
                    nonce = BigInteger.ZERO,
                    gasLimit = gasLimit,
                )
        )

    @Test
    fun `loads stored wei priority fee into the field as gwei`() = runTest {
        val vm = viewModel()

        vm.loadData(Chain.Ethereum, ethSpec(priorityFeeWei = BigInteger("1000000000")))
        advanceUntilIdle()

        assertEquals("1", vm.priorityFeeState.text.toString())
    }

    @Test
    fun `saves gwei priority fee input back as wei`() = runTest {
        val vm = viewModel()
        vm.loadData(Chain.Ethereum, ethSpec(priorityFeeWei = BigInteger("1000000000")))
        advanceUntilIdle()

        vm.priorityFeeState.setTextAndPlaceCursorAtEnd("2")
        val settings = vm.save() as GasSettings.Eth

        assertEquals(BigInteger("2000000000"), settings.priorityFee)
    }

    @Test
    fun `eth gas settings round-trip through load and save without an edit`() = runTest {
        val vm = viewModel()
        vm.loadData(
            Chain.Ethereum,
            ethSpec(priorityFeeWei = BigInteger("1500000000"), gasLimit = BigInteger("21000")),
        )
        advanceUntilIdle()

        assertEquals("1.5", vm.priorityFeeState.text.toString())

        val settings = vm.save() as GasSettings.Eth
        assertEquals(BigInteger("1500000000"), settings.priorityFee)
        assertEquals(BigInteger("3000000000"), settings.baseFee)
        assertEquals(BigInteger("21000"), settings.gasLimit)
    }

    @Test
    fun `blank priority fee saves as zero`() = runTest {
        val vm = viewModel()
        vm.loadData(Chain.Ethereum, ethSpec(priorityFeeWei = BigInteger("1000000000")))
        advanceUntilIdle()

        vm.priorityFeeState.setTextAndPlaceCursorAtEnd("")
        val settings = vm.save() as GasSettings.Eth

        assertEquals(BigInteger.ZERO, settings.priorityFee)
    }
}
