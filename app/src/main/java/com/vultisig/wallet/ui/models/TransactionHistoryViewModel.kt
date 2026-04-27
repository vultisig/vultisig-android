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
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import com.vultisig.wallet.data.repositories.TransactionHistoryType
import com.vultisig.wallet.data.usecases.RefreshPendingTransactionsUseCase
import com.vultisig.wallet.data.utils.safeLaunch
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

enum class TransactionHistoryTab {
    OVERVIEW,
    SWAP,
    SEND,
}

data class TransactionHistoryGroupUiModel(
    val datePrefix: UiText,
    val dateSuffix: UiText,
    val transactions: List<TransactionHistoryItemUiModel>,
)

sealed interface TransactionStatusUiModel {
    data object Broadcasted : TransactionStatusUiModel

    data class Pending(val elapsedTime: UiText) : TransactionStatusUiModel

    data object Confirmed : TransactionStatusUiModel

    data class Failed(val reason: UiText?) : TransactionStatusUiModel
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
    val isAssetSearchSheetVisible: Boolean = false,
    val assetSearchItems: List<TransactionAssetUiModel> = emptyList(),
    val selectedAssetIds: Set<String> = emptySet(),
    val selectedAssets: List<TransactionAssetUiModel> = emptyList(),
)

@HiltViewModel
internal class TransactionHistoryViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionHistoryRepository: TransactionHistoryRepository,
    private val refreshPendingTransactions: RefreshPendingTransactionsUseCase,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val vaultId: String = savedStateHandle.toRoute<Route.TransactionHistory>().vaultId

    private val currentTime = MutableStateFlow(System.currentTimeMillis())
    private val selectedAssetIds = MutableStateFlow<Set<String>>(emptySet())
    private val selectedAssetsList = MutableStateFlow<List<TransactionAssetUiModel>>(emptyList())

    val assetSearchTextFieldState = TextFieldState()

    private val _uiState = MutableStateFlow(TransactionHistoryUiState())
    val uiState: StateFlow<TransactionHistoryUiState> = _uiState.asStateFlow()

    init {
        startTimeTicker()
        observeTransactions()
        observeAssetSearchItems()
        refreshOnEnter()
    }

    fun selectTab(tab: TransactionHistoryTab) {
        _uiState.update { it.copy(selectedTab = tab, isLoading = true) }
    }

    fun openSearch() {
        _uiState.update { it.copy(isAssetSearchSheetVisible = true) }
    }

    fun toggleAssetSelection(asset: TransactionAssetUiModel) {
        val newIds: Set<String>
        val newList: List<TransactionAssetUiModel>
        if (asset.tokenId in selectedAssetIds.value) {
            newIds = selectedAssetIds.value - asset.tokenId
            newList = selectedAssetsList.value.filter { a -> a.tokenId != asset.tokenId }
        } else {
            newIds = selectedAssetIds.value + asset.tokenId
            newList = selectedAssetsList.value + asset
        }
        selectedAssetIds.value = newIds
        selectedAssetsList.value = newList
        _uiState.update { it.copy(selectedAssetIds = newIds, selectedAssets = newList) }
    }

    fun removeAssetFilter(assetId: String) {
        val newIds = selectedAssetIds.value - assetId
        val newList = selectedAssetsList.value.filter { a -> a.tokenId != assetId }
        selectedAssetIds.value = newIds
        selectedAssetsList.value = newList
        _uiState.update { it.copy(selectedAssetIds = newIds, selectedAssets = newList) }
    }

    fun clearAllFilters() {
        selectedAssetIds.update { emptySet() }
        selectedAssetsList.update { emptyList() }
        _uiState.update { it.copy(selectedAssetIds = emptySet(), selectedAssets = emptyList()) }
    }

    fun confirmAssetSearch() {
        _uiState.update { it.copy(isAssetSearchSheetVisible = false) }
    }

    fun closeSearch() {
        selectedAssetIds.update { emptySet() }
        selectedAssetsList.update { emptyList() }
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
                refreshPendingTransactions(vaultId)
                delay(100.milliseconds) // prevent refresh ui freezing
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun refreshOnEnter() {
        viewModelScope.safeLaunch { refreshPendingTransactions(vaultId) }
    }

    private fun startTimeTicker() {
        viewModelScope.launch {
            while (true) {
                delay(1.minutes)
                currentTime.value = System.currentTimeMillis()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeTransactions() {
        viewModelScope.launch {
            uiState
                .map { it.selectedTab }
                .distinctUntilChanged()
                .flatMapLatest { tab ->
                    transactionHistoryRepository.observeTransactions(
                        vaultId = vaultId,
                        type = tab.toRepositoryType(),
                    )
                }
                .combine(currentTime) { entities, now ->
                    entities.mapNotNull { it.toUiModel(now) }.groupByDate(now)
                }
                .combine(selectedAssetIds) { groups, ids ->
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
                .observeTransactions(vaultId = vaultId, type = TransactionHistoryType.OVERVIEW)
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

    private fun TransactionHistoryTab.toRepositoryType() =
        when (this) {
            TransactionHistoryTab.OVERVIEW -> TransactionHistoryType.OVERVIEW
            TransactionHistoryTab.SWAP -> TransactionHistoryType.SWAPS
            TransactionHistoryTab.SEND -> TransactionHistoryType.SEND
        }

    private fun TransactionHistoryEntity.toUiModel(now: Long): TransactionHistoryItemUiModel? {
        val statusUiModel =
            when (status) {
                TransactionStatus.BROADCASTED -> TransactionStatusUiModel.Broadcasted
                TransactionStatus.PENDING ->
                    TransactionStatusUiModel.Pending(elapsedTime = formatElapsed(now - timestamp))

                TransactionStatus.CONFIRMED -> TransactionStatusUiModel.Confirmed
                TransactionStatus.FAILED ->
                    TransactionStatusUiModel.Failed(UiText.DynamicString(failureReason.orEmpty()))
                // NotFound is transient — the indexer has not seen the tx yet. Render as Pending.
                TransactionStatus.NotFound ->
                    TransactionStatusUiModel.Pending(elapsedTime = formatElapsed(now - timestamp))
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
                )
            }
    }

    private fun formatElapsed(elapsedMs: Long): UiText {
        // Coerce against wall-clock skew (NTP correction, DST, manual date change).
        val minutes = elapsedMs.coerceAtLeast(0L) / 60_000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            days > 0 ->
                UiText.FormattedText(R.string.transaction_history_elapsed_days, listOf(days))
            hours > 0 ->
                UiText.FormattedText(R.string.transaction_history_elapsed_hours, listOf(hours))
            minutes > 0 ->
                UiText.FormattedText(R.string.transaction_history_elapsed_minutes, listOf(minutes))
            else -> UiText.StringResource(R.string.transaction_history_elapsed_just_now)
        }
    }
}
