@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.models.FeatureFlagJson
import com.vultisig.wallet.data.db.models.PendingLimitOrderEntity
import com.vultisig.wallet.data.db.models.TransactionHistoryEntity
import com.vultisig.wallet.data.db.models.TransactionStatus
import com.vultisig.wallet.data.db.models.TransactionType as DbTransactionType
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.LimitOrderStatus
import com.vultisig.wallet.data.models.SendTransactionHistoryData
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.FeatureFlagRepository
import com.vultisig.wallet.data.repositories.PendingLimitOrderRepository
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import com.vultisig.wallet.data.repositories.swap.LimitSwapConfig
import com.vultisig.wallet.data.usecases.RefreshLimitOrdersUseCase
import com.vultisig.wallet.data.usecases.RefreshPendingTransactionsUseCase
import com.vultisig.wallet.ui.models.TransactionAssetUiModel
import com.vultisig.wallet.ui.models.TransactionHistoryItemUiModel
import com.vultisig.wallet.ui.models.TransactionHistoryTab
import com.vultisig.wallet.ui.models.TransactionHistoryViewModel
import com.vultisig.wallet.ui.models.TransactionStatusUiModel
import com.vultisig.wallet.ui.models.limitorder.BuildLimitOrderCancelTransactionUseCase
import com.vultisig.wallet.ui.models.limitorder.LimitOrderCancelException
import com.vultisig.wallet.ui.models.limitorder.LimitOrderCancelFailure
import com.vultisig.wallet.ui.models.limitorder.LimitOrderToUiModelMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapperImpl
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.math.BigInteger
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
import vultisig.keysign.v1.TransactionType

/**
 * Unit tests for [TransactionHistoryViewModel].
 *
 * This suite avoids `runTest` and uses [StandardTestDispatcher], which never auto-runs coroutines.
 * Each test reads `vm.uiState.value` synchronously (StateFlow updates happen on the calling thread
 * for [`MutableStateFlow.update`]) so we never need to suspend or advance time. The
 * `testScope.cancel()` in `tearDown` cleans up any queued tasks before the next test.
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
    private lateinit var pendingLimitOrderRepository: PendingLimitOrderRepository
    private lateinit var refreshLimitOrders: RefreshLimitOrdersUseCase
    private lateinit var mapLimitOrderToUiModel: LimitOrderToUiModelMapper
    private lateinit var buildLimitOrderCancelTransaction: BuildLimitOrderCancelTransactionUseCase
    private lateinit var depositTransactionRepository: DepositTransactionRepository
    private lateinit var featureFlagRepository: FeatureFlagRepository
    private lateinit var limitSwapConfig: LimitSwapConfig
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
        pendingLimitOrderRepository = mockk(relaxed = true)
        every { pendingLimitOrderRepository.observeOrders(any()) } returns flowOf(emptyList())
        refreshLimitOrders = mockk(relaxed = true)
        mapLimitOrderToUiModel = LimitOrderToUiModelMapper(TokenValueToDecimalUiStringMapperImpl())
        buildLimitOrderCancelTransaction = mockk(relaxed = true)
        depositTransactionRepository = mockk(relaxed = true)
        // Both flags ON by default so the existing cases see the Limit tab; the gate has its own
        // tests below.
        featureFlagRepository = mockk(relaxed = true)
        coEvery { featureFlagRepository.getFeatureFlags() } returns
            FeatureFlagJson(isLimitSwapEnabled = true)
        limitSwapConfig = mockk(relaxed = true)
        every { limitSwapConfig.isFeatureEnabled } returns flowOf(true)
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
            pendingLimitOrderRepository = pendingLimitOrderRepository,
            refreshLimitOrders = refreshLimitOrders,
            mapLimitOrderToUiModel = mapLimitOrderToUiModel,
            buildLimitOrderCancelTransaction = buildLimitOrderCancelTransaction,
            depositTransactionRepository = depositTransactionRepository,
            featureFlagRepository = featureFlagRepository,
            limitSwapConfig = limitSwapConfig,
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
        every { transactionHistoryRepository.observeTransactions(any(), any(), any()) } returns
            flowOf(emptyList())

        val vm = createViewModel()
        testScope.runCurrent()

        vm.uiState.value.groups.isEmpty().shouldBeTrue()
        vm.uiState.value.isLoading.shouldBeFalse()
    }

    /**
     * Verifies refresh() invokes refreshPendingTransactions(vaultId) on the use-case. Construction
     * alone no longer polls — the screen's resume effect owns that — so one explicit refresh is
     * exactly one call.
     */
    @Test
    fun `refresh invokes refreshPendingTransactions`() {
        val vm = createViewModel()
        testScope.runCurrent()

        vm.refresh()
        testScope.runCurrent()

        coVerify(exactly = 1) { refreshPendingTransactions(VAULT_ID) }
    }

    /**
     * The screen re-checks settlement on every return, not once at construction: a transaction
     * broadcast seconds before the screen opened has no receipt yet at that first check, and
     * nothing else would look again.
     */
    @Test
    fun `onScreenResumed refreshes pending transactions`() {
        val vm = createViewModel()
        testScope.runCurrent()
        coVerify(exactly = 0) { refreshPendingTransactions(VAULT_ID) }

        vm.onScreenResumed()
        testScope.runCurrent()

        coVerify(exactly = 1) { refreshPendingTransactions(VAULT_ID) }
    }

    /** A row still in flight is re-checked while the user watches it, without any gesture. */
    @Test
    fun `in-flight rows are re-polled on a timer while the screen is visible`() {
        every { transactionHistoryRepository.observeTransactions(any(), any(), any()) } returns
            flowOf(listOf(inFlightEntity()))

        val vm = createViewModel()
        testScope.runCurrent()
        vm.onScreenResumed()
        testScope.runCurrent()

        testScope.testScheduler.advanceTimeBy(POLL_INTERVAL_MS + 1)
        testScope.runCurrent()

        coVerify(exactly = 2) { refreshPendingTransactions(VAULT_ID) }
    }

    /** Leaving the screen stops the timer: a backgrounded history screen must not keep polling. */
    @Test
    fun `polling stops once the screen is no longer visible`() {
        every { transactionHistoryRepository.observeTransactions(any(), any(), any()) } returns
            flowOf(listOf(inFlightEntity()))

        val vm = createViewModel()
        testScope.runCurrent()
        vm.onScreenResumed()
        testScope.runCurrent()
        vm.onScreenPaused()
        testScope.runCurrent()

        testScope.testScheduler.advanceTimeBy(POLL_INTERVAL_MS * 4)
        testScope.runCurrent()

        coVerify(exactly = 1) { refreshPendingTransactions(VAULT_ID) }
    }

    /** A settled row costs nothing: no timer starts when there is nothing left to settle. */
    @Test
    fun `no polling timer runs when every row has settled`() {
        every { transactionHistoryRepository.observeTransactions(any(), any(), any()) } returns
            flowOf(listOf(inFlightEntity().copy(status = TransactionStatus.CONFIRMED)))

        val vm = createViewModel()
        testScope.runCurrent()
        vm.onScreenResumed()
        testScope.runCurrent()

        testScope.testScheduler.advanceTimeBy(POLL_INTERVAL_MS * 4)
        testScope.runCurrent()

        coVerify(exactly = 1) { refreshPendingTransactions(VAULT_ID) }
    }

    /** Opening a row that is still in flight asks the chain about that one transaction. */
    @Test
    fun `openDetail re-checks an in-flight row`() {
        val vm = createViewModel()
        val item = sendItem().copy(status = TransactionStatusUiModel.Broadcasted)

        vm.openDetail(item)
        testScope.runCurrent()

        coVerify(exactly = 1) { refreshPendingTransactions.refreshOne("Ethereum", "0xabc") }
    }

    /** A settled row is already final — opening it must not spend a status call. */
    @Test
    fun `openDetail does not re-check a settled row`() {
        val vm = createViewModel()

        vm.openDetail(sendItem())
        testScope.runCurrent()

        coVerify(exactly = 0) { refreshPendingTransactions.refreshOne(any(), any()) }
    }

    /**
     * The cancel goes through the ordinary deposit verify → keysign flow, so a happy path is
     * "stored, then routed to VerifyDeposit with the SAME transaction id". Routing to an id the
     * repository never received would land the user on an empty verify screen.
     */
    @Test
    fun `cancelLimitOrder stores the built transaction and routes to its verify screen`() {
        val order = restingOrder()
        coEvery { pendingLimitOrderRepository.getOrder(ORDER_HASH) } returns order
        coEvery { buildLimitOrderCancelTransaction.build(VAULT_ID, order) } returns cancelTx()

        val vm = createViewModel()
        testScope.runCurrent()

        vm.cancelLimitOrder(ORDER_HASH)
        testScope.runCurrent()

        coVerify { depositTransactionRepository.addTransaction(match { it.id == CANCEL_TX_ID }) }
        coVerify {
            navigator.route(Route.VerifyDeposit(vaultId = VAULT_ID, transactionId = CANCEL_TX_ID))
        }
        vm.uiState.value.cancelError.shouldBeNull()
    }

    /**
     * A builder refusal must surface as its own message and must NOT reach keysign. Eligibility is
     * re-checked inside the builder against the stored record rather than the tapped card, so this
     * is the path a card that has gone stale takes.
     */
    @Test
    fun `a refused cancel surfaces an error and signs nothing`() {
        val order = restingOrder()
        coEvery { pendingLimitOrderRepository.getOrder(ORDER_HASH) } returns order
        coEvery { buildLimitOrderCancelTransaction.build(VAULT_ID, order) } throws
            LimitOrderCancelException(
                LimitOrderCancelFailure.InsufficientBalance,
                "vault cannot cover the cancel",
            )

        val vm = createViewModel()
        testScope.runCurrent()

        vm.cancelLimitOrder(ORDER_HASH)
        testScope.runCurrent()

        vm.uiState.value.cancelError shouldBe
            UiText.StringResource(R.string.limit_order_cancel_error_insufficient_balance)
        coVerify(exactly = 0) { depositTransactionRepository.addTransaction(any()) }
        coVerify(exactly = 0) { navigator.route(any<Route.VerifyDeposit>()) }

        vm.dismissCancelError()
        vm.uiState.value.cancelError.shouldBeNull()
    }

    /**
     * The asset chips render above every tab, so a LIMIT list that ignores them shows orders the
     * user has just filtered out. Matched on EITHER leg — a pair is what an order is about.
     */
    @Test
    fun `the limit tab honours an active asset chip on either leg`() {
        every { pendingLimitOrderRepository.observeOrders(VAULT_ID) } returns
            flowOf(
                listOf(
                    restingOrder(hash = "RUNE_TO_BTC"),
                    restingOrder(
                        hash = "RUNE_TO_ETH",
                        targetAsset = "ETH.ETH",
                        targetTicker = "ETH",
                    ),
                )
            )

        val vm = createViewModel()
        testScope.runCurrent()
        vm.uiState.value.limitOrders.map { it.id } shouldBe listOf("RUNE_TO_BTC", "RUNE_TO_ETH")

        vm.toggleAssetSelection(
            TransactionAssetUiModel(ticker = "ETH", chain = "Ethereum", logo = "")
        )
        testScope.runCurrent()

        vm.uiState.value.limitOrders.map { it.id } shouldBe listOf("RUNE_TO_ETH")
    }

    /**
     * Placing an order needs the remote kill switch AND the local toggle, which defaults off — so
     * an unconditional tab is a fourth, permanently empty tab for nearly everyone.
     */
    @Test
    fun `the limit tab is hidden when the feature cannot be reached`() {
        every { limitSwapConfig.isFeatureEnabled } returns flowOf(false)

        val vm = createViewModel()
        testScope.runCurrent()

        vm.uiState.value.isLimitTabVisible.shouldBeFalse()
    }

    /** Turning the feature off must never hide an order that is still resting and cancellable. */
    @Test
    fun `an existing order keeps the limit tab reachable with the feature off`() {
        every { limitSwapConfig.isFeatureEnabled } returns flowOf(false)
        every { pendingLimitOrderRepository.observeOrders(VAULT_ID) } returns
            flowOf(listOf(restingOrder()))

        val vm = createViewModel()
        testScope.runCurrent()

        vm.uiState.value.isLimitTabVisible.shouldBeTrue()
    }

    private fun restingOrder(
        hash: String = ORDER_HASH,
        targetAsset: String = "BTC.BTC",
        targetTicker: String = "BTC",
    ) =
        PendingLimitOrderEntity(
            inboundTxHash = hash,
            vaultId = VAULT_ID,
            sourceAsset = "THOR.RUNE",
            sourceAmount = "100000000",
            targetAsset = targetAsset,
            destAddr = "bc1qxy",
            targetPrice = "0.04",
            expiryBlocks = 14_400,
            createdAt = 0L,
            status = LimitOrderStatus.Pending.raw,
            sourceChain = Chain.ThorChain.raw,
            sourceDecimals = 8,
            sourceAddress = "thor1abc",
            sourceTicker = "RUNE",
            targetTicker = targetTicker,
            sourceAmount1e8 = "100000000",
            tradeTarget = "4000000",
            sourceAssetFull = "THOR.RUNE",
            targetAssetFull = targetAsset,
            expiryObservedAt = 1L,
        )

    private fun cancelTx(): DepositTransaction {
        val rune = Coins.coins.getValue(Chain.ThorChain).first { it.isNativeToken }
        return DepositTransaction(
            id = CANCEL_TX_ID,
            vaultId = VAULT_ID,
            srcToken = rune,
            srcAddress = "thor1abc",
            srcTokenValue = TokenValue(BigInteger.ZERO, rune.ticker, rune.decimal),
            memo = "m=<:100000000THOR.RUNE:4000000BTC.BTC:0",
            dstAddress = "",
            estimatedFees = TokenValue(BigInteger.ZERO, rune.ticker, rune.decimal),
            estimateFeesFiat = "$0.00",
            blockChainSpecific =
                BlockChainSpecific.THORChain(
                    accountNumber = BigInteger.ZERO,
                    sequence = BigInteger.ZERO,
                    fee = BigInteger.ZERO,
                    isDeposit = true,
                    transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                ),
        )
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

    private fun inFlightEntity() =
        TransactionHistoryEntity(
            id = "Ethereum:0xabc",
            vaultId = VAULT_ID,
            type = DbTransactionType.SEND,
            status = TransactionStatus.BROADCASTED,
            chain = "Ethereum",
            timestamp = 0L,
            txHash = "0xabc",
            explorerUrl = "",
            payload =
                SendTransactionHistoryData(
                    fromAddress = "0xFrom",
                    toAddress = "0xTo",
                    amount = "1.0",
                    token = "ETH",
                    tokenLogo = "",
                    feeEstimate = "",
                    memo = "",
                    fiatValue = "",
                ),
            confirmedAt = null,
            failureReason = null,
            lastCheckedAt = null,
        )

    private companion object {
        const val VAULT_ID = "vault-1"
        const val ORDER_HASH = "HASH"
        const val CANCEL_TX_ID = "cancel-tx"
        const val POLL_INTERVAL_MS = 15_000L
    }
}
