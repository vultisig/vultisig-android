package com.vultisig.wallet.ui.models.defi

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.chains.ton.TonAccountStakingInfoJson
import com.vultisig.wallet.data.api.chains.ton.TonStakingApi
import com.vultisig.wallet.data.blockchain.ton.TonNominatorPool
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.DeFiTab
import com.vultisig.wallet.ui.screens.v2.defi.formatPercentage
import com.vultisig.wallet.ui.screens.v2.defi.model.PositionUiModelDialog
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import timber.log.Timber

internal const val TON_KEY = "TON"

/** UI model for the TON nominator-pool staking position. */
@Immutable
internal data class TonStakingUiModel(
    val totalAmountPrice: String = "",
    val ticker: String = "",
    val poolName: String = "",
    val stakedDisplay: String = "",
    val stakedFiatDisplay: String = "",
    val apy: String? = null,
    val hasPosition: Boolean = false,
    /**
     * A withdrawal is in flight (`pending_withdraw`/`ready_withdraw` > 0). The pool returns the
     * staked balance at the next validation cycle, so stake/unstake are blocked until then to avoid
     * stacking a second request against the same cycle.
     */
    val isActionLocked: Boolean = false,
    val pendingWithdrawDisplay: String? = null,
    /** Cycle-end as epoch millis; drives the unlock countdown when [isActionLocked]. */
    val unlockEpochMs: Long? = null,
)

/** UI state for the TON DeFi positions screen. */
@Immutable
internal sealed interface TonDeFiUiState {
    /** Shown only on first open when there is nothing to render yet. */
    data object Loading : TonDeFiUiState

    @Immutable data class Error(val error: UiText) : TonDeFiUiState

    @Immutable
    data class Success(
        val tonData: TonStakingUiModel,
        /**
         * A reload is in flight. Mirrors [isActionLocked]'s `loadJob` guard so the card's
         * Stake/Unstake buttons disable in lockstep — otherwise a resume-triggered refresh would
         * leave them Enabled over a stale snapshot while a tap silently no-ops.
         */
        val isReloading: Boolean = false,
        val isBalanceVisible: Boolean = true,
        val selectedTab: DeFiTab = DeFiTab.STAKED,
        val showPositionSelectionDialog: Boolean = false,
        val stakePositionsDialog: List<PositionUiModelDialog> = emptyList(),
        val selectedPositions: List<String> = listOf(TON_KEY),
        val tempSelectedPositions: List<String> = listOf(TON_KEY),
    ) : TonDeFiUiState
}

/**
 * View-model for the native TON nominator-pool staking position on the DeFi/Earn tab.
 *
 * Reads the account's pools from tonapi, treats the largest as the primary position (mirrors
 * [com.vultisig.wallet.data.blockchain.ton.TonDeFiBalanceService] / vultisig-ios
 * `TonStakeInteractor`), and decorates it with the pool name + APY. [onStake]/[onUnstake] navigate
 * to the dedicated [Route.TonStake]/[Route.TonUnstake] screens, which carry the fund-safe
 * transaction core (bounceable send, per-implementation comment, amount rules) built in
 * [buildPositionUiModel]'s sibling stake/unstake view-models.
 */
@HiltViewModel
internal class TonDeFiPositionsViewModel
@Inject
constructor(
    private val vaultRepository: VaultRepository,
    private val tonStakingApi: TonStakingApi,
    private val balanceVisibilityRepository: BalanceVisibilityRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val _state = MutableStateFlow<TonDeFiUiState>(TonDeFiUiState.Loading)
    val state: StateFlow<TonDeFiUiState> = _state.asStateFlow()

    private var vaultId: VaultId = ""
    private var cachedTonCoin: Coin? = null
    /** Address of the pool backing the current position; drives add-more/unstake routing. */
    private var cachedPoolAddress: String? = null
    /** Staked amount shown on the unstake confirmation screen. */
    private var cachedStakedDisplay: String = ""
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
        // Flag the in-flight reload on an already-rendered screen so the buttons disable while the
        // isActionLocked() guard is closed by the active loadJob.
        _state.update { current ->
            if (current is TonDeFiUiState.Success) current.copy(isReloading = true) else current
        }
        loadJob =
            viewModelScope.safeLaunch(
                onError = { e ->
                    Timber.e(e, "Failed to load TON DeFi data")
                    // Keep a screen that already shows data on a background-refresh failure; only
                    // surface the error state when there's nothing rendered yet. Clear the reload
                    // flag so the buttons re-enable once the failed refresh settles.
                    if (_state.value !is TonDeFiUiState.Success) {
                        _state.value =
                            TonDeFiUiState.Error(R.string.error_view_default_description.asUiText())
                    } else {
                        _state.update { current ->
                            if (current is TonDeFiUiState.Success) current.copy(isReloading = false)
                            else current
                        }
                    }
                }
            ) {
                val tonCoin = findTonCoin(vaultId)
                cachedTonCoin = tonCoin
                if (tonCoin == null) {
                    _state.value =
                        TonDeFiUiState.Error(R.string.ton_defi_error_ton_not_in_vault.asUiText())
                    return@safeLaunch
                }

                val isBalanceVisible = balanceVisibilityRepository.getVisibility(vaultId)
                val currency = appCurrencyRepository.currency.first()
                val currencyFormat = appCurrencyRepository.getCurrencyFormat()
                val price = cachedPrice(tonCoin.id, currency)

                val positionDialog =
                    listOf(
                        PositionUiModelDialog(
                            logo = getCoinLogo(tonCoin.logo),
                            ticker = tonCoin.ticker,
                            positionKey = TON_KEY,
                        )
                    )

                val primary =
                    tonStakingApi.getNominatorPools(tonCoin.address).maxByOrNull {
                        it.stakedTotal()
                    }

                val staked = primary?.stakedTotal() ?: BigInteger.ZERO
                val hasStake = staked > BigInteger.ZERO

                // Only a manageable nominator-pool position (built successfully below) drives the
                // add-more/unstake routing; anything else clears the caches so no failing flow is
                // offered.
                val position =
                    if (primary != null && hasStake) {
                        buildPositionUiModel(
                            staked = staked,
                            pendingWithdraw =
                                BigInteger.valueOf(primary.pendingWithdraw + primary.readyWithdraw),
                            poolAddress = primary.pool,
                            coin = tonCoin,
                            price = price,
                            currencyFormat = currencyFormat,
                        )
                    } else {
                        null
                    }

                val tonData =
                    if (position != null) {
                        cachedPoolAddress = primary!!.pool
                        cachedStakedDisplay =
                            "${staked.toBigDecimal().movePointLeft(tonCoin.decimal).stripTrailingZeros().toPlainString()} ${tonCoin.ticker}"
                        position
                    } else {
                        cachedPoolAddress = null
                        cachedStakedDisplay = ""
                        // Show a zeroed position card (with a disabled Unstake) rather than an
                        // empty state, mirroring iOS/macOS which always render the card.
                        TonStakingUiModel(
                            ticker = tonCoin.ticker,
                            stakedDisplay = "0 ${tonCoin.ticker}",
                            stakedFiatDisplay = currencyFormat.format(BigDecimal.ZERO),
                            hasPosition = false,
                        )
                    }

                _state.value =
                    TonDeFiUiState.Success(
                        tonData = tonData,
                        isBalanceVisible = isBalanceVisible,
                        stakePositionsDialog = positionDialog,
                    )
            }
    }

    private suspend fun buildPositionUiModel(
        staked: BigInteger,
        pendingWithdraw: BigInteger,
        poolAddress: String,
        coin: Coin,
        price: BigDecimal,
        currencyFormat: NumberFormat,
    ): TonStakingUiModel? {
        // Pool metadata (name/apy/cycle) is display-only, so a genuine lookup miss degrades to a
        // short fallback label. Cancellation must still propagate — otherwise a cancelled reload
        // could swallow the exception and let this coroutine publish a stale Success state.
        val poolInfo =
            try {
                tonStakingApi.getStakingPool(poolAddress)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }

        // A position in a pool this app can't stake into (liquid-staking Tonstakers/`liquidTF`)
        // must not offer Stake/Unstake — the `"d"`/`"w"` text-comment deposit only fails deep in
        // buildTonStakingTransaction. Mirror the Stake picker's filterAndSortPools filter. A
        // metadata miss (null implementation) is treated permissively so a transient tonapi failure
        // can't hide a genuine nominator position.
        if (
            poolInfo?.implementation != null &&
                !TonNominatorPool.isNominatorImplementation(poolInfo.implementation)
        ) {
            return null
        }

        val stakedTon = staked.toBigDecimal().movePointLeft(coin.decimal)
        val stakedFiat = currencyFormat.format(stakedTon.multiply(price))
        val isLocked = pendingWithdraw > BigInteger.ZERO

        return TonStakingUiModel(
            totalAmountPrice = stakedFiat,
            ticker = coin.ticker,
            poolName = poolInfo?.name?.takeIf { it.isNotBlank() } ?: shortPool(poolAddress),
            stakedDisplay = "${stakedTon.stripTrailingZeros().toPlainString()} ${coin.ticker}",
            stakedFiatDisplay = stakedFiat,
            // tonapi `apy` is a percentage (13.27 = 13.27%); `formatPercentage` multiplies by 100,
            // so scale to a fraction first.
            apy = poolInfo?.apy?.let { (it / 100).formatPercentage() },
            hasPosition = true,
            isActionLocked = isLocked,
            pendingWithdrawDisplay =
                pendingWithdraw
                    .takeIf { it > BigInteger.ZERO }
                    ?.let {
                        "${it.toBigDecimal().movePointLeft(coin.decimal).stripTrailingZeros().toPlainString()} ${coin.ticker}"
                    },
            unlockEpochMs = poolInfo?.cycleEnd?.takeIf { isLocked }?.let { it * 1000L },
        )
    }

    /** Short `EQ…`-style fallback label when the pool name is unavailable. */
    private fun shortPool(poolAddress: String): String =
        if (poolAddress.length > 12) "${poolAddress.take(6)}…${poolAddress.takeLast(4)}"
        else poolAddress

    private suspend fun cachedPrice(tokenId: String, currency: AppCurrency): BigDecimal =
        tokenPriceRepository.getCachedPrice(tokenId = tokenId, appCurrency = currency)
            ?: BigDecimal.ZERO

    private suspend fun findTonCoin(vaultId: VaultId): Coin? =
        vaultRepository.get(vaultId)?.coins?.find { it.chain == Chain.Ton && it.isNativeToken }

    fun onTabSelected(tab: DeFiTab) {
        _state.update { current ->
            if (current is TonDeFiUiState.Success) current.copy(selectedTab = tab) else current
        }
    }

    fun setPositionSelectionDialogVisibility(visible: Boolean) {
        _state.update { current ->
            if (current is TonDeFiUiState.Success)
                current.copy(
                    showPositionSelectionDialog = visible,
                    tempSelectedPositions = current.selectedPositions,
                )
            else current
        }
    }

    fun onPositionSelectionChange(ticker: String, selected: Boolean) {
        _state.update { current ->
            if (current is TonDeFiUiState.Success) {
                val updated =
                    if (selected) current.tempSelectedPositions + ticker
                    else current.tempSelectedPositions - ticker
                current.copy(tempSelectedPositions = updated)
            } else current
        }
    }

    fun onPositionSelectionDone() {
        _state.update { current ->
            if (current is TonDeFiUiState.Success)
                current.copy(
                    showPositionSelectionDialog = false,
                    selectedPositions = current.tempSelectedPositions,
                )
            else current
        }
    }

    /**
     * A pending withdrawal blocks both stake and unstake until the pool releases the balance. Read
     * the lock from the latest loaded state, and treat an in-flight reload as locked so a
     * resume-triggered refresh right after a Withdraw can't be raced by a stale unlocked snapshot.
     */
    private fun isActionLocked(): Boolean {
        if (loadJob?.isActive == true) return true
        return (_state.value as? TonDeFiUiState.Success)?.tonData?.isActionLocked ?: false
    }

    /**
     * Opens the dedicated Stake screen. An existing position prefills its pool (add-more); a
     * first-time stake leaves the pool unset so the user picks one there — mirroring macOS "Stake
     * TON".
     */
    fun onStake() {
        if (isActionLocked()) return
        viewModelScope.safeLaunch(onError = { e -> Timber.e(e, "Failed to open TON stake") }) {
            if (cachedTonCoin == null) {
                refresh()
                return@safeLaunch
            }
            navigator.route(Route.TonStake(vaultId = vaultId, poolAddress = cachedPoolAddress))
        }
    }

    fun onUnstake() {
        if (isActionLocked()) return
        val poolAddress = cachedPoolAddress ?: return
        viewModelScope.safeLaunch(onError = { e -> Timber.e(e, "Failed to open TON unstake") }) {
            if (cachedTonCoin == null) {
                refresh()
                return@safeLaunch
            }
            navigator.route(
                Route.TonUnstake(
                    vaultId = vaultId,
                    poolAddress = poolAddress,
                    stakedDisplay = cachedStakedDisplay,
                )
            )
        }
    }
}

private fun TonAccountStakingInfoJson.stakedTotal(): BigInteger =
    BigInteger.valueOf(amount) + BigInteger.valueOf(pendingDeposit)
