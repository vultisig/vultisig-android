package com.vultisig.wallet.ui.screens.v2.defi.solana

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.api.KaminoApi
import com.vultisig.wallet.data.api.KaminoUserPositionJson
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoPositionMath
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoVault
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoVaultRegistry
import com.vultisig.wallet.data.blockchain.solana.kamino.coin
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.KaminoVaultSelectionRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.FIAT_VALUE_UNAVAILABLE
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Drives the Kamino Earn segment of the Solana DeFi tab.
 *
 * Cards are gated on the per-vault opt-in and, once enabled, always render: a vault with no deposit
 * still gets a card at zero. A refresh replaces values in place rather than clearing them first, so
 * a failed poll leaves the last known figures on screen instead of blanking a position the user
 * holds.
 */
@HiltViewModel
internal class KaminoEarnViewModel
@Inject
constructor(
    private val navigator: Navigator<Destination>,
    private val kaminoApi: KaminoApi,
    private val selectionRepository: KaminoVaultSelectionRepository,
    private val vaultRepository: VaultRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val balanceVisibilityRepository: BalanceVisibilityRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(KaminoEarnUiModel())
    val state: StateFlow<KaminoEarnUiModel> = _state.asStateFlow()

    private lateinit var vaultId: String
    private var loadJob: Job? = null
    private var selectionJob: Job? = null

    fun setData(vaultId: String) {
        this.vaultId = vaultId
        loadBalanceVisibility()
        observeSelection()
    }

    fun refresh() {
        if (::vaultId.isInitialized) load()
    }

    /** Opens the vault picker, seeded with whatever is currently enabled. */
    fun openPicker() {
        viewModelScope.safeLaunch {
            val current = selectionRepository.getSelectedVaults(vaultId).first()
            _state.update { it.copy(isShowingPicker = true, pendingSelection = current) }
        }
    }

    /** Dismisses the picker without saving. */
    fun closePicker() {
        _state.update { it.copy(isShowingPicker = false) }
    }

    fun onVaultToggled(vaultAddress: String, isSelected: Boolean) {
        if (!KaminoVaultRegistry.isAllowed(vaultAddress)) return
        _state.update { current ->
            current.copy(
                pendingSelection =
                    if (isSelected) current.pendingSelection + vaultAddress
                    else current.pendingSelection - vaultAddress
            )
        }
    }

    /**
     * Persists the edited selection. Saving an empty set is how the user turns Kamino Earn off, and
     * the repository records that as a real choice rather than falling back to the default.
     */
    fun savePicker() {
        val pending = _state.value.pendingSelection
        _state.update { it.copy(isShowingPicker = false) }
        viewModelScope.safeLaunch {
            withContext(ioDispatcher) { selectionRepository.saveSelectedVaults(vaultId, pending) }
            // The selection flow re-emits and reloads, so nothing else has to be refreshed here.
        }
    }

    /** Opens the deposit form for [vaultAddress]. */
    fun onDeposit(vaultId: String, vaultAddress: String) =
        openAmountForm(vaultId, vaultAddress, isWithdraw = false)

    /** Opens the withdraw form for [vaultAddress]. */
    fun onWithdraw(vaultId: String, vaultAddress: String) =
        openAmountForm(vaultId, vaultAddress, isWithdraw = true)

    private fun openAmountForm(vaultId: String, vaultAddress: String, isWithdraw: Boolean) {
        // Refuse to route at all for a vault the app does not curate, rather than letting the form
        // open and fail there.
        if (!KaminoVaultRegistry.isAllowed(vaultAddress)) {
            Timber.e("Refusing to open a Kamino form for uncurated vault %s", vaultAddress)
            return
        }
        viewModelScope.safeLaunch {
            navigator.route(
                Route.KaminoAmount(
                    vaultId = vaultId,
                    vaultAddress = vaultAddress,
                    isWithdraw = isWithdraw,
                )
            )
        }
    }

    private fun loadBalanceVisibility() {
        viewModelScope.safeLaunch {
            val isVisible =
                withContext(ioDispatcher) { balanceVisibilityRepository.getVisibility(vaultId) }
            _state.update { it.copy(isBalanceVisible = isVisible) }
        }
    }

    /**
     * Reloads whenever the opt-in changes, so toggling a vault under Manage positions takes effect
     * without leaving the screen.
     */
    private fun observeSelection() {
        selectionJob?.cancel()
        selectionJob =
            viewModelScope.safeLaunch {
                selectionRepository.getSelectedVaults(vaultId).distinctUntilChanged().collect {
                    selected ->
                    _state.update { it.copy(hasEnabledVaults = selected.isNotEmpty()) }
                    load(selected)
                }
            }
    }

    private fun load(selected: Set<String>? = null) {
        loadJob?.cancel()
        loadJob =
            viewModelScope.safeLaunch(
                onError = { throwable ->
                    Timber.e(throwable, "Failed to load Kamino Earn positions")
                    _state.update { it.copy(isLoading = false) }
                }
            ) {
                val enabled = selected ?: selectionRepository.getSelectedVaults(vaultId).first()
                val vaults = KaminoVaultRegistry.ALLOW_LIST.filter { it.address in enabled }

                if (vaults.isEmpty()) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            hasEnabledVaults = false,
                            rows = emptyList(),
                            totalFiat = null,
                        )
                    }
                    return@safeLaunch
                }

                _state.update { it.copy(isLoading = true, hasEnabledVaults = true) }

                val walletAddress = resolveSolanaAddress()
                val positions = fetchPositions(walletAddress)
                val currencyFormat =
                    withContext(ioDispatcher) { appCurrencyRepository.getCurrencyFormat() }

                // Each vault is independent: one failing call must not empty the other cards.
                val rows = supervisorScope {
                    vaults
                        .map { vault ->
                            async(ioDispatcher) {
                                runCatching {
                                        buildRow(
                                            vault = vault,
                                            walletAddress = walletAddress,
                                            position = positions?.get(vault.address),
                                            positionsAreKnown = positions != null,
                                        )
                                    }
                                    .getOrElse { throwable ->
                                        Timber.e(
                                            throwable,
                                            "Failed to build Kamino row for %s",
                                            vault.address,
                                        )
                                        null
                                    }
                            }
                        }
                        .awaitAll()
                }

                val resolved = rows.filterNotNull()
                _state.update { current ->
                    current.copy(
                        isLoading = false,
                        // Keep the previous rows if every vault failed, rather than blanking
                        // positions the user holds because one refresh did not land — but only for
                        // vaults still enabled, or disabling one would leave its card on screen.
                        rows =
                            resolved.ifEmpty {
                                current.rows.filter { row -> row.vaultAddress in enabled }
                            },
                        totalFiat =
                            resolved
                                .takeIf { it.isNotEmpty() }
                                ?.let { currencyFormat.format(totalFiatValue(it)) }
                                ?: current.totalFiat,
                    )
                }
            }
    }

    /**
     * Positions for the whole wallet in one call, keyed by vault. An address holding nothing
     * returns an empty list, which is a real answer rather than a failure.
     */
    private suspend fun fetchPositions(
        walletAddress: String
    ): Map<String, KaminoUserPositionJson>? =
        withContext(ioDispatcher) {
            // Null, not empty: an empty list is the wallet genuinely holding nothing, while a
            // failed
            // call is not knowing. Collapsing the two would render a real position as "0 USDC",
            // which reads as a lost deposit.
            runCatching { kaminoApi.getUserPositions(walletAddress) }
                .getOrElse { throwable ->
                    Timber.e(throwable, "Failed to load Kamino positions")
                    null
                }
                ?.associateBy { it.vaultAddress }
        }

    private suspend fun buildRow(
        vault: KaminoVault,
        walletAddress: String,
        position: KaminoUserPositionJson?,
        positionsAreKnown: Boolean,
    ): KaminoEarnRow = supervisorScope {
        val stateDeferred = async {
            runCatching { kaminoApi.getVaultState(vault.address) }.getOrNull()
        }
        val metricsDeferred = async {
            runCatching { kaminoApi.getVaultMetrics(vault.address) }.getOrNull()
        }
        val pnlDeferred = async {
            runCatching { kaminoApi.getPositionPnl(walletAddress, vault.address) }.getOrNull()
        }

        val vaultState = stateDeferred.await()
        val metrics = metricsDeferred.await()
        val pnl = pnlDeferred.await()

        // A position that could not be read is not a zero one. Null keeps the balance blank rather
        // than asserting "0", which on a real deposit reads as though it had gone.
        val shares =
            if (positionsAreKnown) {
                KaminoPositionMath.decimalOrNull(position?.totalShares) ?: BigDecimal.ZERO
            } else {
                null
            }
        val tokensPerShare = KaminoPositionMath.decimalOrNull(metrics?.tokensPerShare)
        val tokenAmount =
            if (tokensPerShare == null || shares == null) {
                null
            } else {
                KaminoPositionMath.tokenAmount(shares, tokensPerShare, vault.tokenDecimals)
            }

        val coin = vault.coin
        val apy =
            KaminoPositionMath.decimalOrNull(metrics?.apy30d)?.let(KaminoPositionMath::apyPercent)
        val pnlToken = KaminoPositionMath.decimalOrNull(pnl?.totalPnl?.token)

        KaminoEarnRow(
            vaultAddress = vault.address,
            name = vaultState?.state?.name?.takeIf { it.isNotBlank() } ?: vault.fallbackName,
            curator = vault.curator,
            riskTier = vault.riskTier,
            tokenLogo = coin?.logo.orEmpty(),
            tokenTicker = coin?.ticker.orEmpty(),
            depositedDisplay =
                tokenAmount?.let {
                    "${it.stripTrailingZeros().toPlainString()} ${coin?.ticker.orEmpty()}".trim()
                } ?: FIAT_VALUE_UNAVAILABLE,
            depositedFiat = tokenAmount?.let { amount -> coin?.let { fiatOrNull(amount, it) } },
            apyDisplay = apy?.let { "${it.toPlainString()}%" },
            pnlDisplay =
                pnlToken?.let {
                    // Unsigned: the row's label already says whether this was earned or lost, so a
                    // minus sign in front of "Lost" would state it twice.
                    val amount = it.abs().setScale(vault.tokenDecimals, RoundingMode.DOWN)
                    "${amount.stripTrailingZeros().toPlainString()} ${coin?.ticker.orEmpty()}"
                        .trim()
                },
            pnlDirection =
                when {
                    pnlToken == null -> KaminoEarnRow.PnlDirection.FLAT
                    pnlToken.signum() > 0 -> KaminoEarnRow.PnlDirection.UP
                    pnlToken.signum() < 0 -> KaminoEarnRow.PnlDirection.DOWN
                    else -> KaminoEarnRow.PnlDirection.FLAT
                },
            fiatValue =
                tokenAmount?.let { amount -> coin?.let { fiatValueOrNull(amount, it) } }
                    ?: BigDecimal.ZERO,
            // Two different claims live in a zero here: "read, and holds nothing" and "not read
            // yet". Only the first may remove Withdraw. Hiding it on an unread position strands a
            // user who deposited on another device, or whose refresh failed right after depositing
            // —
            // the way out of a position is never removed on the strength of a read that has not
            // happened. The withdraw form reads the position itself and can say "nothing to
            // withdraw" truthfully.
            hasPosition = shares == null || shares.signum() > 0,
        )
    }

    /**
     * Fiat goes through the app's own price pipeline rather than Kamino's USD figures, because the
     * user may not be reading in dollars.
     */
    private suspend fun fiatValueOrNull(amount: BigDecimal, coin: Coin): BigDecimal? {
        if (amount.signum() == 0) return BigDecimal.ZERO
        val currency = appCurrencyRepository.currency.first()
        val price =
            runCatching {
                    tokenPriceRepository.getCachedPrice(tokenId = coin.id, appCurrency = currency)
                        ?: tokenPriceRepository.getPriceByContactAddress(
                            coin.chain.id,
                            coin.contractAddress,
                        )
                }
                .getOrNull() ?: return null
        return amount.multiply(price).setScale(2, RoundingMode.DOWN)
    }

    private suspend fun fiatOrNull(amount: BigDecimal, coin: Coin): String? {
        val value = fiatValueOrNull(amount, coin) ?: return null
        return runCatching { appCurrencyRepository.getCurrencyFormat().format(value) }.getOrNull()
    }

    /**
     * Summed per row, because the vaults do not share an underlying token — dollars and SOL cannot
     * be added before each has been priced.
     */
    private fun totalFiatValue(rows: List<KaminoEarnRow>): BigDecimal =
        rows.fold(BigDecimal.ZERO) { running, row -> running.add(row.fiatValue) }

    private suspend fun resolveSolanaAddress(): String {
        val vault =
            withContext(ioDispatcher) { vaultRepository.get(vaultId) }
                ?: error("Vault $vaultId not found")
        val (address, _) =
            withContext(ioDispatcher) {
                chainAccountAddressRepository.getAddress(Chain.Solana, vault)
            }
        return address
    }
}
