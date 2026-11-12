@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.blockchain.utxo.UtxoFeeService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.usecases.ConvertGweiToWeiUseCase
import com.vultisig.wallet.data.usecases.ConvertWeiToGweiUseCase
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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
    fun `with no tier picked, the sheet saves exactly what it did before tiers existed`() =
        runTest {
            coEvery { evmApi.getFeeHistory() } returns FEE_HISTORY
            val vm = viewModel()

            vm.loadData(
                Chain.Ethereum,
                ethSpec(priorityFeeWei = BigInteger("1500000000"), gasLimit = BigInteger("21000")),
                saved = null,
            )
            advanceUntilIdle()

            vm.state.value.selectedPriority.shouldBeNull()
            vm.save() shouldBe
                GasSettings.Eth(
                    baseFee = BigInteger("3000000000"),
                    priorityFee = BigInteger("1500000000"),
                    gasLimit = BigInteger("21000"),
                )
        }

    @Test
    fun `picking a tier fills the priority fee field and Save signs what it shows`() = runTest {
        coEvery { evmApi.getFeeHistory() } returns FEE_HISTORY
        val vm = viewModel()
        vm.loadData(
            Chain.Ethereum,
            ethSpec(priorityFeeWei = BigInteger("1500000000")),
            saved = null,
        )
        advanceUntilIdle()

        vm.selectPriority(PriorityFee.FAST)

        vm.priorityFeeState.text.toString() shouldBe "4"
        vm.state.value.selectedPriority shouldBe PriorityFee.FAST
        val saved = vm.save() as GasSettings.Eth
        saved.priorityFee shouldBe BigInteger("4000000000")
        saved.baseFee shouldBe BigInteger("3000000000")
        saved.gasLimit shouldBe BigInteger("21000")
    }

    @Test
    fun `editing the field a tier filled deselects the tier`() = runTest {
        coEvery { evmApi.getFeeHistory() } returns FEE_HISTORY
        val vm = viewModel()
        vm.loadData(
            Chain.Ethereum,
            ethSpec(priorityFeeWei = BigInteger("1500000000")),
            saved = null,
        )
        advanceUntilIdle()
        vm.selectPriority(PriorityFee.LOW)

        vm.priorityFeeState.setTextAndPlaceCursorAtEnd("1.25")
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        vm.state.value.selectedPriority.shouldBeNull()
        (vm.save() as GasSettings.Eth).priorityFee shouldBe BigInteger("1250000000")
    }

    @Test
    fun `tiers stay unavailable when the fee history fails, and picking does nothing`() = runTest {
        coEvery { evmApi.getFeeHistory() } throws IllegalStateException("rpc down")
        val vm = viewModel()
        vm.loadData(
            Chain.Ethereum,
            ethSpec(priorityFeeWei = BigInteger("1500000000")),
            saved = null,
        )
        advanceUntilIdle()

        vm.state.value.showsPriority shouldBe true
        vm.state.value.priorityTiers.shouldBeNull()
        vm.selectPriority(PriorityFee.FAST)
        vm.priorityFeeState.text.toString() shouldBe "1.5"
        vm.state.value.selectedPriority.shouldBeNull()
    }

    @Test
    fun `a legacy-gas chain offers no tiers`() = runTest {
        coEvery { evmApi.getGasPrice() } returns BigInteger("5000000000")
        val vm = viewModel()

        vm.loadData(Chain.BscChain, ethSpec(priorityFeeWei = BigInteger.ZERO), saved = null)
        advanceUntilIdle()

        vm.state.value.showsPriority shouldBe false
    }

    @Test
    fun `reopening shows the saved values and marks the tier they still equal`() = runTest {
        coEvery { evmApi.getFeeHistory() } returns FEE_HISTORY
        val saved =
            GasSettings.Eth(
                baseFee = BigInteger("2500000000"),
                priorityFee = BigInteger("2500000000"),
                gasLimit = BigInteger("30000"),
            )
        val vm = viewModel()

        vm.loadData(Chain.Ethereum, ethSpec(priorityFeeWei = BigInteger("1500000000")), saved)
        advanceUntilIdle()

        vm.baseFeeState.text.toString() shouldBe "2.5"
        vm.priorityFeeState.text.toString() shouldBe "2.5"
        vm.gasLimitState.text.toString() shouldBe "30000"
        vm.state.value.selectedPriority shouldBe PriorityFee.NORMAL
        vm.save() shouldBe saved
    }

    @Test
    fun `reopening with saved values that match no tier marks none`() = runTest {
        coEvery { evmApi.getFeeHistory() } returns FEE_HISTORY
        val saved =
            GasSettings.Eth(
                baseFee = BigInteger("2500000000"),
                priorityFee = BigInteger("2600000000"),
                gasLimit = BigInteger("30000"),
            )
        val vm = viewModel()

        vm.loadData(Chain.Ethereum, ethSpec(priorityFeeWei = BigInteger("1500000000")), saved)
        advanceUntilIdle()

        vm.state.value.selectedPriority.shouldBeNull()
    }

    @Test
    fun `UTXO tiers scale today's byte fee and Save signs the picked one`() = runTest {
        coEvery { utxoFeeService.getDefaultGasFee(Chain.Bitcoin) } returns BigInteger("30")
        val vm = viewModel()

        vm.loadData(Chain.Bitcoin, utxoSpec(), saved = null)
        advanceUntilIdle()
        vm.byteFeeState.text.toString() shouldBe "30"
        vm.state.value.selectedPriority.shouldBeNull()

        vm.selectPriority(PriorityFee.LOW)

        vm.byteFeeState.text.toString() shouldBe "22"
        vm.save() shouldBe GasSettings.UTXO(byteFee = BigInteger("22"))
    }

    @Test
    fun `UTXO with no tier picked saves today's byte fee`() = runTest {
        coEvery { utxoFeeService.getDefaultGasFee(Chain.Bitcoin) } returns BigInteger("30")
        val vm = viewModel()

        vm.loadData(Chain.Bitcoin, utxoSpec(), saved = null)
        advanceUntilIdle()

        vm.save() shouldBe GasSettings.UTXO(byteFee = BigInteger("30"))
    }

    @Test
    fun `a failed Ethereum fee fetch does not keep a later UTXO sheet disabled`() = runTest {
        coEvery { evmApi.getBaseFee() } throws RuntimeException("network error")
        coEvery { utxoFeeService.getDefaultGasFee(Chain.Bitcoin) } returns BigInteger("30")
        val vm = viewModel()
        vm.loadData(
            Chain.Ethereum,
            ethSpec(priorityFeeWei = BigInteger("1000000000")),
            saved = null,
        )
        advanceUntilIdle()
        vm.state.value.isLoadingEthFee shouldBe true

        vm.loadData(Chain.Bitcoin, utxoSpec(), saved = null)
        advanceUntilIdle()

        vm.state.value.isLoadingEthFee shouldBe false
        vm.state.value.priorityTiers.shouldNotBeNull()
    }

    private fun utxoSpec() =
        BlockChainSpecificAndUtxo(
            blockChainSpecific =
                BlockChainSpecific.UTXO(byteFee = BigInteger("30"), sendMaxAmount = false)
        )

    @Test
    fun `loads stored wei priority fee into the field as gwei`() = runTest {
        val vm = viewModel()

        vm.loadData(
            Chain.Ethereum,
            ethSpec(priorityFeeWei = BigInteger("1000000000")),
            saved = null,
        )
        advanceUntilIdle()

        assertEquals("1", vm.priorityFeeState.text.toString())
    }

    @Test
    fun `saves gwei priority fee input back as wei`() = runTest {
        val vm = viewModel()
        vm.loadData(
            Chain.Ethereum,
            ethSpec(priorityFeeWei = BigInteger("1000000000")),
            saved = null,
        )
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
            saved = null,
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
        vm.loadData(
            Chain.Ethereum,
            ethSpec(priorityFeeWei = BigInteger("1000000000")),
            saved = null,
        )
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
            saved = null,
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
            vm.loadData(
                Chain.Ethereum,
                ethSpec(priorityFeeWei = BigInteger("1000000000")),
                saved = null,
            )
            advanceUntilIdle()

            vm.baseFeeState.setTextAndPlaceCursorAtEnd("-5")
            val settings = vm.save() as GasSettings.Eth

            assertEquals(BigInteger.ZERO, settings.baseFee)
            assertEquals(settings.priorityFee, settings.maxFeePerGasWei)
        }

    @Test
    fun `negative priority fee input clamps to zero`() = runTest {
        val vm = viewModel()
        vm.loadData(
            Chain.Ethereum,
            ethSpec(priorityFeeWei = BigInteger("1000000000")),
            saved = null,
        )
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

            vm.loadData(
                Chain.BscChain,
                ethSpec(priorityFeeWei = BigInteger("1000000000")),
                saved = null,
            )
            advanceUntilIdle()

            // Not the mocked 3 gwei getBaseFee() — BSC's base fee is meaningless (BEP-226).
            assertEquals("5", vm.baseFeeState.text.toString())
            assertEquals("0", vm.priorityFeeState.text.toString())
        }

    @Test
    fun `legacy gas chain save sums to the entered gas price alone`() = runTest {
        coEvery { evmApi.getGasPrice() } returns BigInteger("5000000000")
        val vm = viewModel()
        vm.loadData(
            Chain.BscChain,
            ethSpec(priorityFeeWei = BigInteger("1000000000")),
            saved = null,
        )
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
        vm.loadData(
            Chain.Ethereum,
            ethSpec(priorityFeeWei = BigInteger("1000000000")),
            saved = null,
        )
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

        vm.loadData(
            Chain.BscChain,
            ethSpec(priorityFeeWei = BigInteger("1000000000")),
            saved = null,
        )
    }

    @Test
    fun `isLoadingEthFee clears once the fetch resolves`() = runTest {
        val vm = viewModel()

        vm.loadData(
            Chain.Ethereum,
            ethSpec(priorityFeeWei = BigInteger("1000000000")),
            saved = null,
        )
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

        vm.loadData(
            Chain.Ethereum,
            ethSpec(priorityFeeWei = BigInteger("1000000000")),
            saved = null,
        )
        advanceUntilIdle()

        assertEquals(true, vm.state.value.isLoadingEthFee)
    }

    // Legacy-gas sibling of the test above (issue #5399): eth_gasPrice failing must hit the same
    // isLoadingEthFee gate, not silently populate the BSC price field with a fake zero.
    @Test
    fun `legacy gas chain keeps isLoadingEthFee true when eth_gasPrice fails`() = runTest {
        coEvery { evmApi.getGasPrice() } throws RuntimeException("network error")
        val vm = viewModel()

        vm.loadData(
            Chain.BscChain,
            ethSpec(priorityFeeWei = BigInteger("1000000000")),
            saved = null,
        )
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

    private companion object {
        // Ascending, as getFeeHistory returns it: Low 1 gwei, Normal 2.5 gwei, Fast 4 gwei.
        val FEE_HISTORY =
            listOf(
                BigInteger("1000000000"),
                BigInteger("2000000000"),
                BigInteger("3000000000"),
                BigInteger("4000000000"),
            )
    }
}
