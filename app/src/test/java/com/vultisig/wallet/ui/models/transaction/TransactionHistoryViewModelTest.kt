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
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Unit tests for [TransactionHistoryViewModel].
 *
 * The production VM starts a perpetual `while (true) delay(1.minutes)` ticker in `init` via
 * `viewModelScope.launch`. Standard `runTest` would loop forever advancing virtual time over that
 * ticker, so this suite avoids `runTest` entirely:
 * - We use [StandardTestDispatcher], which never auto-runs coroutines. The ticker `launch` is
 *   queued but never fires unless we explicitly call [TestScope.runCurrent].
 * - Each test reads `vm.uiState.value` synchronously (StateFlow updates happen on the calling
 *   thread for [`MutableStateFlow.update`]) so we never need to suspend or advance time.
 * - The `testScope.cancel()` in `tearDown` cleans up any queued tasks before the next test.
 *
 * Class-level `@Timeout(5s, SEPARATE_THREAD)` is the safety net — interrupts on its own thread so a
 * future regression that introduces a real wait fails fast instead of hanging the suite.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Timeout(value = 5, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
internal class TransactionHistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

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

    /** Cancels any queued tasks, unmocks statics, resets `Dispatchers.Main` after each test. */
    @AfterEach
    fun tearDown() {
        testScope.cancel()
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    private fun createViewModel(): TransactionHistoryViewModel =
        TransactionHistoryViewModel(
            savedStateHandle = SavedStateHandle(),
            transactionHistoryRepository = transactionHistoryRepository,
            refreshPendingTransactions = refreshPendingTransactions,
            navigator = navigator,
        )

    /** Verifies selectTab updates selectedTab and re-enters the loading state. */
    @Test
    fun `selectTab updates selectedTab and sets isLoading`() {
        val vm = createViewModel()
        // Drain init so the initial transaction observer flips isLoading off.
        testScope.runCurrent()

        vm.selectTab(TransactionHistoryTab.SWAP)
        testScope.runCurrent()

        vm.uiState.value.selectedTab shouldBe TransactionHistoryTab.SWAP
        vm.uiState.value.isLoading.shouldBeTrue()
    }

    /** Verifies openSearch sets isAssetSearchSheetVisible to true. */
    @Test
    fun `openSearch sets isAssetSearchSheetVisible to true`() {
        val vm = createViewModel()
        vm.openSearch()
        vm.uiState.value.isAssetSearchSheetVisible.shouldBeTrue()
    }

    /** Verifies confirmAssetSearch closes the asset search sheet. */
    @Test
    fun `confirmAssetSearch closes the asset search sheet`() {
        val vm = createViewModel()
        vm.openSearch()
        vm.confirmAssetSearch()
        vm.uiState.value.isAssetSearchSheetVisible.shouldBeFalse()
    }

    /** Verifies openDetail sets selectedItem. */
    @Test
    fun `openDetail sets selectedItem`() {
        val vm = createViewModel()
        val item = sendItem()
        vm.openDetail(item)
        vm.uiState.value.selectedItem.shouldNotBeNull()
        vm.uiState.value.selectedItem shouldBe item
    }

    /** Verifies dismissDetail clears selectedItem. */
    @Test
    fun `dismissDetail clears selectedItem`() {
        val vm = createViewModel()
        vm.openDetail(sendItem())
        vm.dismissDetail()
        vm.uiState.value.selectedItem.shouldBeNull()
    }

    /** Verifies toggleAssetSelection adds asset to selectedAssetIds. */
    @Test
    fun `toggleAssetSelection adds asset to selectedAssetIds`() {
        val vm = createViewModel()
        val asset = TransactionAssetUiModel("ETH", "Ethereum", "")
        vm.toggleAssetSelection(asset)
        vm.uiState.value.selectedAssetIds.contains("Ethereum:ETH").shouldBeTrue()
    }

    /** Verifies clearAllFilters resets selectedAssets and ids. */
    @Test
    fun `clearAllFilters resets selectedAssets and ids`() {
        val vm = createViewModel()
        vm.toggleAssetSelection(TransactionAssetUiModel("ETH", "Ethereum", ""))
        vm.clearAllFilters()
        vm.uiState.value.selectedAssetIds.isEmpty().shouldBeTrue()
        vm.uiState.value.selectedAssets.isEmpty().shouldBeTrue()
    }

    /**
     * Verifies confirmAssetSearch closes the sheet WITHOUT discarding the toggled selection — the
     * production code commits selections live via toggleAssetSelection, not on confirm.
     */
    @Test
    fun `confirmAssetSearch closes sheet and preserves toggled selection`() {
        val vm = createViewModel()
        val asset = TransactionAssetUiModel("ETH", "Ethereum", "")

        vm.openSearch()
        vm.toggleAssetSelection(asset)
        vm.confirmAssetSearch()

        vm.uiState.value.isAssetSearchSheetVisible.shouldBeFalse()
        vm.uiState.value.selectedAssetIds.contains(asset.tokenId).shouldBeTrue()
        vm.uiState.value.selectedAssets shouldBe listOf(asset)
    }

    /**
     * Verifies openDetail preserves the full transaction hash (no truncation) so navigation to the
     * detail screen carries the entire id, guarding against bugs that would silently truncate it.
     */
    @Test
    fun `openDetail preserves the full transaction hash`() {
        val vm = createViewModel()
        val fullHash = "0x" + "a".repeat(64) // 66-char EVM-style txHash
        val item = sendItem().copy(id = fullHash, txHash = fullHash)

        vm.openDetail(item)

        val selected = vm.uiState.value.selectedItem
        selected.shouldNotBeNull()
        selected.txHash shouldBe fullHash
        selected.id shouldBe fullHash
    }

    /**
     * Verifies that when the repository emits an empty transaction list, the ViewModel exposes an
     * empty groups list and clears the loading flag after the init flow completes.
     */
    @Test
    fun `empty repository flow yields empty groups and clears loading`() {
        every { transactionHistoryRepository.observeTransactions(any(), any()) } returns
            flowOf(emptyList())

        val vm = createViewModel()
        testScope.runCurrent()

        vm.uiState.value.groups.isEmpty().shouldBeTrue()
        vm.uiState.value.isLoading.shouldBeFalse()
    }

    /**
     * Verifies refresh() invokes refreshPendingTransactions(vaultId) on the use-case. The
     * production code calls it once from `refreshOnEnter` in init and once from each explicit
     * `refresh()`, so the assertion is `atLeast = 2` after one refresh call.
     */
    @Test
    fun `refresh invokes refreshPendingTransactions`() {
        val vm = createViewModel()
        testScope.runCurrent() // drain init's refreshOnEnter call

        vm.refresh()
        testScope.runCurrent()

        coVerify(atLeast = 2) { refreshPendingTransactions(VAULT_ID) }
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
