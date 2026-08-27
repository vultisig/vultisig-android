package com.vultisig.wallet.ui.models

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.db.models.TransactionHistoryEntity
import com.vultisig.wallet.data.db.models.TransactionStatus
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.SendTransactionHistoryData
import com.vultisig.wallet.data.models.SwapTransactionHistoryData
import com.vultisig.wallet.data.models.UnknownTransactionHistoryData
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.models.getProviderLogo
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.FeatureFlagRepository
import com.vultisig.wallet.data.repositories.PendingLimitOrderRepository
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import com.vultisig.wallet.data.repositories.TransactionHistoryType
import com.vultisig.wallet.data.repositories.swap.LimitSwapConfig
import com.vultisig.wallet.data.usecases.RefreshLimitOrdersUseCase
import com.vultisig.wallet.data.usecases.RefreshPendingTransactionsUseCase
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.limitorder.BuildLimitOrderCancelTransactionUseCase
import com.vultisig.wallet.ui.models.limitorder.LimitOrderCancelException
import com.vultisig.wallet.ui.models.limitorder.LimitOrderCancelFailure
import com.vultisig.wallet.ui.models.limitorder.LimitOrderHistoryUiModel
import com.vultisig.wallet.ui.models.limitorder.LimitOrderToUiModelMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

enum class TransactionHistoryTab {
    OVERVIEW,
    SWAP,
    SEND,

    /**
     * THORChain limit orders. Its own tab rather than rows in [SWAP] because a resting order is not
     * a settled transaction: it has a live status, a countdown, and — while it rests — an action.
     * The inbound deposit that placed it still appears under [SWAP] and [OVERVIEW] as the on-chain
     * transaction it is.
     */
    LIMIT,
}

data class TransactionHistoryGroupUiModel(
    val datePrefix: UiText,
    val dateSuffix: UiText,
    val transactions: List<TransactionHistoryItemUiModel>,
    val dateKey: String,
)

sealed interface TransactionStatusUiModel {
    data object Broadcasted : TransactionStatusUiModel

    data object Pending : TransactionStatusUiModel

    data object Confirmed : TransactionStatusUiModel

    data class Failed(val reason: UiText?) : TransactionStatusUiModel

    /**
     * THORChain/MayaChain inbound tx that the network refunded (paused pool, unmet swap limit,
     * etc.). Funds were returned to the sender; the intended side effect did not happen.
     */
    data class Refunded(val reason: UiText?) : TransactionStatusUiModel
}

sealed interface TransactionHistoryItemUiModel {
    val id: String
    val txHash: String
    val chain: String
    val status: TransactionStatusUiModel
    val explorerUrl: String
    val timestamp: Long

    data class Send(
        override val id: String,
        override val txHash: String,
        override val chain: String,
        override val status: TransactionStatusUiModel,
        override val explorerUrl: String,
        override val timestamp: Long,
        val fromAddress: String,
        val toAddress: String,
        val amount: String,
        val token: String,
        val tokenLogo: ImageModel,
        val fiatValue: String?,
        val provider: String?,
        val feeEstimate: String?,
        /**
         * Decoded one-line summary of a dApp-supplied tx (e.g. XRPL signRipple), shown in place of
         * the misleading native "0" amount when present. Null for ordinary sends.
         */
        val dappSummary: String? = null,
    ) : TransactionHistoryItemUiModel

    data class Swap(
        override val id: String,
        override val txHash: String,
        override val chain: String,
        override val status: TransactionStatusUiModel,
        override val explorerUrl: String,
        override val timestamp: Long,
        val fromToken: String,
        val fromAmount: String,
        val fromChain: String,
        val fromTokenLogo: ImageModel,
        val toToken: String,
        val toAmount: String,
        val toChain: String,
        val toTokenLogo: ImageModel,
        val provider: String,
        val providerLogo: ImageModel?,
        val fiatValue: String?,
        val fromAddress: String?,
        val toAddress: String?,
        val feeEstimate: String?,
        // A limit order's amount is the floor its memo enforces; a market swap's is the expected
        // output. Only the former may be labelled "min. payout" (#5711).
        val isLimitOrder: Boolean = false,
    ) : TransactionHistoryItemUiModel
}

data class TransactionAssetUiModel(val ticker: String, val chain: String, val logo: ImageModel) {
    val tokenId: String
        get() = "$chain:$ticker"
}

@Immutable
data class TransactionHistoryUiState(
    val selectedTab: TransactionHistoryTab = TransactionHistoryTab.OVERVIEW,
    val groups: List<TransactionHistoryGroupUiModel> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val selectedItem: TransactionHistoryItemUiModel? = null,
    /**
     * Whether the Limit tab is offered at all.
     *
     * Placing an order needs BOTH the remote `limit-swap` kill switch and the local Advanced
     * Settings toggle, which defaults off — so for almost everyone an unconditional tab is a
     * fourth, permanently empty tab for a feature they cannot reach. It is also shown when the
     * vault already HAS orders, so turning the feature off never hides an order that is still
     * resting.
     */
    val isLimitTabVisible: Boolean = false,
    val isAssetSearchSheetVisible: Boolean = false,
    val assetSearchItems: List<TransactionAssetUiModel> = emptyList(),
    val selectedAssetIds: Set<String> = emptySet(),
    val selectedAssets: List<TransactionAssetUiModel> = emptyList(),
    val chainName: String? = null,
    val limitOrders: List<LimitOrderHistoryUiModel> = emptyList(),
    /** Why the last cancel attempt could not even be prepared. Cleared on dismissal. */
    val cancelError: UiText? = null,
)

@HiltViewModel
internal class TransactionHistoryViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionHistoryRepository: TransactionHistoryRepository,
    private val refreshPendingTransactions: RefreshPendingTransactionsUseCase,
    private val pendingLimitOrderRepository: PendingLimitOrderRepository,
    private val refreshLimitOrders: RefreshLimitOrdersUseCase,
    private val mapLimitOrderToUiModel: LimitOrderToUiModelMapper,
    private val buildLimitOrderCancelTransaction: BuildLimitOrderCancelTransactionUseCase,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val featureFlagRepository: FeatureFlagRepository,
    private val limitSwapConfig: LimitSwapConfig,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val route: Route.TransactionHistory = savedStateHandle.toRoute()
    private val vaultId: String = route.vaultId
    private val chainId: String? = route.chainId?.takeIf { it.isNotBlank() }

    val assetSearchTextFieldState = TextFieldState()

    private val _uiState = MutableStateFlow(TransactionHistoryUiState(chainName = chainId))
    val uiState: StateFlow<TransactionHistoryUiState> = _uiState.asStateFlow()

    init {
        observeTransactions()
        observeLimitTabVisibility()
        observeAssetSearchItems()
        observeLimitOrders()
        refreshOnEnter()
    }

    fun selectTab(tab: TransactionHistoryTab) {
        _uiState.update { it.copy(selectedTab = tab, isLoading = true) }
    }

    fun openSearch() {
        _uiState.update { it.copy(isAssetSearchSheetVisible = true) }
    }

    fun toggleAssetSelection(asset: TransactionAssetUiModel) {
        _uiState.update { state ->
            val wasSelected = asset.tokenId in state.selectedAssetIds
            val newIds =
                if (wasSelected) state.selectedAssetIds - asset.tokenId
                else state.selectedAssetIds + asset.tokenId
            val newList =
                if (wasSelected) state.selectedAssets.filter { a -> a.tokenId != asset.tokenId }
                else state.selectedAssets + asset
            state.copy(selectedAssetIds = newIds, selectedAssets = newList)
        }
    }

    fun removeAssetFilter(assetId: String) {
        _uiState.update { state ->
            val newIds = state.selectedAssetIds - assetId
            val newList = state.selectedAssets.filter { a -> a.tokenId != assetId }
            state.copy(selectedAssetIds = newIds, selectedAssets = newList)
        }
    }

    fun clearAllFilters() {
        _uiState.update { it.copy(selectedAssetIds = emptySet(), selectedAssets = emptyList()) }
    }

    fun confirmAssetSearch() {
        _uiState.update { it.copy(isAssetSearchSheetVisible = false) }
    }

    fun closeSearch() {
        _uiState.update {
            it.copy(
                isAssetSearchSheetVisible = false,
                selectedAssetIds = emptySet(),
                selectedAssets = emptyList(),
            )
        }
    }

    fun back() {
        viewModelScope.launch { navigator.back() }
    }

    fun openDetail(item: TransactionHistoryItemUiModel) {
        _uiState.update { it.copy(selectedItem = item) }
    }

    fun dismissDetail() {
        _uiState.update { it.copy(selectedItem = null) }
    }

    fun refresh() {
        viewModelScope.safeLaunch(
            onError = { t -> Timber.w(t, "TransactionHistoryViewModel.refresh() failed") }
        ) {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                // Independently, for the same reason refreshOnEnter keeps them apart: a queue poll
                // that fails must not take the rest of the history down with it, and vice versa.
                // One `try` around both would silently skip the limit orders whenever the pending
                // transactions threw.
                coroutineScope {
                    launch {
                        runCatchingRefresh("pending transactions") {
                            refreshPendingTransactions(vaultId, chainId)
                        }
                    }
                    launch { runCatchingRefresh("limit orders") { refreshLimitOrders(vaultId) } }
                }
                delay(100.milliseconds) // prevent refresh ui freezing
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private suspend fun runCatchingRefresh(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Pull-to-refresh failed for %s", what)
        }
    }

    private fun refreshOnEnter() {
        viewModelScope.safeLaunch { refreshPendingTransactions(vaultId, chainId) }
        // Separate from the pending-transaction refresh: a queue poll that fails must not take the
        // rest of the history down with it, and vice versa.
        viewModelScope.safeLaunch(onError = { t -> Timber.w(t, "Limit-order refresh failed") }) {
            refreshLimitOrders(vaultId)
        }
    }

    /**
     * Limit orders come from their own table rather than the transaction-history rows, because a
     * resting order is not a settled transaction: THORChain's queue is the only thing that knows
     * whether it is still live, and the inbound deposit that placed it confirms within minutes
     * regardless.
     *
     * Not filtered by [chainId]: an order is a claim about a PAIR, and hiding one because the user
     * opened history from the destination chain would hide exactly the order they came looking for.
     * The asset chips ARE honoured, on either leg, because those the user chose deliberately and a
     * list that ignores an active chip is simply wrong.
     *
     * Re-mapped on a timer as well as on a Room emission. The expiry label is computed against a
     * `now` baked in at map time, and the only writers to that table are the on-enter poll and
     * pull-to-refresh — so without a tick a card sits reading "Expires in 4m" long after the order
     * expired.
     */
    private fun observeLimitOrders() {
        viewModelScope.launch {
            combine(
                    pendingLimitOrderRepository.observeOrders(vaultId),
                    expiryTicks(),
                    _uiState.map { it.selectedAssetIds }.distinctUntilChanged(),
                ) { orders, now, assetIds ->
                    mapLimitOrderToUiModel.map(orders, now).filter { it.matchesAssetIds(assetIds) }
                }
                .collect { uiModels ->
                    _uiState.update {
                        it.copy(
                            limitOrders = uiModels,
                            // An order the user already has keeps the tab reachable regardless of
                            // the flags — it is still resting, and it is still cancellable.
                            isLimitTabVisible = it.isLimitTabVisible || uiModels.isNotEmpty(),
                        )
                    }
                }
        }
    }

    /**
     * The Limit tab is offered only when the feature is actually reachable: the remote `limit-swap`
     * kill switch AND the local Advanced Settings toggle, the same conjunction the swap form gates
     * placement on. Without it every user gets a fourth tab that can only ever be empty.
     */
    private fun observeLimitTabVisibility() {
        viewModelScope.safeLaunch(onError = { t -> Timber.w(t, "Limit tab gate failed") }) {
            val isRemoteEnabled = featureFlagRepository.getFeatureFlags().isLimitSwapEnabled
            limitSwapConfig.isFeatureEnabled.collect { isLocallyEnabled ->
                if (isRemoteEnabled && isLocallyEnabled) {
                    _uiState.update { it.copy(isLimitTabVisible = true) }
                }
            }
        }
    }

    /** `now`, re-emitted often enough that a minute-granularity countdown never reads stale. */
    private fun expiryTicks(): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(EXPIRY_TICK)
        }
    }

    /**
     * An order matches a chip on EITHER leg — the pair is what an order is about, and someone
     * filtering to ETH wants their RUNE→ETH order in the list as much as their ETH→BTC one.
     *
     * Matched on the ticker alone. A chip's id is `chain:ticker`, but only the order's SOURCE chain
     * is recorded, so qualifying the buy leg by chain is not possible and qualifying only one of
     * them would be arbitrary.
     */
    private fun LimitOrderHistoryUiModel.matchesAssetIds(assetIds: Set<String>): Boolean {
        if (assetIds.isEmpty()) return true
        return assetIds.any { id ->
            val ticker = id.substringAfterLast(':')
            ticker.equals(sellTicker, ignoreCase = true) ||
                ticker.equals(buyTicker, ignoreCase = true)
        }
    }

    /**
     * Prepare the cancel for [orderId] and hand it to the ordinary deposit verify → keysign flow.
     *
     * Eligibility is re-checked inside the builder against the stored record, not against the
     * tapped card: the list snapshot can be minutes old, and in that window the order can fill,
     * expire, or already have a cancel against it. A cancel signed for a closed order spends a fee
     * (and on an L1 route donates dust) for a memo that can no longer match anything.
     */
    fun cancelLimitOrder(orderId: String) {
        viewModelScope.safeLaunch(
            onError = { t ->
                Timber.w(t, "Could not prepare a limit-order cancel")
                _uiState.update { it.copy(cancelError = t.toCancelErrorText()) }
            }
        ) {
            val order =
                pendingLimitOrderRepository.getOrder(orderId)
                    ?: error("limit order $orderId is no longer stored")
            val transaction = buildLimitOrderCancelTransaction.build(vaultId, order)
            depositTransactionRepository.addTransaction(transaction)
            navigator.route(Route.VerifyDeposit(vaultId = vaultId, transactionId = transaction.id))
        }
    }

    fun dismissCancelError() {
        _uiState.update { it.copy(cancelError = null) }
    }

    private fun Throwable.toCancelErrorText(): UiText =
        UiText.StringResource(
            when ((this as? LimitOrderCancelException)?.failure) {
                LimitOrderCancelFailure.MissingSigningCoin ->
                    R.string.limit_order_cancel_error_missing_coin
                LimitOrderCancelFailure.NoInboundAddress ->
                    R.string.limit_order_cancel_error_no_inbound
                LimitOrderCancelFailure.DustUnavailable ->
                    R.string.limit_order_cancel_error_dust_unavailable
                LimitOrderCancelFailure.InsufficientBalance ->
                    R.string.limit_order_cancel_error_insufficient_balance
                LimitOrderCancelFailure.NotCancellable,
                null -> R.string.limit_order_cancel_error_generic
            }
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeTransactions() {
        viewModelScope.launch {
            uiState
                .map { it.selectedTab }
                .distinctUntilChanged()
                .flatMapLatest { tab ->
                    // The limit tab is fed by its own table, not by transaction-history rows, so
                    // this query stands down rather than emitting the previous tab's transactions
                    // underneath it.
                    tab.toRepositoryType()?.let { type ->
                        transactionHistoryRepository.observeTransactions(
                            vaultId = vaultId,
                            type = type,
                            chain = chainId,
                        )
                    } ?: flowOf(emptyList())
                }
                .map { entities ->
                    val now = System.currentTimeMillis()
                    entities.mapNotNull { it.toUiModel() }.groupByDate(now)
                }
                .combine(_uiState.map { it.selectedAssetIds }.distinctUntilChanged()) { groups, ids
                    ->
                    if (ids.isEmpty()) groups
                    else
                        groups.mapNotNull { group ->
                            val filtered = group.transactions.filter { it.matchesAssetIds(ids) }
                            if (filtered.isEmpty()) null else group.copy(transactions = filtered)
                        }
                }
                .collect { groups ->
                    _uiState.update { it.copy(groups = groups, isLoading = false) }
                }
        }
    }

    private fun TransactionHistoryItemUiModel.matchesAssetIds(assetIds: Set<String>): Boolean =
        when (this) {
            is TransactionHistoryItemUiModel.Send -> "$chain:$token" in assetIds
            is TransactionHistoryItemUiModel.Swap ->
                "$fromChain:$fromToken" in assetIds || "$toChain:$toToken" in assetIds
        }

    private fun observeAssetSearchItems() {
        viewModelScope.launch {
            transactionHistoryRepository
                .observeTransactions(
                    vaultId = vaultId,
                    type = TransactionHistoryType.OVERVIEW,
                    chain = chainId,
                )
                .map { entities ->
                    entities
                        .flatMap { entity ->
                            buildList {
                                when (val p = entity.payload) {
                                    is SendTransactionHistoryData ->
                                        add(
                                            TransactionAssetUiModel(
                                                ticker = p.token,
                                                chain = entity.chain,
                                                logo = getCoinLogo(p.tokenLogo),
                                            )
                                        )

                                    is SwapTransactionHistoryData -> {
                                        add(
                                            TransactionAssetUiModel(
                                                ticker = p.fromToken,
                                                chain = p.fromChain,
                                                logo = getCoinLogo(p.fromTokenLogo),
                                            )
                                        )
                                        add(
                                            TransactionAssetUiModel(
                                                ticker = p.toToken,
                                                chain = p.toChain,
                                                logo = getCoinLogo(p.toTokenLogo),
                                            )
                                        )
                                    }

                                    is UnknownTransactionHistoryData -> Unit
                                }
                            }
                        }
                        .distinctBy { it.tokenId }
                }
                .combine(assetSearchTextFieldState.textAsFlow()) { items, query ->
                    val q = query.toString().trim()
                    if (q.isBlank()) items
                    else
                        items.filter {
                            it.ticker.contains(q, ignoreCase = true) ||
                                it.chain.contains(q, ignoreCase = true)
                        }
                }
                .collect { items -> _uiState.update { it.copy(assetSearchItems = items) } }
        }
    }

    /** Null for the tab that has no transaction-history rows behind it. */
    private fun TransactionHistoryTab.toRepositoryType(): TransactionHistoryType? =
        when (this) {
            TransactionHistoryTab.OVERVIEW -> TransactionHistoryType.OVERVIEW
            TransactionHistoryTab.SWAP -> TransactionHistoryType.SWAPS
            TransactionHistoryTab.SEND -> TransactionHistoryType.SEND
            TransactionHistoryTab.LIMIT -> null
        }

    private fun TransactionHistoryEntity.toUiModel(): TransactionHistoryItemUiModel? {
        val statusUiModel =
            when (status) {
                TransactionStatus.BROADCASTED -> TransactionStatusUiModel.Broadcasted
                TransactionStatus.PENDING -> TransactionStatusUiModel.Pending

                TransactionStatus.CONFIRMED -> TransactionStatusUiModel.Confirmed
                TransactionStatus.FAILED ->
                    TransactionStatusUiModel.Failed(UiText.DynamicString(failureReason.orEmpty()))
                TransactionStatus.REFUNDED ->
                    TransactionStatusUiModel.Refunded(UiText.DynamicString(failureReason.orEmpty()))
                // NotFound is transient — the indexer has not seen the tx yet. Render as Pending.
                TransactionStatus.NotFound -> TransactionStatusUiModel.Pending
            }

        return when (val p = payload) {
            is SendTransactionHistoryData ->
                TransactionHistoryItemUiModel.Send(
                    id = id,
                    txHash = txHash,
                    chain = chain,
                    status = statusUiModel,
                    explorerUrl = explorerUrl,
                    timestamp = timestamp,
                    fromAddress = p.fromAddress,
                    toAddress = p.toAddress,
                    amount = p.amount,
                    token = p.token,
                    tokenLogo = getCoinLogo(p.tokenLogo),
                    fiatValue = p.fiatValue,
                    provider = null,
                    feeEstimate = p.feeEstimate,
                    dappSummary = p.dappSummary,
                )

            is SwapTransactionHistoryData ->
                TransactionHistoryItemUiModel.Swap(
                    id = id,
                    txHash = txHash,
                    chain = chain,
                    status = statusUiModel,
                    explorerUrl = explorerUrl,
                    timestamp = timestamp,
                    fromToken = p.fromToken,
                    fromAmount = p.fromAmount,
                    fromChain = p.fromChain,
                    fromTokenLogo = getCoinLogo(p.fromTokenLogo),
                    toToken = p.toToken,
                    toAmount = p.toAmount,
                    toChain = p.toChain,
                    toTokenLogo = getCoinLogo(p.toTokenLogo),
                    provider = p.provider,
                    providerLogo = getProviderLogo(p.provider),
                    fiatValue = p.fiatValue,
                    fromAddress = null,
                    toAddress = null,
                    feeEstimate = null,
                    isLimitOrder = p.isLimitOrder,
                )

            is UnknownTransactionHistoryData -> null
        }
    }

    private fun List<TransactionHistoryItemUiModel>.groupByDate(
        nowMs: Long
    ): List<TransactionHistoryGroupUiModel> {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val yesterday = today.minusDays(1)
        val labelFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

        return groupBy { item -> Instant.ofEpochMilli(item.timestamp).atZone(zone).toLocalDate() }
            .entries
            .sortedByDescending { it.key }
            .map { (date, items) ->
                val dateSuffix = UiText.DynamicString(date.format(labelFormatter))
                val datePrefix =
                    when (date) {
                        today -> UiText.StringResource(R.string.transaction_history_date_today)
                        yesterday ->
                            UiText.StringResource(R.string.transaction_history_date_yesterday)
                        else -> null
                    }
                TransactionHistoryGroupUiModel(
                    datePrefix = datePrefix ?: UiText.Empty,
                    dateSuffix = dateSuffix,
                    transactions = items,
                    dateKey = date.toString(),
                )
            }
    }

    private companion object {
        /**
         * How often the limit-order cards are re-mapped so their countdown advances. Well under the
         * minute the label is granular to, and it costs one pure re-map of a short list — no
         * network and no database.
         */
        val EXPIRY_TICK = 15.seconds
    }
}
