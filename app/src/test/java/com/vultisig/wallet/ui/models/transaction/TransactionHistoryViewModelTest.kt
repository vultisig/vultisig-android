@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import com.vultisig.wallet.data.usecases.RefreshPendingTransactionsUseCase
import com.vultisig.wallet.ui.models.TransactionAssetUiModel
import com.vultisig.wallet.ui.models.TransactionHistoryItemUiModel
import com.vultisig.wallet.ui.models.TransactionHistoryTab
import com.vultisig.wallet.ui.models.TransactionHistoryViewModel
import com.vultisig.wallet.ui.models.TransactionStatusUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Unit tests for [TransactionHistoryViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class TransactionHistoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var transactionHistoryRepository: TransactionHistoryRepository
    private lateinit var refreshPendingTransactions: RefreshPendingTransactionsUseCase
    private lateinit var navigator: Navigator<Destination>

    /** Sets up mocks and test dispatcher before each test. */
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.TransactionHistory>() } returns
            Route.TransactionHistory(vaultId = VAULT_ID)
        transactionHistoryRepository = mockk(relaxed = true)
        refreshPendingTransactions = mockk(relaxed = true)
        navigator = mockk(relaxed = true)
    }

    /** Cleans up mocks and resets test dispatcher after each test. */
    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        TransactionHistoryViewModel(
            savedStateHandle = SavedStateHandle(),
            transactionHistoryRepository = transactionHistoryRepository,
            refreshPendingTransactions = refreshPendingTransactions,
            navigator = navigator,
        )

    /**
     * Cancels the VM's `viewModelScope` so the perpetual `startTimeTicker` loop does not keep
     * `runTest` from settling at end-of-test. `androidx.lifecycle.ViewModel.clear` is package-
     * private; reach it via reflection so we don't need a debug-only override.
     */
    private fun clear(vm: ViewModel) {
        ViewModel::class.java.getDeclaredMethod("clear").apply { isAccessible = true }.invoke(vm)
    }

    /** Verifies selectTab updates selectedTab and re-enters the loading state. */
    @Test
    fun `selectTab updates selectedTab and sets isLoading`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            // Let the initial transaction observer drain and flip isLoading off, so the test
            // unambiguously asserts that selectTab is what flips it back on. Use runCurrent
            // (not advanceUntilIdle) — the VM starts a perpetual `while (true) delay(1.minutes)`
            // ticker in init that would make advanceUntilIdle loop forever in virtual time.
            runCurrent()

            vm.selectTab(TransactionHistoryTab.SWAP)

            assertEquals(TransactionHistoryTab.SWAP, vm.uiState.value.selectedTab)
            assertTrue(vm.uiState.value.isLoading)
            clear(vm)
        }

    /** Verifies openSearch sets isAssetSearchSheetVisible to true. */
    @Test
    fun `openSearch sets isAssetSearchSheetVisible to true`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.openSearch()
            assertTrue(vm.uiState.value.isAssetSearchSheetVisible)
            clear(vm)
        }

    /** Verifies confirmAssetSearch closes the asset search sheet. */
    @Test
    fun `confirmAssetSearch closes the asset search sheet`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.openSearch()
            vm.confirmAssetSearch()
            assertFalse(vm.uiState.value.isAssetSearchSheetVisible)
            clear(vm)
        }

    /** Verifies openDetail sets selectedItem. */
    @Test
    fun `openDetail sets selectedItem`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            val item = sendItem()
            vm.openDetail(item)
            assertNotNull(vm.uiState.value.selectedItem)
            assertEquals(item, vm.uiState.value.selectedItem)
            clear(vm)
        }

    /** Verifies dismissDetail clears selectedItem. */
    @Test
    fun `dismissDetail clears selectedItem`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.openDetail(sendItem())
            vm.dismissDetail()
            assertNull(vm.uiState.value.selectedItem)
            clear(vm)
        }

    /** Verifies toggleAssetSelection adds asset to selectedAssetIds. */
    @Test
    fun `toggleAssetSelection adds asset to selectedAssetIds`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            val asset = TransactionAssetUiModel("ETH", "Ethereum", "")
            vm.toggleAssetSelection(asset)
            assertTrue(vm.uiState.value.selectedAssetIds.contains("Ethereum:ETH"))
            clear(vm)
        }

    /** Verifies clearAllFilters resets selectedAssets and ids. */
    @Test
    fun `clearAllFilters resets selectedAssets and ids`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.toggleAssetSelection(TransactionAssetUiModel("ETH", "Ethereum", ""))
            vm.clearAllFilters()
            assertTrue(vm.uiState.value.selectedAssetIds.isEmpty())
            assertTrue(vm.uiState.value.selectedAssets.isEmpty())
            clear(vm)
        }

    /**
     * Verifies confirmAssetSearch closes the sheet WITHOUT discarding the toggled selection — the
     * production code commits selections live via toggleAssetSelection, not on confirm.
     */
    @Test
    fun `confirmAssetSearch closes sheet and preserves toggled selection`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            val asset = TransactionAssetUiModel("ETH", "Ethereum", "")

            vm.openSearch()
            vm.toggleAssetSelection(asset)
            vm.confirmAssetSearch()

            assertFalse(vm.uiState.value.isAssetSearchSheetVisible)
            assertTrue(vm.uiState.value.selectedAssetIds.contains(asset.tokenId))
            assertEquals(listOf(asset), vm.uiState.value.selectedAssets)
            clear(vm)
        }

    /**
     * Verifies openDetail preserves the full transaction hash (no truncation) so navigation to the
     * detail screen carries the entire id, guarding against bugs that would silently truncate it.
     */
    @Test
    fun `openDetail preserves the full transaction hash`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            val fullHash = "0x" + "a".repeat(64) // 66-char EVM-style txHash
            val item = sendItem().copy(id = fullHash, txHash = fullHash)

            vm.openDetail(item)

            val selected = vm.uiState.value.selectedItem
            assertNotNull(selected)
            assertEquals(fullHash, selected.txHash)
            assertEquals(fullHash, selected.id)
            clear(vm)
        }

    /**
     * Verifies that when the repository emits an empty transaction list, the ViewModel exposes an
     * empty groups list and clears the loading flag. `runCurrent` (not `advanceUntilIdle`) is used
     * because the VM starts a perpetual ticker — advancing virtual time would never settle.
     */
    @Test
    fun `empty repository flow yields empty groups and clears loading`() =
        runTest(testDispatcher) {
            every { transactionHistoryRepository.observeTransactions(any(), any()) } returns
                flowOf(emptyList())

            val vm = createViewModel()
            runCurrent()

            assertTrue(vm.uiState.value.groups.isEmpty())
            assertFalse(vm.uiState.value.isLoading)
            clear(vm)
        }

    /**
     * Verifies refresh() invokes refreshPendingTransactions(vaultId) on the use-case and clears
     * isRefreshing once the suspend call returns. Uses `advanceTimeBy(100ms)` to step over the
     * `delay(100.milliseconds)` inside the production refresh — using `advanceUntilIdle` would loop
     * forever on the perpetual ticker.
     */
    @Test
    fun `refresh invokes refreshPendingTransactions and clears isRefreshing`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            runCurrent() // drain init's refreshOnEnter call

            vm.refresh()
            advanceTimeBy(150.milliseconds)
            runCurrent()

            coVerify(atLeast = 2) { refreshPendingTransactions(VAULT_ID) }
            assertFalse(vm.uiState.value.isRefreshing)
            clear(vm)
        }

    private fun sendItem() =
        TransactionHistoryItemUiModel.Send(
            id = "id-1",
            txHash = "0xabc",
            chain = "Ethereum",
            status = TransactionStatusUiModel.Confirmed,
            explorerUrl = "https://etherscan.io/tx/0xabc",
            timestamp = 0L,
            fromAddress = "0xFrom",
            toAddress = "0xTo",
            amount = "1.0",
            token = "ETH",
            tokenLogo = "",
            fiatValue = "1000",
            provider = null,
            feeEstimate = null,
        )

    private companion object {
        const val VAULT_ID = "vault-1"
    }
}
