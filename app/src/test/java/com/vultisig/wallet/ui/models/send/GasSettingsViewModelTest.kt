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
import org.junit.jupiter.api.assertThrows

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

    @Test
    fun `maxFeePerGasWei sums base and priority fee`() = runTest {
        val vm = viewModel()
        vm.loadData(
            Chain.Ethereum,
            ethSpec(priorityFeeWei = BigInteger("1500000000")), // 1.5 gwei
        )
        advanceUntilIdle()
        // baseFee mocked to 3 gwei in setUp.

        val settings = vm.save() as GasSettings.Eth

        assertEquals(BigInteger("4500000000"), settings.maxFeePerGasWei)
    }

    @Test
    fun `negative base fee input clamps to zero and no longer drags the sum below priority fee`() =
        runTest {
            val vm = viewModel()
            vm.loadData(Chain.Ethereum, ethSpec(priorityFeeWei = BigInteger("1000000000")))
            advanceUntilIdle()

            vm.baseFeeState.setTextAndPlaceCursorAtEnd("-5")
            val settings = vm.save() as GasSettings.Eth

            assertEquals(BigInteger.ZERO, settings.baseFee)
            assertEquals(settings.priorityFee, settings.maxFeePerGasWei)
        }

    @Test
    fun `negative priority fee input clamps to zero`() = runTest {
        val vm = viewModel()
        vm.loadData(Chain.Ethereum, ethSpec(priorityFeeWei = BigInteger("1000000000")))
        advanceUntilIdle()

        vm.priorityFeeState.setTextAndPlaceCursorAtEnd("-2")
        val settings = vm.save() as GasSettings.Eth

        assertEquals(BigInteger.ZERO, settings.priorityFee)
        assertEquals(settings.baseFee, settings.maxFeePerGasWei)
    }

    @Test
    fun `legacy gas chain sources its single price field from eth_gasPrice, not the base fee`() =
        runTest {
            coEvery { evmApi.getGasPrice() } returns BigInteger("5000000000") // 5 gwei
            val vm = viewModel()

            vm.loadData(Chain.BscChain, ethSpec(priorityFeeWei = BigInteger("1000000000")))
            advanceUntilIdle()

            // Not the mocked 3 gwei getBaseFee() — BSC's base fee is meaningless (BEP-226).
            assertEquals("5", vm.baseFeeState.text.toString())
            assertEquals("0", vm.priorityFeeState.text.toString())
        }

    @Test
    fun `legacy gas chain save sums to the entered gas price alone`() = runTest {
        coEvery { evmApi.getGasPrice() } returns BigInteger("5000000000")
        val vm = viewModel()
        vm.loadData(Chain.BscChain, ethSpec(priorityFeeWei = BigInteger("1000000000")))
        advanceUntilIdle()

        val settings = vm.save() as GasSettings.Eth

        assertEquals(BigInteger("5000000000"), settings.maxFeePerGasWei)
        assertEquals(BigInteger.ZERO, settings.priorityFee)
    }

    /**
     * This ViewModel is Hilt-scoped to the Send screen, not to one dialog open, so a field left
     * over from a prior chain's session survives a close-without-saving. Without clearing the
     * fields up front, a Save tapped for the newly opened chain before this fetch resolves could
     * sign a fee carried over from the previous chain (issue #5397 follow-up).
     */
    @Test
    fun `switching chains blanks stale fields before the new chain's fetch resolves`() = runTest {
        val vm = viewModel()
        vm.loadData(Chain.Ethereum, ethSpec(priorityFeeWei = BigInteger("1000000000")))
        advanceUntilIdle()
        // Left over from the prior (Ethereum) session; the dialog was closed without saving.
        vm.priorityFeeState.setTextAndPlaceCursorAtEnd("9")

        coEvery { evmApi.getGasPrice() } coAnswers
            {
                // By the time the new chain's fetch actually runs, both fields must already be
                // blank — never still holding Ethereum's stale priority fee.
                assertEquals("", vm.baseFeeState.text.toString())
                assertEquals("", vm.priorityFeeState.text.toString())
                BigInteger("5000000000")
            }

        vm.loadData(Chain.BscChain, ethSpec(priorityFeeWei = BigInteger("1000000000")))
    }

    @Test
    fun `isLoadingEthFee clears once the fetch resolves`() = runTest {
        val vm = viewModel()

        vm.loadData(Chain.Ethereum, ethSpec(priorityFeeWei = BigInteger("1000000000")))
        advanceUntilIdle()

        assertEquals(false, vm.state.value.isLoadingEthFee)
    }

    /**
     * CodeRabbit finding on this PR: blanking the fields on chain switch (above) closed the
     * stale-value leak, but a slow or failed fetch left the fields blank with Save still pressable
     * — save() would then read "" as zero, reproducing the original zero-fee bug via a different
     * trigger. isLoadingEthFee must stay true (Save disabled) until a fetch actually succeeds.
     */
    @Test
    fun `isLoadingEthFee stays true when the fetch fails, keeping Save disabled`() = runTest {
        coEvery { evmApi.getBaseFee() } throws RuntimeException("network error")
        val vm = viewModel()

        vm.loadData(Chain.Ethereum, ethSpec(priorityFeeWei = BigInteger("1000000000")))
        advanceUntilIdle()

        assertEquals(true, vm.state.value.isLoadingEthFee)
    }

    // Legacy-gas sibling of the test above (issue #5399): eth_gasPrice failing must hit the same
    // isLoadingEthFee gate, not silently populate the BSC price field with a fake zero.
    @Test
    fun `legacy gas chain keeps isLoadingEthFee true when eth_gasPrice fails`() = runTest {
        coEvery { evmApi.getGasPrice() } throws RuntimeException("network error")
        val vm = viewModel()

        vm.loadData(Chain.BscChain, ethSpec(priorityFeeWei = BigInteger("1000000000")))
        advanceUntilIdle()

        assertEquals(true, vm.state.value.isLoadingEthFee)
        assertEquals("", vm.baseFeeState.text.toString())
    }

    @Test
    fun `GasSettings Eth rejects a negative baseFee or priorityFee`() {
        assertThrows<IllegalArgumentException> {
            GasSettings.Eth(
                baseFee = BigInteger.valueOf(-1),
                priorityFee = BigInteger.ZERO,
                gasLimit = BigInteger("21000"),
            )
        }
        assertThrows<IllegalArgumentException> {
            GasSettings.Eth(
                baseFee = BigInteger.ZERO,
                priorityFee = BigInteger.valueOf(-1),
                gasLimit = BigInteger("21000"),
            )
        }
    }
}
