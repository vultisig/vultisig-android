@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.transaction

import androidx.lifecycle.SavedStateHandle
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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

    /** Verifies selectTab updates selectedTab and re-enters the loading state. */
    @Test
    fun `selectTab updates selectedTab and sets isLoading`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            // Let the initial transaction observer drain and flip isLoading off, so the test
            // unambiguously asserts that selectTab is what flips it back on.
            advanceUntilIdle()

            vm.selectTab(TransactionHistoryTab.SWAP)

            assertEquals(TransactionHistoryTab.SWAP, vm.uiState.value.selectedTab)
            assertTrue(vm.uiState.value.isLoading)
        }

    /** Verifies openSearch sets isAssetSearchSheetVisible to true. */
    @Test
    fun `openSearch sets isAssetSearchSheetVisible to true`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.openSearch()
            assertTrue(vm.uiState.value.isAssetSearchSheetVisible)
        }

    /** Verifies confirmAssetSearch closes the asset search sheet. */
    @Test
    fun `confirmAssetSearch closes the asset search sheet`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.openSearch()
            vm.confirmAssetSearch()
            assertFalse(vm.uiState.value.isAssetSearchSheetVisible)
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
        }

    /** Verifies dismissDetail clears selectedItem. */
    @Test
    fun `dismissDetail clears selectedItem`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.openDetail(sendItem())
            vm.dismissDetail()
            assertNull(vm.uiState.value.selectedItem)
        }

    /** Verifies toggleAssetSelection adds asset to selectedAssetIds. */
    @Test
    fun `toggleAssetSelection adds asset to selectedAssetIds`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            val asset = TransactionAssetUiModel("ETH", "Ethereum", "")
            vm.toggleAssetSelection(asset)
            assertTrue(vm.uiState.value.selectedAssetIds.contains("Ethereum:ETH"))
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
