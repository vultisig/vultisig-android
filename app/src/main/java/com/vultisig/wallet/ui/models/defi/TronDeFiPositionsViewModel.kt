package com.vultisig.wallet.ui.models.defi

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.models.TronAccountJson
import com.vultisig.wallet.data.api.models.TronAccountResourceJson
import com.vultisig.wallet.data.api.models.calculateResourceStats
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.DeFiTab
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.screens.v2.defi.model.PositionUiModelDialog
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import timber.log.Timber

private const val TRON_KEY = "TRON"
private const val SUN_PER_TRX = 1_000_000L

private fun Long.sunToTrx(): BigDecimal =
    BigDecimal(this).divide(BigDecimal(SUN_PER_TRX)).setScale(6, RoundingMode.DOWN)

private val TRON_STAKE_POSITIONS_DIALOG =
    listOf(
        PositionUiModelDialog(logo = getCoinLogo("tron"), ticker = "Tron", positionKey = TRON_KEY)
    )

private val TRON_DEFAULT_SELECTED_POSITIONS = listOf(TRON_KEY)

@Immutable
internal data class TronStakingUiModel(
    val totalAmountPrice: String = "",
    val frozenTotalPrice: String = "",
    val frozenTotalTrx: String = "",
    val availableBandwidth: Long = 0L,
    val totalBandwidth: Long = 0L,
    val availableEnergy: Long = 0L,
    val totalEnergy: Long = 0L,
    val pendingWithdrawals: List<TronPendingWithdrawalUiModel> = emptyList(),
    val hasFrozenBalance: Boolean = false,
)

@Immutable
internal sealed interface TronDeFiUiState {
    data object Loading : TronDeFiUiState

    @Immutable data class Error(val error: UiText) : TronDeFiUiState

    @Immutable
    data class Success(
        val tronData: TronStakingUiModel,
        val isBalanceVisible: Boolean = true,
        val selectedTab: DeFiTab = DeFiTab.STAKED,
        val showPositionSelectionDialog: Boolean = false,
        val stakePositionsDialog: List<PositionUiModelDialog> = TRON_STAKE_POSITIONS_DIALOG,
        val selectedPositions: List<String> = TRON_DEFAULT_SELECTED_POSITIONS,
        val tempSelectedPositions: List<String> = TRON_DEFAULT_SELECTED_POSITIONS,
    ) : TronDeFiUiState
}

@Immutable data class TronPendingWithdrawalUiModel(val amountTrx: String, val expiryEpochMs: Long)

internal enum class TronAction(val defiType: DeFiNavActions) {
    FREEZE(DeFiNavActions.FREEZE_TRX),
    UNFREEZE(DeFiNavActions.UNFREEZE_TRX),
}

@HiltViewModel
internal class TronDeFiPositionsViewModel
@Inject
constructor(
    private val vaultRepository: VaultRepository,
    private val tronApi: TronApi,
    private val balanceVisibilityRepository: BalanceVisibilityRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val _state = MutableStateFlow<TronDeFiUiState>(TronDeFiUiState.Loading)
    val state: StateFlow<TronDeFiUiState> = _state.asStateFlow()

    private var vaultId: VaultId = ""
    private var cachedTrxCoin: Coin? = null
    private var loadJob: Job? = null

    fun setData(vaultId: VaultId) {
        this.vaultId = vaultId
        loadData(vaultId)
    }

    fun refresh() {
        if (vaultId.isNotEmpty()) loadData(vaultId)
    }

    private fun loadData(vaultId: VaultId) {
        loadJob?.cancel()
        loadJob =
            viewModelScope.safeLaunch(
                onError = { e ->
                    Timber.e(e, "Failed to load Tron DeFi data")
                    _state.value =
                        TronDeFiUiState.Error(
                            e.message?.asUiText()
                                ?: R.string.error_view_default_description.asUiText()
                        )
                }
            ) {
                _state.value = TronDeFiUiState.Loading

                // Resolve the TRX coin for this vault
                val trxCoin = findTrxCoin(vaultId)
                cachedTrxCoin = trxCoin
                if (trxCoin == null) {
                    _state.value =
                        TronDeFiUiState.Error(R.string.tron_defi_error_trx_not_in_vault.asUiText())
                    return@safeLaunch
                }

                val isBalanceVisible = balanceVisibilityRepository.getVisibility(vaultId)

                // Fetch account state and resource usage in parallel
                val (account, resource) =
                    coroutineScope {
                        val accountDeferred = async { tronApi.getAccount(trxCoin.address) }
                        val resourceDeferred = async { tronApi.getAccountResource(trxCoin.address) }
                        Pair(accountDeferred.await(), resourceDeferred.await())
                    }

                // Resolve fiat price for display
                val currency = appCurrencyRepository.currency.first()
                val currencyFormat = appCurrencyRepository.getCurrencyFormat()
                val trxPrice =
                    tokenPriceRepository.getCachedPrice(
                        tokenId = trxCoin.id,
                        appCurrency = currency,
                    ) ?: BigDecimal.ZERO

                _state.value =
                    TronDeFiUiState.Success(
                        tronData = buildStakingUiModel(account, resource, trxPrice, currencyFormat),
                        isBalanceVisible = isBalanceVisible,
                    )
            }
    }

    private fun buildStakingUiModel(
        account: TronAccountJson,
        resource: TronAccountResourceJson,
        trxPrice: BigDecimal,
        currencyFormat: NumberFormat,
    ): TronStakingUiModel {
        val availableBalanceTrx = (account.balance ?: 0L).sunToTrx()
        val frozenTotal =
            account.frozenBandwidthSun.sunToTrx().add(account.frozenEnergySun.sunToTrx())

        val stats = resource.calculateResourceStats()
        val pendingWithdrawals = mapPendingWithdrawals(account)

        return TronStakingUiModel(
            totalAmountPrice = currencyFormat.format(availableBalanceTrx.multiply(trxPrice)),
            frozenTotalPrice = currencyFormat.format(frozenTotal.multiply(trxPrice)),
            frozenTotalTrx = frozenTotal.stripTrailingZeros().toPlainString(),
            availableBandwidth = stats.availableBandwidth,
            totalBandwidth = stats.totalBandwidth,
            availableEnergy = stats.availableEnergy,
            totalEnergy = stats.totalEnergy,
            pendingWithdrawals = pendingWithdrawals,
            hasFrozenBalance = frozenTotal > BigDecimal.ZERO,
        )
    }

    private fun mapPendingWithdrawals(
        account: TronAccountJson
    ): List<TronPendingWithdrawalUiModel> =
        (account.unfrozenV2 ?: emptyList())
            .mapNotNull { entry ->
                val amountSun = entry.unfreezeAmount ?: return@mapNotNull null
                val expireTimeMs = entry.unfreezeExpireTime ?: return@mapNotNull null
                TronPendingWithdrawalUiModel(
                    amountTrx = amountSun.sunToTrx().toPlainString(),
                    expiryEpochMs = expireTimeMs,
                )
            }
            .sortedWith(compareBy { it.expiryEpochMs })

    private suspend fun findTrxCoin(vaultId: VaultId) =
        vaultRepository.get(vaultId)?.coins?.find { it.chain == Chain.Tron && it.isNativeToken }

    fun onTabSelected(tab: DeFiTab) {
        _state.update { current ->
            if (current is TronDeFiUiState.Success) current.copy(selectedTab = tab) else current
        }
    }

    fun setPositionSelectionDialogVisibility(visible: Boolean) {
        _state.update { current ->
            if (current is TronDeFiUiState.Success)
                current.copy(
                    showPositionSelectionDialog = visible,
                    tempSelectedPositions = current.selectedPositions,
                )
            else current
        }
    }

    fun onPositionSelectionChange(ticker: String, selected: Boolean) {
        _state.update { current ->
            if (current is TronDeFiUiState.Success) {
                val updated =
                    if (selected) current.tempSelectedPositions + ticker
                    else current.tempSelectedPositions - ticker
                current.copy(tempSelectedPositions = updated)
            } else current
        }
    }

    fun onPositionSelectionDone() {
        _state.update { current ->
            if (current is TronDeFiUiState.Success)
                current.copy(
                    showPositionSelectionDialog = false,
                    selectedPositions = current.tempSelectedPositions,
                )
            else current
        }
    }

    fun onTronAction(action: TronAction) {
        viewModelScope.safeLaunch(
            onError = { e -> Timber.e(e, "Failed to navigate for action %s", action) }
        ) {
            val trxCoin = cachedTrxCoin
            if (trxCoin == null) {
                Timber.w("TRX coin not cached when handling action %s", action)
                refresh()
                return@safeLaunch
            }
            navigator.route(
                Route.Send(
                    vaultId = vaultId,
                    chainId = Chain.Tron.id,
                    tokenId = trxCoin.id,
                    address = trxCoin.address,
                    type = action.defiType.type,
                )
            )
        }
    }

    fun onBackClick() {
        viewModelScope.safeLaunch { navigator.navigate(Destination.Back) }
    }
}
