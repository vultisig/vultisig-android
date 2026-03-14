package com.vultisig.wallet.ui.models

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.db.models.TransactionHistoryEntity
import com.vultisig.wallet.data.db.models.TransactionStatus
import com.vultisig.wallet.data.db.models.TransactionType
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import com.vultisig.wallet.data.repositories.TransactionHistoryType
import com.vultisig.wallet.data.usecases.RefreshPendingTransactionsUseCase
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── UI models ────────────────────────────────────────────────────────────────

enum class TransactionHistoryTab {
    OVERVIEW,
    SWAP,
    SEND,
}

data class TransactionHistoryGroupUiModel(
    val dateLabel: String,
    val transactions: List<TransactionHistoryItemUiModel>,
)

sealed interface TransactionStatusUiModel {
    data object Broadcasted : TransactionStatusUiModel

    data class Pending(val elapsedTime: String) : TransactionStatusUiModel

    data object Confirmed : TransactionStatusUiModel

    data class Failed(val reason: String?) : TransactionStatusUiModel
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
        val fiatValue: String?,
        val fromAddress: String?,
        val toAddress: String?,
        val feeEstimate: String?,
    ) : TransactionHistoryItemUiModel
}

@Immutable
data class TransactionHistoryUiState(
    val selectedTab: TransactionHistoryTab = TransactionHistoryTab.OVERVIEW,
    val groups: List<TransactionHistoryGroupUiModel> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val selectedItem: TransactionHistoryItemUiModel? = null,
)

@HiltViewModel
internal class TransactionHistoryViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val transactionHistoryRepository: TransactionHistoryRepository,
    private val refreshPendingTransactions: RefreshPendingTransactionsUseCase,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val vaultId: String = savedStateHandle.toRoute<Route.TransactionHistory>().vaultId

    private val currentTime = MutableStateFlow(System.currentTimeMillis())

    val uiState = MutableStateFlow(TransactionHistoryUiState())

    init {
        startTimeTicker()
        observeTransactions()
        refreshOnEnter()
    }

    fun selectTab(tab: TransactionHistoryTab) {
        uiState.update { it.copy(selectedTab = tab, isLoading = true) }
    }

    fun back() {
        viewModelScope.launch { navigator.back() }
    }

    fun openDetail(item: TransactionHistoryItemUiModel) {
        uiState.update { it.copy(selectedItem = item) }
    }

    fun dismissDetail() {
        uiState.update { it.copy(selectedItem = null) }
    }

    fun refresh() {
        viewModelScope.launch {
            uiState.update { it.copy(isRefreshing = true) }
            try {
                refreshPendingTransactions(vaultId)
                delay(100.milliseconds) // prevent refresh ui freezing
            } finally {
                uiState.update { it.copy(isRefreshing = false) }
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
                .flatMapLatest { tab ->
                    transactionHistoryRepository.observeTransactions(
                        vaultId = vaultId,
                        type = tab.toRepositoryType(),
                    )
                }
                .combine(currentTime) { entities, now ->
                    entities.map { it.toUiModel(now) }.groupByDate(now)
                }
                .collect { groups ->
                    uiState.update { it.copy(groups = groups, isLoading = false) }
                }
        }
    }

    private fun TransactionHistoryTab.toRepositoryType() =
        when (this) {
            TransactionHistoryTab.OVERVIEW -> TransactionHistoryType.OVERVIEW
            TransactionHistoryTab.SWAP -> TransactionHistoryType.SWAPS
            TransactionHistoryTab.SEND -> TransactionHistoryType.SEND
        }

    private fun TransactionHistoryEntity.toUiModel(now: Long): TransactionHistoryItemUiModel {
        val statusUiModel =
            when (status) {
                TransactionStatus.BROADCASTED -> TransactionStatusUiModel.Broadcasted
                TransactionStatus.PENDING ->
                    TransactionStatusUiModel.Pending(elapsedTime = formatElapsed(now - timestamp))

                TransactionStatus.CONFIRMED -> TransactionStatusUiModel.Confirmed
                TransactionStatus.FAILED -> TransactionStatusUiModel.Failed(failureReason)
            }

        return when (type) {
            TransactionType.SEND ->
                TransactionHistoryItemUiModel.Send(
                    id = id,
                    txHash = txHash,
                    chain = chain,
                    status = statusUiModel,
                    explorerUrl = explorerUrl,
                    timestamp = timestamp,
                    fromAddress = fromAddress.orEmpty(),
                    toAddress = toAddress.orEmpty(),
                    amount = amount.orEmpty(),
                    token = token.orEmpty(),
                    tokenLogo = getCoinLogo(tokenLogo.orEmpty()),
                    fiatValue = fiatValue,
                    provider = provider,
                    feeEstimate = feeEstimate,
                )

            TransactionType.SWAP ->
                TransactionHistoryItemUiModel.Swap(
                    id = id,
                    txHash = txHash,
                    chain = chain,
                    status = statusUiModel,
                    explorerUrl = explorerUrl,
                    timestamp = timestamp,
                    fromToken = fromToken.orEmpty(),
                    fromAmount = fromAmount.orEmpty(),
                    fromChain = fromChain.orEmpty(),
                    fromTokenLogo = getCoinLogo(fromTokenLogo.orEmpty()),
                    toToken = toToken.orEmpty(),
                    toAmount = toAmount.orEmpty(),
                    toChain = toChain.orEmpty(),
                    toTokenLogo = getCoinLogo(toTokenLogo.orEmpty()),
                    provider = provider.orEmpty(),
                    fiatValue = fiatValue,
                    fromAddress = fromAddress,
                    toAddress = toAddress,
                    feeEstimate = feeEstimate,
                )
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
                val label =
                    when (date) {
                        today ->
                            context.getString(
                                R.string.transaction_history_date_today,
                                date.format(labelFormatter),
                            )
                        yesterday ->
                            context.getString(
                                R.string.transaction_history_date_yesterday,
                                date.format(labelFormatter),
                            )
                        else -> date.format(labelFormatter)
                    }
                TransactionHistoryGroupUiModel(dateLabel = label, transactions = items)
            }
    }

    private fun formatElapsed(elapsedMs: Long): String {
        val minutes = elapsedMs / 60_000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            days > 0 -> context.getString(R.string.transaction_history_elapsed_days, days)
            hours > 0 -> context.getString(R.string.transaction_history_elapsed_hours, hours)
            minutes > 0 -> context.getString(R.string.transaction_history_elapsed_minutes, minutes)
            else -> context.getString(R.string.transaction_history_elapsed_just_now)
        }
    }
}
