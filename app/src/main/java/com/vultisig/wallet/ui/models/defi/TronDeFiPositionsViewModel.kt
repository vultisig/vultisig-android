package com.vultisig.wallet.ui.models.defi

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.models.calculateResourceStats
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.DeFiTab
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Immutable
data class TronDeFiUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val availableBalanceTrx: String = "0",
    val totalAmountPrice: String = "",
    val frozenBandwidthTrx: String = "0",
    val frozenEnergyTrx: String = "0",
    val unfreezingTrx: String = "0",
    val availableBandwidth: Long = 0L,
    val totalBandwidth: Long = 0L,
    val availableEnergy: Long = 0L,
    val totalEnergy: Long = 0L,
    val bandwidthProgress: Float = 0f,
    val energyProgress: Float = 0f,
    val pendingWithdrawals: List<TronPendingWithdrawalUiModel> = emptyList(),
    val isBalanceVisible: Boolean = true,
    val selectedTab: Int = DeFiTab.STAKED.displayNameRes,
    val showPositionSelectionDialog: Boolean = false,
)

@Immutable
data class TronPendingWithdrawalUiModel(
    val amountTrx: String,
    val timeRemaining: String,
    val isClaimable: Boolean,
)

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

    private val _state = MutableStateFlow(TronDeFiUiState())
    val state: StateFlow<TronDeFiUiState> = _state.asStateFlow()

    private var vaultId: VaultId = ""

    fun setData(vaultId: VaultId) {
        this.vaultId = vaultId
        loadData()
    }

    fun refresh() {
        loadData()
    }

    private fun loadData() {
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to load Tron DeFi data")
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        ) {
            _state.update { it.copy(isLoading = true, error = null) }

            val vault = vaultRepository.get(vaultId) ?: return@safeLaunch
            val trxCoin =
                vault.coins.find { it.chain == Chain.Tron && it.isNativeToken } ?: return@safeLaunch

            val isBalanceVisible = balanceVisibilityRepository.getVisibility(vaultId)

            val (account, resource) =
                coroutineScope {
                    val accountDeferred = async { tronApi.getAccount(trxCoin.address) }
                    val resourceDeferred = async { tronApi.getAccountResource(trxCoin.address) }
                    Pair(accountDeferred.await(), resourceDeferred.await())
                }

            val sunPerTrx = BigDecimal(1_000_000)
            val availableBalanceTrx =
                BigDecimal(account.balance ?: 0L).divide(sunPerTrx).setScale(6, RoundingMode.DOWN)
            val frozenBandwidthTrx =
                BigDecimal(account.frozenBandwidthSun)
                    .divide(sunPerTrx)
                    .setScale(6, RoundingMode.DOWN)
            val frozenEnergyTrx =
                BigDecimal(account.frozenEnergySun).divide(sunPerTrx).setScale(6, RoundingMode.DOWN)
            val unfreezingTrx =
                BigDecimal(account.unfreezingTotalSun)
                    .divide(sunPerTrx)
                    .setScale(6, RoundingMode.DOWN)

            val stats = resource.calculateResourceStats()
            val bandwidthProgress =
                if (stats.totalBandwidth > 0)
                    stats.availableBandwidth.toFloat() / stats.totalBandwidth.toFloat()
                else 0f
            val energyProgress =
                if (stats.totalEnergy > 0)
                    stats.availableEnergy.toFloat() / stats.totalEnergy.toFloat()
                else 0f

            val now = System.currentTimeMillis()
            val pendingWithdrawals =
                (account.unfrozenV2 ?: emptyList())
                    .mapNotNull { entry ->
                        val amountSun = entry.unfreezeAmount ?: return@mapNotNull null
                        val expireTimeMs = entry.unfreezeExpireTime ?: return@mapNotNull null
                        val amountTrx =
                            BigDecimal(amountSun).divide(sunPerTrx).setScale(6, RoundingMode.DOWN)
                        val remaining = expireTimeMs - now
                        val isClaimable = remaining <= 0
                        TronPendingWithdrawalUiModel(
                            amountTrx = amountTrx.toPlainString(),
                            timeRemaining = formatTimeRemaining(remaining),
                            isClaimable = isClaimable,
                        )
                    }
                    .sortedWith(compareBy({ !it.isClaimable }))

            val currency = appCurrencyRepository.currency.first()
            val currencyFormat =
                withContext(Dispatchers.IO) { appCurrencyRepository.getCurrencyFormat() }
            val trxPrice =
                tokenPriceRepository.getCachedPrice(tokenId = trxCoin.id, appCurrency = currency)
                    ?: BigDecimal.ZERO
            val totalAmountPrice = currencyFormat.format(availableBalanceTrx.multiply(trxPrice))

            _state.update {
                it.copy(
                    isLoading = false,
                    availableBalanceTrx = availableBalanceTrx.toPlainString(),
                    totalAmountPrice = totalAmountPrice,
                    frozenBandwidthTrx = frozenBandwidthTrx.toPlainString(),
                    frozenEnergyTrx = frozenEnergyTrx.toPlainString(),
                    unfreezingTrx = unfreezingTrx.toPlainString(),
                    availableBandwidth = stats.availableBandwidth,
                    totalBandwidth = stats.totalBandwidth,
                    availableEnergy = stats.availableEnergy,
                    totalEnergy = stats.totalEnergy,
                    bandwidthProgress = bandwidthProgress,
                    energyProgress = energyProgress,
                    pendingWithdrawals = pendingWithdrawals,
                    isBalanceVisible = isBalanceVisible,
                )
            }
        }
    }

    fun onTabSelected(tab: DeFiTab) {
        _state.update { it.copy(selectedTab = tab.displayNameRes) }
    }

    fun setPositionSelectionDialogVisibility(visible: Boolean) {
        _state.update { it.copy(showPositionSelectionDialog = visible) }
    }

    fun onClickFreeze(resourceType: String) {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId) ?: return@launch
            val trxCoin =
                vault.coins.find { it.chain == Chain.Tron && it.isNativeToken } ?: return@launch
            navigator.route(
                Route.Send(
                    vaultId = vaultId,
                    chainId = Chain.Tron.id,
                    tokenId = trxCoin.id,
                    address = trxCoin.address,
                    memo = "FREEZE:$resourceType",
                )
            )
        }
    }

    fun onClickUnfreeze(resourceType: String) {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId) ?: return@launch
            val trxCoin =
                vault.coins.find { it.chain == Chain.Tron && it.isNativeToken } ?: return@launch
            navigator.route(
                Route.Send(
                    vaultId = vaultId,
                    chainId = Chain.Tron.id,
                    tokenId = trxCoin.id,
                    address = trxCoin.address,
                    memo = "UNFREEZE:$resourceType",
                )
            )
        }
    }

    fun onBackClick() {
        viewModelScope.launch { navigator.navigate(Destination.Back) }
    }

    private fun formatTimeRemaining(remainingMs: Long): String {
        if (remainingMs <= 0) return "Ready to claim"
        val days = remainingMs / (1000L * 60 * 60 * 24)
        val hours = (remainingMs % (1000L * 60 * 60 * 24)) / (1000L * 60 * 60)
        return if (days > 0) {
            "$days days, $hours hrs"
        } else {
            val minutes = (remainingMs % (1000L * 60 * 60)) / (1000L * 60)
            "$hours hrs, $minutes min"
        }
    }
}
