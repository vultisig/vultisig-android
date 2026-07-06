package com.vultisig.wallet.ui.models.defi

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.api.chains.ton.TonStakingApi
import com.vultisig.wallet.data.api.chains.ton.TonStakingPoolEntryJson
import com.vultisig.wallet.data.blockchain.ton.TonNominatorPool
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.cosmosstaking.cachedSpendableBalance
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.deposit.DepositGasFeeHelper
import com.vultisig.wallet.ui.models.deposit.submit.TonStakingAction
import com.vultisig.wallet.ui.models.deposit.submit.buildTonStakingTransaction
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber
import wallet.core.jni.TONAddressConverter

/** A verified nominator pool the picker renders and the user can stake into. */
@Immutable
internal data class TonPoolUiModel(
    val address: String,
    val name: String,
    /** Annual percentage yield as a percentage (e.g. `13.27` = 13.27%). */
    val apy: Double,
    /** Minimum stake in human-decimal TON. */
    val minStake: BigDecimal,
    val verified: Boolean,
) {
    /** Pool name, or a short `0:a447…a16a` fallback when unnamed. */
    val displayName: String
        get() = name.ifBlank { shortTonPoolAddress(address) }
}

internal fun shortTonPoolAddress(address: String): String =
    if (address.length > 14) "${address.take(8)}…${address.takeLast(4)}" else address

@Immutable
internal data class TonStakeUiState(
    val ticker: String = "",
    val stakeableBalance: BigDecimal = BigDecimal.ZERO,
    val pools: List<TonPoolUiModel> = emptyList(),
    val isLoadingPools: Boolean = false,
    val selectedPool: TonPoolUiModel? = null,
    val isShowingPicker: Boolean = false,
    val percentageSelected: Int = -1,
    /** Effective minimum the amount must clear: the pool minimum plus the ~1 TON deposit buffer. */
    val requiredMinStake: BigDecimal = DEFAULT_MIN_STAKE + DEPOSIT_BUFFER,
    val isSubmitting: Boolean = false,
    val errorMessage: UiText? = null,
) {
    companion object {
        /** Conservative floor before a pool is picked. Mirrors iOS `defaultMinStake`. */
        val DEFAULT_MIN_STAKE: BigDecimal = BigDecimal.ONE

        /** ~1 TON deposit commission a stake must clear on top of the pool minimum. */
        val DEPOSIT_BUFFER: BigDecimal = BigDecimal.ONE
    }
}

/**
 * View-model for the dedicated TON nominator-pool stake screen (mirrors iOS
 * `TonStakeTransactionViewModel` / macOS "Stake TON"). Owns the amount + pool-selection form and,
 * on submit, delegates to the shared fund-safe [buildTonStakingTransaction] core (bounceable send,
 * per-implementation comment, min-stake + buffer), persists the deposit transaction, and routes to
 * the existing verify screen. Reuses [DepositGasFeeHelper] for fees — no bespoke fee logic.
 */
@HiltViewModel
internal class TonStakeViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val tonStakingApi: TonStakingApi,
    private val accountsRepository: AccountsRepository,
    private val balanceRepository: BalanceRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val depositGasFeeHelper: DepositGasFeeHelper,
    private val transactionRepository:
        com.vultisig.wallet.data.repositories.DepositTransactionRepository,
    private val navigator: Navigator<com.vultisig.wallet.ui.navigation.Destination>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val route: Route.TonStake = savedStateHandle.toRoute()

    val amountFieldState = TextFieldState()
    val searchTextFieldState = TextFieldState()

    private val _state = MutableStateFlow(TonStakeUiState())
    val state: StateFlow<TonStakeUiState> = _state.asStateFlow()

    private var coin: Coin? = null
    private var allPools: List<TonPoolUiModel> = emptyList()

    init {
        loadCoin()
        loadPools()
        observeSearch()
    }

    fun openPoolPicker() {
        searchTextFieldState.clearText()
        _state.update { it.copy(isShowingPicker = true, pools = allPools) }
    }

    fun closePoolPicker() {
        _state.update { it.copy(isShowingPicker = false) }
    }

    /**
     * Filter the pool list off the search field state so the typed query survives recomposition.
     */
    private fun observeSearch() {
        viewModelScope.safeLaunch {
            searchTextFieldState.textAsFlow().collect { query ->
                val needle = query.trim().toString().lowercase()
                val visible =
                    if (needle.isEmpty()) allPools
                    else
                        allPools.filter {
                            it.name.lowercase().contains(needle) ||
                                it.address.lowercase().contains(needle)
                        }
                _state.update { it.copy(pools = visible) }
            }
        }
    }

    fun onPoolSelected(pool: TonPoolUiModel) {
        _state.update {
            it.copy(
                selectedPool = pool,
                isShowingPicker = false,
                requiredMinStake = pool.minStake + TonStakeUiState.DEPOSIT_BUFFER,
                errorMessage = null,
            )
        }
    }

    /** 25/50/75/100% chip → fill the amount field from the stakeable balance. */
    fun onPercentageChange(percent: Int) {
        _state.update { it.copy(percentageSelected = percent) }
        val available = _state.value.stakeableBalance
        if (available <= BigDecimal.ZERO) return
        val amount =
            available
                .multiply(BigDecimal(percent))
                .divide(BigDecimal(100), 9, RoundingMode.DOWN)
                .stripTrailingZeros()
                .toPlainString()
        amountFieldState.edit { replace(0, length, amount) }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun back() {
        viewModelScope.safeLaunch { navigator.back() }
    }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return

        val pool = current.selectedPool
        if (pool == null) {
            setError(R.string.ton_staking_select_pool.asUiText())
            return
        }

        val amount = amountFieldState.text.toString().trim().toBigDecimalOrNull()
        if (amount == null || amount < current.requiredMinStake) {
            // The screen already surfaces the "minimum stake" hint; block here as a backstop.
            return
        }

        _state.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to build TON stake transaction")
                setError(R.string.dialog_default_error_body.asUiText())
            }
        ) {
            try {
                val nodeAddressFieldState = TextFieldState(pool.address)
                val tokenAmountFieldState = TextFieldState(amountFieldState.text.toString())

                val transaction =
                    buildTonStakingTransaction(
                        action = TonStakingAction.DEPOSIT,
                        vaultIdProvider = { route.vaultId },
                        chainProvider = { Chain.Ton },
                        stateProvider = { DepositFormUiModel(depositChain = Chain.Ton) },
                        nodeAddressFieldState = nodeAddressFieldState,
                        tokenAmountFieldState = tokenAmountFieldState,
                        accountsRepository = accountsRepository,
                        tonStakingApi = tonStakingApi,
                        toBounceableAddress = ::toTonBounceableAddress,
                        blockChainSpecificRepository = blockChainSpecificRepository,
                        calculateGasFee = { chain, token, src ->
                            depositGasFeeHelper.calculateGasFee(route.vaultId, chain, token, src)
                        },
                        getFeesFiatValue = { specific, gasFee, token ->
                            depositGasFeeHelper.getFeesFiatValue(Chain.Ton, specific, gasFee, token)
                        },
                    )

                transactionRepository.addTransaction(transaction)
                navigator.route(
                    Route.VerifyDeposit(vaultId = route.vaultId, transactionId = transaction.id)
                )
                _state.update { it.copy(isSubmitting = false) }
            } catch (e: InvalidTransactionDataException) {
                setError(e.text)
            }
        }
    }

    private fun loadCoin() {
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to load TON coin for stake flow")
                setError(R.string.ton_defi_error_ton_not_in_vault.asUiText())
            }
        ) {
            val vault =
                withContext(ioDispatcher) { vaultRepository.get(route.vaultId) }
                    ?: return@safeLaunch setError(
                        R.string.ton_defi_error_ton_not_in_vault.asUiText()
                    )
            val nativeCoin =
                vault.coins.firstOrNull { it.chain == Chain.Ton && it.isNativeToken }
                    ?: return@safeLaunch setError(
                        R.string.ton_defi_error_ton_not_in_vault.asUiText()
                    )
            coin = nativeCoin

            // Reserve the actual deposit network fee (~0.05 TON, iOS `TonHelper.defaultFee`) off
            // the
            // spendable balance, not the 0.2 TON withdraw signal fee — reserving too much clamps a
            // wallet just above the pool minimum below it and wrongly disables Continue.
            val gasFee =
                withContext(ioDispatcher) {
                    depositGasFeeHelper.calculateGasFee(
                        route.vaultId,
                        Chain.Ton,
                        nativeCoin,
                        nativeCoin.address,
                    )
                }
            val gasReservation = BigDecimal(gasFee.value).movePointLeft(nativeCoin.decimal)
            val total =
                withContext(ioDispatcher) { balanceRepository.cachedSpendableBalance(nativeCoin) }
            val stakeable = (total - gasReservation).coerceAtLeast(BigDecimal.ZERO)

            _state.update { it.copy(ticker = nativeCoin.ticker, stakeableBalance = stakeable) }
        }
    }

    private fun loadPools() {
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to load TON staking pools")
                // Clear the spinner so the picker falls back to its empty state instead of
                // spinning forever when the pools request fails.
                _state.update { it.copy(isLoadingPools = false) }
            }
        ) {
            _state.update { it.copy(isLoadingPools = true) }
            val decimals = coin?.decimal ?: com.vultisig.wallet.data.models.Coins.Ton.TON.decimal
            allPools =
                withContext(ioDispatcher) {
                    filterAndSortPools(tonStakingApi.getStakingPools(), decimals)
                }
            _state.update { it.copy(isLoadingPools = false, pools = allPools) }
            prefillExistingPool(decimals)
        }
    }

    /** For an add-more stake, preselect the position's pool (fetch it if capacity-filtered out). */
    private suspend fun prefillExistingPool(decimals: Int) {
        val poolAddress = route.poolAddress?.takeIf { it.isNotBlank() } ?: return
        if (_state.value.selectedPool != null) return
        val existing =
            allPools.firstOrNull { it.address == poolAddress }
                ?: runCatching {
                        withContext(ioDispatcher) { tonStakingApi.getStakingPool(poolAddress) }
                    }
                    .getOrNull()
                    ?.let { info ->
                        TonPoolUiModel(
                            address = poolAddress,
                            name = info.name ?: "",
                            apy = info.apy ?: 0.0,
                            minStake = BigDecimal(info.minStake ?: 0L).movePointLeft(decimals),
                            verified = true,
                        )
                    }
        // Re-check after the async lookup: if the user picked a pool in the picker while the
        // fetch was in flight, keep their choice rather than clobbering it with the prefill.
        if (existing != null && _state.value.selectedPool == null) onPoolSelected(existing)
    }

    private fun setError(message: UiText) {
        _state.update { it.copy(errorMessage = message, isSubmitting = false) }
    }

    companion object {
        /**
         * Keeps verified **nominator** pools that still have capacity, sorted by APY descending.
         * Liquid-staking pools (e.g. Tonstakers / `liquidTF`) are excluded because the `"d"`/`"w"`
         * text-comment deposit mechanism can't stake into them. Pure so the sort/filter contract
         * can be pinned independent of the network layer.
         */
        fun filterAndSortPools(
            entries: List<TonStakingPoolEntryJson>,
            decimals: Int,
        ): List<TonPoolUiModel> =
            entries
                .filter { it.verified }
                .filter { TonNominatorPool.isNominatorImplementation(it.implementation) }
                .filter { hasCapacity(it) }
                .sortedByDescending { it.apy }
                .map { entry ->
                    TonPoolUiModel(
                        address = entry.address,
                        name = entry.name,
                        apy = entry.apy,
                        minStake = BigDecimal(entry.minStake).movePointLeft(decimals),
                        verified = entry.verified,
                    )
                }

        /**
         * Whether the pool has room for another nominator. A pool at capacity would reject a stake,
         * so it is hidden. Missing counts are treated as "has room" rather than filtering it out.
         */
        private fun hasCapacity(entry: TonStakingPoolEntryJson): Boolean {
            val current = entry.currentNominators
            val max = entry.maxNominators
            if (current == null || max == null || max <= 0) return true
            return current < max
        }
    }
}

/**
 * Converts a TON pool address (raw `0:…` or user-friendly) to the bounceable `EQ…` form required
 * for nominator-pool deposits — a non-bounceable message a pool rejects is absorbed (lost).
 */
private fun toTonBounceableAddress(address: String): String =
    TONAddressConverter.toUserFriendly(address, /* bounceable= */ true, /* testnet= */ false)

private fun SavedStateHandle.toRoute(): Route.TonStake =
    Route.TonStake(
        vaultId = checkNotNull(get<String>("vaultId")) { "vaultId is required" },
        poolAddress = get<String>("poolAddress"),
    )
