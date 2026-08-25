package com.vultisig.wallet.ui.models.defi

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.models.TronAccountJson
import com.vultisig.wallet.data.api.models.TronAccountResourceJson
import com.vultisig.wallet.data.api.models.calculateResourceStats
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.blockchain.tron.TRON_WITHDRAW_EXPIRE_UNFREEZE_MEMO
import com.vultisig.wallet.data.blockchain.tron.TronResourceType
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.models.getPubKeyByChain
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.repositories.TronDeFiSnapshot
import com.vultisig.wallet.data.repositories.TronDeFiSnapshotCache
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.DeFiTab
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.screens.v2.defi.model.PositionUiModelDialog
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import com.vultisig.wallet.ui.utils.formatTokenAmount
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val TRON_KEY = "TRON"
private const val SUN_PER_TRX = 1_000_000L

internal fun Long.sunToTrx(): BigDecimal =
    BigDecimal(this).divide(BigDecimal(SUN_PER_TRX)).setScale(6, RoundingMode.DOWN)

private val TRON_STAKE_POSITIONS_DIALOG =
    listOf(
        PositionUiModelDialog(logo = getCoinLogo("tron"), ticker = "Tron", positionKey = TRON_KEY)
    )

private val TRON_DEFAULT_SELECTED_POSITIONS = listOf(TRON_KEY)

/** UI model for the Tron staking/freeze position screen. */
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
    /** Every pending entry summed, matured or not — the figure the card's header carries. */
    val pendingWithdrawalsTotalTrx: String = "",
    val hasFrozenBalance: Boolean = false,
    val hasAvailableBalance: Boolean = false,
)

/** UI state for the Tron DeFi positions screen. */
@Immutable
internal sealed interface TronDeFiUiState {
    /** Loading state, shown only on the first open when there is no cached snapshot to render. */
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
        /** Set while a claim is being priced and staged, so the badge can't fire twice. */
        val isClaimingWithdrawal: Boolean = false,
        /** Transient claim failure, surfaced as a dialog; never replaces the rendered positions. */
        val claimError: UiText? = null,
    ) : TronDeFiUiState
}

@Immutable
data class TronPendingWithdrawalUiModel(
    val amountTrx: String,
    val expiryEpochMs: Long,
    val resourceType: TronResourceType?,
    /**
     * The unrounded sun amount behind [amountTrx]. The claim contract takes no amount, so this is
     * only summed across the expired entries to show the user what a claim will return.
     */
    val amountSun: Long = 0L,
)

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
    private val tronDeFiSnapshotCache: TronDeFiSnapshotCache,
    private val transactionRepository: TransactionRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val feeServiceComposite: FeeServiceComposite,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val _state = MutableStateFlow<TronDeFiUiState>(TronDeFiUiState.Loading)
    val state: StateFlow<TronDeFiUiState> = _state.asStateFlow()

    private var vaultId: VaultId = ""
    private var cachedTrxCoin: Coin? = null
    private var loadJob: Job? = null
    private var claimJob: Job? = null

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
                    // Don't blow away a screen that's already showing data on a background-refresh
                    // failure; only surface the error state when there's nothing rendered yet.
                    if (_state.value !is TronDeFiUiState.Success) {
                        _state.value =
                            TronDeFiUiState.Error(
                                R.string.error_view_default_description.asUiText()
                            )
                    }
                }
            ) {
                // Resolve the TRX coin for this vault
                val trxCoin = findTrxCoin(vaultId)
                cachedTrxCoin = trxCoin
                if (trxCoin == null) {
                    _state.value =
                        TronDeFiUiState.Error(R.string.tron_defi_error_trx_not_in_vault.asUiText())
                    return@safeLaunch
                }

                val isBalanceVisible = balanceVisibilityRepository.getVisibility(vaultId)
                val currency = appCurrencyRepository.currency.first()
                val currencyFormat = appCurrencyRepository.getCurrencyFormat()

                // Render the cached snapshot immediately so reopening doesn't flash skeletons;
                // only show the loading skeleton when there's nothing cached to display.
                val cached = tronDeFiSnapshotCache.read(trxCoin.address)
                if (cached != null) {
                    val cachedPrice = trxCachedPrice(trxCoin.id, currency)
                    publishLoaded(
                        tronData =
                            buildStakingUiModel(
                                cached.account,
                                cached.resource,
                                cachedPrice,
                                currencyFormat,
                            ),
                        isBalanceVisible = isBalanceVisible,
                    )
                } else {
                    _state.value = TronDeFiUiState.Loading
                }

                // Fetch fresh account state and resource usage in parallel
                val (account, resource) =
                    coroutineScope {
                        val accountDeferred = async { tronApi.getAccount(trxCoin.address) }
                        val resourceDeferred = async { tronApi.getAccountResource(trxCoin.address) }
                        Pair(accountDeferred.await(), resourceDeferred.await())
                    }

                val trxPrice = trxCachedPrice(trxCoin.id, currency)

                publishLoaded(
                    tronData = buildStakingUiModel(account, resource, trxPrice, currencyFormat),
                    isBalanceVisible = isBalanceVisible,
                )

                // Cache for the next open within this session.
                tronDeFiSnapshotCache.write(
                    trxCoin.address,
                    TronDeFiSnapshot(account = account, resource = resource),
                )
            }
    }

    /**
     * Swaps freshly loaded chain data into the rendered state without touching the fields the user
     * owns — a claim in flight, its error dialog, the position picker. Rebuilding [Success] from
     * scratch would reset those to their defaults whenever a pull-to-refresh or a resume landed
     * mid-claim, re-enabling the claim button and dismissing the error the user hadn't read yet.
     */
    private fun publishLoaded(tronData: TronStakingUiModel, isBalanceVisible: Boolean) {
        _state.update { current ->
            (current as? TronDeFiUiState.Success)?.copy(
                tronData = tronData,
                isBalanceVisible = isBalanceVisible,
                // Preserve claim-related fields across refreshes
                isClaimingWithdrawal = current.isClaimingWithdrawal,
                claimError = current.claimError,
            ) ?: TronDeFiUiState.Success(tronData = tronData, isBalanceVisible = isBalanceVisible)
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
        // Same total the DeFi tab's aggregator reports, read from the one shared property so the
        // header and the wallet-wide roll-up cannot drift apart again (#5482).
        val defiTotal = account.defiLockedTotalSun.sunToTrx()

        val stats = resource.calculateResourceStats()
        val pendingWithdrawals = mapPendingWithdrawals(account)

        return TronStakingUiModel(
            totalAmountPrice = currencyFormat.format(defiTotal.multiply(trxPrice)),
            frozenTotalPrice = currencyFormat.format(frozenTotal.multiply(trxPrice)),
            frozenTotalTrx = frozenTotal.stripTrailingZeros().formatTokenAmount(),
            availableBandwidth = stats.availableBandwidth,
            totalBandwidth = stats.totalBandwidth,
            availableEnergy = stats.availableEnergy,
            totalEnergy = stats.totalEnergy,
            pendingWithdrawals = pendingWithdrawals,
            pendingWithdrawalsTotalTrx =
                account.unfreezingTotalSun.sunToTrx().stripTrailingZeros().formatTokenAmount(),
            hasFrozenBalance = frozenTotal > BigDecimal.ZERO,
            hasAvailableBalance = availableBalanceTrx > BigDecimal.ZERO,
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
                    amountTrx = amountSun.sunToTrx().stripTrailingZeros().formatTokenAmount(),
                    expiryEpochMs = expireTimeMs,
                    resourceType = entry.type.toTronResourceType(),
                    amountSun = amountSun,
                )
            }
            .sortedWith(compareBy { it.expiryEpochMs })

    private fun String?.toTronResourceType(): TronResourceType? =
        when (this) {
            "BANDWIDTH" -> TronResourceType.BANDWIDTH
            "ENERGY" -> TronResourceType.ENERGY
            else -> null
        }

    private suspend fun trxCachedPrice(tokenId: String, currency: AppCurrency): BigDecimal =
        tokenPriceRepository.getCachedPrice(tokenId = tokenId, appCurrency = currency)
            ?: BigDecimal.ZERO

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

    /**
     * Stages the `WithdrawExpireUnfreeze` claim that returns already-unlocked TRX to the spendable
     * balance and hands it to the standard Verify -> TSS flow.
     *
     * The contract takes no amount — it sweeps every entry whose unlock has elapsed — so the
     * expired rows are summed here purely so Verify shows what the claim returns, and which row was
     * tapped has no bearing on what gets signed.
     */
    fun onClaimExpiredWithdrawals() {
        if (claimJob?.isActive == true) return
        val trxCoin = cachedTrxCoin ?: return
        val current = _state.value as? TronDeFiUiState.Success ?: return

        val claimableSun =
            current.tronData.pendingWithdrawals
                .filter { it.expiryEpochMs <= System.currentTimeMillis() }
                .sumOf { it.amountSun }
        if (claimableSun <= 0L) return

        claimJob =
            viewModelScope.safeLaunch(
                onError = { e ->
                    Timber.e(e, "Failed to stage Tron withdraw-expire-unfreeze")
                    updateSuccess {
                        it.copy(
                            isClaimingWithdrawal = false,
                            claimError = R.string.error_view_default_description.asUiText(),
                        )
                    }
                }
            ) {
                updateSuccess { it.copy(isClaimingWithdrawal = true, claimError = null) }
                try {
                    val vault = vaultRepository.get(vaultId) ?: error("Vault not found: $vaultId")

                    val fees =
                        withContext(Dispatchers.IO) {
                            feeServiceComposite.calculateFees(
                                Transfer(
                                    coin = trxCoin,
                                    vault =
                                        VaultData(
                                            vaultHexPublicKey = vault.getPubKeyByChain(Chain.Tron),
                                            vaultHexChainCode = vault.hexChainCode,
                                        ),
                                    amount = BigInteger.ZERO,
                                    to = trxCoin.address,
                                    memo = TRON_WITHDRAW_EXPIRE_UNFREEZE_MEMO,
                                )
                            )
                        }
                    val gasFee = TokenValue(value = fees.amount, token = trxCoin)

                    // The claim is paid out of the spendable balance — precisely what the TRX being
                    // claimed is not yet part of — so a vault with everything staked can reach here
                    // unable to afford its own claim. Reject before a keysign ceremony that could
                    // only fail at broadcast.
                    val liquidSun =
                        withContext(Dispatchers.IO) { tronApi.getAccount(trxCoin.address) }.balance
                            ?: 0L
                    if (BigInteger.valueOf(liquidSun) < gasFee.value) {
                        updateSuccess {
                            it.copy(
                                isClaimingWithdrawal = false,
                                claimError =
                                    UiText.FormattedText(
                                        R.string.send_error_insufficient_native_balance_with_fees,
                                        listOf(trxCoin.ticker),
                                    ),
                            )
                        }
                        return@safeLaunch
                    }

                    val specific =
                        withContext(Dispatchers.IO) {
                            blockChainSpecificRepository.getSpecific(
                                chain = Chain.Tron,
                                address = trxCoin.address,
                                token = trxCoin,
                                gasFee = gasFee,
                                isSwap = false,
                                isMaxAmountEnabled = false,
                                isDeposit = false,
                                dstAddress = trxCoin.address,
                                memo = TRON_WITHDRAW_EXPIRE_UNFREEZE_MEMO,
                            )
                        }

                    val estimatedFee =
                        gasFeeToEstimatedFee(
                            GasFeeParams(
                                gasLimit = BigInteger.ONE,
                                gasFee = gasFee,
                                selectedToken = trxCoin,
                            )
                        )

                    val currency = appCurrencyRepository.currency.first()
                    val claimableTrx = claimableSun.sunToTrx()

                    val transaction =
                        Transaction(
                            id = UUID.randomUUID().toString(),
                            vaultId = vaultId,
                            chainId = Chain.Tron.raw,
                            token = trxCoin,
                            srcAddress = trxCoin.address,
                            dstAddress = trxCoin.address,
                            tokenValue =
                                TokenValue(
                                    value = BigInteger.valueOf(claimableSun),
                                    unit = trxCoin.ticker,
                                    decimals = trxCoin.decimal,
                                ),
                            fiatValue =
                                FiatValue(
                                    value =
                                        claimableTrx.multiply(trxCachedPrice(trxCoin.id, currency)),
                                    currency = currency.ticker,
                                ),
                            gasFee = gasFee,
                            blockChainSpecific = specific.blockChainSpecific,
                            memo = TRON_WITHDRAW_EXPIRE_UNFREEZE_MEMO,
                            estimatedFee = estimatedFee.formattedFiatValue,
                            totalGas = estimatedFee.formattedTokenValue,
                        )

                    transactionRepository.addTransaction(transaction)
                    // The claim settles the pending rows this snapshot still holds; drop it so the
                    // list is re-read from the chain when the screen resumes after signing.
                    tronDeFiSnapshotCache.clear(trxCoin.address)
                    navigator.route(
                        Route.VerifySend(transactionId = transaction.id, vaultId = vaultId)
                    )
                } finally {
                    updateSuccess { it.copy(isClaimingWithdrawal = false) }
                }
            }
    }

    fun onDismissClaimError() {
        updateSuccess { it.copy(claimError = null) }
    }

    private fun updateSuccess(transform: (TronDeFiUiState.Success) -> TronDeFiUiState.Success) {
        _state.update { current ->
            if (current is TronDeFiUiState.Success) transform(current) else current
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
}
