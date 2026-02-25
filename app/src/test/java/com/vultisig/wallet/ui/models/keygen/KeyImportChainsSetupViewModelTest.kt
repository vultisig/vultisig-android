@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keygen

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshots.Snapshot
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.ChainImportSetting
import com.vultisig.wallet.data.repositories.DerivationPath
import com.vultisig.wallet.data.repositories.KeyImportData
import com.vultisig.wallet.data.repositories.KeyImportRepository
import com.vultisig.wallet.data.usecases.ChainBalanceResult
import com.vultisig.wallet.data.usecases.ScanChainBalancesUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class KeyImportChainsSetupViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private lateinit var navigator: Navigator<Destination>
    private lateinit var keyImportRepository: KeyImportRepository
    private lateinit var scanChainBalances: ScanChainBalancesUseCase

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        navigator = mockk(relaxed = true)
        keyImportRepository = mockk(relaxed = true)
        scanChainBalances = mockk()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = KeyImportChainsSetupViewModel(
        navigator = navigator,
        keyImportRepository = keyImportRepository,
        scanChainBalances = scanChainBalances,
    )

    /**
     * Creates the ViewModel and waits for the init scan to complete.
     * The ViewModel's [startScanning] uses `withContext(Dispatchers.IO)` which
     * dispatches to the real IO thread pool (not replaced by `setMain`).
     * Because the mocked [scanChainBalances] returns instantly, a brief
     * real-time wait lets the IO thread finish before assertions run.
     */
    private fun createViewModelAndAwaitScan(): KeyImportChainsSetupViewModel {
        val vm = createViewModel()
        Thread.sleep(100)
        return vm
    }

    private fun TextFieldState.setTextAndNotify(text: String) {
        edit { replace(0, length, text) }
        Snapshot.sendApplyNotifications()
    }

    private fun setUpMnemonicAndScanResults(
        mnemonic: String = "test mnemonic phrase",
        results: List<ChainBalanceResult> = emptyList(),
    ) {
        every { keyImportRepository.get() } returns KeyImportData(mnemonic = mnemonic)
        coEvery { scanChainBalances(any()) } returns results
    }

    @Test
    fun `no mnemonic falls back to NoActiveChains`() = runTest(mainDispatcher) {
        every { keyImportRepository.get() } returns null
        coEvery { scanChainBalances(any()) } returns emptyList()

        val vm = createViewModelAndAwaitScan()
        advanceUntilIdle()

        assertEquals(ChainsSetupState.NoActiveChains, vm.state.value.screenState)
    }

    @Test
    fun `scanning with active chains transitions to ActiveChains`() = runTest(mainDispatcher) {
        setUpMnemonicAndScanResults(
            results = listOf(
                ChainBalanceResult(
                    chain = Chain.Bitcoin,
                    derivationPath = DerivationPath.Default,
                    address = "bc1addr",
                    hasBalance = true,
                ),
                ChainBalanceResult(
                    chain = Chain.Ethereum,
                    derivationPath = DerivationPath.Default,
                    address = "0xaddr",
                    hasBalance = true,
                ),
                ChainBalanceResult(
                    chain = Chain.Solana,
                    derivationPath = DerivationPath.Default,
                    address = "soladdr",
                    hasBalance = false,
                ),
            )
        )

        val vm = createViewModelAndAwaitScan()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(ChainsSetupState.ActiveChains, state.screenState)
        assertEquals(2, state.activeChains.size)
        assertTrue(state.activeChains.all { it.isSelected })
        assertEquals(2, state.selectedCount)
    }

    @Test
    fun `scanning with no active chains transitions to NoActiveChains`() =
        runTest(mainDispatcher) {
            setUpMnemonicAndScanResults(
                results = Chain.keyImportSupportedChains.map { chain ->
                    ChainBalanceResult(
                        chain = chain,
                        derivationPath = DerivationPath.Default,
                        address = "addr",
                        hasBalance = false,
                    )
                }
            )

            val vm = createViewModelAndAwaitScan()
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(ChainsSetupState.NoActiveChains, state.screenState)
            assertEquals(0, state.selectedCount)
            assertTrue(state.allChains.isNotEmpty())
            assertTrue(state.allChains.none { it.isSelected })
        }

    @Test
    fun `scanning failure falls back to NoActiveChains`() = runTest(mainDispatcher) {
        every { keyImportRepository.get() } returns KeyImportData(mnemonic = "test")
        coEvery { scanChainBalances(any()) } throws RuntimeException("Network error")

        val vm = createViewModelAndAwaitScan()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(ChainsSetupState.NoActiveChains, state.screenState)
        assertTrue(state.allChains.isNotEmpty())
        assertTrue(state.allChains.none { it.isSelected })
    }

    @Test
    fun `scanning preserves derivation path for active chains`() = runTest(mainDispatcher) {
        setUpMnemonicAndScanResults(
            results = listOf(
                ChainBalanceResult(
                    chain = Chain.Solana,
                    derivationPath = DerivationPath.Phantom,
                    address = "phantom_addr",
                    hasBalance = true,
                ),
                ChainBalanceResult(
                    chain = Chain.Solana,
                    derivationPath = DerivationPath.Default,
                    address = "default_addr",
                    hasBalance = false,
                ),
            )
        )

        val vm = createViewModelAndAwaitScan()
        advanceUntilIdle()

        val state = vm.state.value
        val solanaItem = state.allChains.first { it.chain == Chain.Solana }
        assertEquals(DerivationPath.Phantom, solanaItem.derivationPath)
        assertTrue(solanaItem.isSelected)
    }

    @Test
    fun `all supported chains are present in allChains after scan`() = runTest(mainDispatcher) {
        setUpMnemonicAndScanResults()

        val vm = createViewModelAndAwaitScan()
        advanceUntilIdle()

        val chainIds = vm.state.value.allChains.map { it.chain }
        assertEquals(Chain.keyImportSupportedChains, chainIds)
    }

    @Test
    fun `selectManually transitions to CustomizeChains`() = runTest(mainDispatcher) {
        setUpMnemonicAndScanResults()

        val vm = createViewModelAndAwaitScan()
        advanceUntilIdle()

        vm.selectManually()

        assertEquals(ChainsSetupState.CustomizeChains, vm.state.value.screenState)
    }

    @Test
    fun `customize transitions to CustomizeChains`() = runTest(mainDispatcher) {
        setUpMnemonicAndScanResults()

        val vm = createViewModelAndAwaitScan()
        advanceUntilIdle()

        vm.customize()

        assertEquals(ChainsSetupState.CustomizeChains, vm.state.value.screenState)
    }

    @Test
    fun `toggleChain selects an unselected chain`() = runTest(mainDispatcher) {
        setUpMnemonicAndScanResults()

        val vm = createViewModelAndAwaitScan()
        advanceUntilIdle()

        assertFalse(vm.state.value.allChains.first { it.chain == Chain.Bitcoin }.isSelected)

        vm.toggleChain(Chain.Bitcoin)

        assertTrue(vm.state.value.allChains.first { it.chain == Chain.Bitcoin }.isSelected)
        assertEquals(1, vm.state.value.selectedCount)
    }

    @Test
    fun `toggleChain deselects a selected chain`() = runTest(mainDispatcher) {
        setUpMnemonicAndScanResults(
            results = listOf(
                ChainBalanceResult(
                    chain = Chain.Bitcoin,
                    derivationPath = DerivationPath.Default,
                    address = "addr",
                    hasBalance = true,
                ),
            )
        )

        val vm = createViewModelAndAwaitScan()
        advanceUntilIdle()

        vm.selectManually()
        assertTrue(vm.state.value.allChains.first { it.chain == Chain.Bitcoin }.isSelected)

        vm.toggleChain(Chain.Bitcoin)

        assertFalse(vm.state.value.allChains.first { it.chain == Chain.Bitcoin }.isSelected)
    }

    @Test
    fun `toggleChain updates selectedCount correctly`() = runTest(mainDispatcher) {
        setUpMnemonicAndScanResults()

        val vm = createViewModelAndAwaitScan()
        advanceUntilIdle()

        assertEquals(0, vm.state.value.selectedCount)

        vm.toggleChain(Chain.Bitcoin)
        assertEquals(1, vm.state.value.selectedCount)

        vm.toggleChain(Chain.Ethereum)
        assertEquals(2, vm.state.value.selectedCount)

        vm.toggleChain(Chain.Bitcoin)
        assertEquals(1, vm.state.value.selectedCount)
    }

    @Test
    fun `toggleChain updates filteredChains`() = runTest(mainDispatcher) {
        setUpMnemonicAndScanResults()

        val vm = createViewModelAndAwaitScan()
        advanceUntilIdle()

        vm.toggleChain(Chain.Bitcoin)

        val filteredBtc = vm.state.value.filteredChains.first { it.chain == Chain.Bitcoin }
        assertTrue(filteredBtc.isSelected)
    }

    @Test
    fun `selectAll selects all chains`() = runTest(mainDispatcher) {
        setUpMnemonicAndScanResults()

        val vm = createViewModelAndAwaitScan()
        advanceUntilIdle()

        vm.selectAll()

        val state = vm.state.value
        assertTrue(state.allChains.all { it.isSelected })
        assertEquals(state.allChains.size, state.selectedCount)
    }

    @Test
    fun `deselectAll deselects all chains`() = runTest(mainDispatcher) {
        setUpMnemonicAndScanResults()

        val vm = createViewModelAndAwaitScan()
        advanceUntilIdle()

        vm.selectAll()
        vm.deselectAll()

        val state = vm.state.value
        assertTrue(state.allChains.none { it.isSelected })
        assertEquals(0, state.selectedCount)
    }

    @Test
    fun `selectAll respects current search filter`() = runTest(mainDispatcher) {
        setUpMnemonicAndScanResults()

        val vm = createViewModelAndAwaitScan()
        advanceUntilIdle()

        vm.searchTextFieldState.setTextAndNotify("Bitcoin")
        advanceUntilIdle()

        vm.selectAll()

        val state = vm.state.value
        assertTrue(state.filteredChains.all {
            it.chain.raw.contains("Bitcoin", ignoreCase = true)
        })
        assertTrue(state.allChains.all { it.isSelected })
    }

    @Test
    fun `search filters chains by name`() = runTest(mainDispatcher) {
        setUpMnemonicAndScanResults()

        val vm = createViewModelAndAwaitScan()
        advanceUntilIdle()

        vm.searchTextFieldState.setTextAndNotify("Bitcoin")
        advanceUntilIdle()

        val filtered = vm.state.value.filteredChains
        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.all { it.chain.raw.contains("Bitcoin", ignoreCase = true) })
    }

    @Test
    fun `search is case insensitive`() = runTest(mainDispatcher) {
        setUpMnemonicAndScanResults()

        val vm = createViewModelAndAwaitScan()
        advanceUntilIdle()

        vm.searchTextFieldState.setTextAndNotify("bitcoin")
        advanceUntilIdle()

        val filtered = vm.state.value.filteredChains
        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.any { it.chain == Chain.Bitcoin })
    }

    @Test
    fun `search with no matches returns empty list`() = runTest(mainDispatcher) {
        setUpMnemonicAndScanResults()

        val vm = createViewModelAndAwaitScan()
        advanceUntilIdle()

        vm.searchTextFieldState.setTextAndNotify("zzzznonexistent")
        advanceUntilIdle()

        assertTrue(vm.state.value.filteredChains.isEmpty())
    }

    @Test
    fun `clearing search shows all chains`() = runTest(mainDispatcher) {
        setUpMnemonicAndScanResults()

        val vm = createViewModelAndAwaitScan()
        advanceUntilIdle()

        val allChainsCount = vm.state.value.allChains.size

        vm.searchTextFieldState.setTextAndNotify("Bitcoin")
        advanceUntilIdle()
        assertTrue(vm.state.value.filteredChains.size < allChainsCount)

        vm.searchTextFieldState.setTextAndNotify("")
        advanceUntilIdle()
        assertEquals(allChainsCount, vm.state.value.filteredChains.size)
    }

    @Test
    fun `toggleChain respects active search filter`() = runTest(mainDispatcher) {
        setUpMnemonicAndScanResults()

        val vm = createViewModelAndAwaitScan()
        advanceUntilIdle()

        vm.searchTextFieldState.setTextAndNotify("Ethereum")
        advanceUntilIdle()

        val filteredBefore = vm.state.value.filteredChains
        assertTrue(filteredBefore.all { it.chain.raw.contains("Ethereum", ignoreCase = true) })

        vm.toggleChain(Chain.Ethereum)

        val filteredAfter = vm.state.value.filteredChains
        assertTrue(filteredAfter.all { it.chain.raw.contains("Ethereum", ignoreCase = true) })
        assertTrue(filteredAfter.first { it.chain == Chain.Ethereum }.isSelected)
    }

    @Test
    fun `continueWithSelection from ActiveChains saves settings and navigates`() =
        runTest(mainDispatcher) {
            setUpMnemonicAndScanResults(
                results = listOf(
                    ChainBalanceResult(
                        chain = Chain.Bitcoin,
                        derivationPath = DerivationPath.Default,
                        address = "addr",
                        hasBalance = true,
                    ),
                )
            )

            val vm = createViewModelAndAwaitScan()
            advanceUntilIdle()

            assertEquals(ChainsSetupState.ActiveChains, vm.state.value.screenState)

            vm.continueWithSelection()
            advanceUntilIdle()

            verify {
                keyImportRepository.setChainSettings(
                    listOf(
                        ChainImportSetting(
                            chain = Chain.Bitcoin,
                            derivationPath = DerivationPath.Default,
                        )
                    )
                )
            }
            coVerify { navigator.route(Route.KeyImport.DeviceCount) }
        }

    @Test
    fun `continueWithSelection from CustomizeChains saves selected chains`() =
        runTest(mainDispatcher) {
            setUpMnemonicAndScanResults()

            val vm = createViewModelAndAwaitScan()
            advanceUntilIdle()

            vm.selectManually()
            vm.toggleChain(Chain.Bitcoin)
            vm.toggleChain(Chain.Ethereum)

            vm.continueWithSelection()
            advanceUntilIdle()

            verify {
                keyImportRepository.setChainSettings(match { settings ->
                    settings.size == 2 &&
                            settings.any { it.chain == Chain.Bitcoin } &&
                            settings.any { it.chain == Chain.Ethereum }
                })
            }
            coVerify { navigator.route(Route.KeyImport.DeviceCount) }
        }

    @Test
    fun `continueWithSelection with no chains selected does not navigate`() =
        runTest(mainDispatcher) {
            setUpMnemonicAndScanResults()

            val vm = createViewModelAndAwaitScan()
            advanceUntilIdle()

            vm.selectManually()

            vm.continueWithSelection()
            advanceUntilIdle()

            verify(exactly = 0) { keyImportRepository.setChainSettings(any()) }
            coVerify(exactly = 0) { navigator.route(any<Any>()) }
        }

    @Test
    fun `continueWithSelection from Scanning does nothing`() = runTest(mainDispatcher) {
        every { keyImportRepository.get() } returns KeyImportData(mnemonic = "test")
        coEvery { scanChainBalances(any()) } coAnswers {
            kotlinx.coroutines.delay(Long.MAX_VALUE)
            emptyList()
        }

        val vm = createViewModel()
        // State is still Scanning because scan hasn't completed

        vm.continueWithSelection()

        verify(exactly = 0) { keyImportRepository.setChainSettings(any()) }
    }

    @Test
    fun `back navigates back`() = runTest(mainDispatcher) {
        setUpMnemonicAndScanResults()

        val vm = createViewModelAndAwaitScan()
        advanceUntilIdle()

        vm.back()
        advanceUntilIdle()

        coVerify { navigator.navigate(Destination.Back) }
    }
}
