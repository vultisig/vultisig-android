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
import com.vultisig.wallet.data.models.settings.AppCurrency
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
import com.vultisig.wallet.ui.utils.formatPercent
import com.vultisig.wallet.ui.utils.formatTokenAmount
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
import kotlinx.coroutines.flow.combine
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

    /**
     * The enabled set [KaminoEarnUiModel.totalValue] was summed over. A stored total answers one
     * question — this selection, in this currency — so once either changes it stops being an answer
     * and is dropped rather than carried forward.
     */
    private var totalCoverage: Set<String> = emptySet()

    fun setData(vaultId: String) {
        this.vaultId = vaultId
        forgetStoredShareTokens()
        loadBalanceVisibility()
        observeSelectionAndCurrency()
    }

    /**
     * Removes any vault-share token a previous version auto-discovered as a wallet balance.
     *
     * Filtering discovery stops new ones arriving, but a wallet that already held shares when it
     * last scanned has them saved — and left there they count the same deposit twice, once as a
     * position and once as a token. Idempotent, and a no-op for everyone who never had one.
     */
    private fun forgetStoredShareTokens() {
        viewModelScope.safeLaunch(
            onError = { Timber.w(it, "Could not sweep stored Kamino share tokens") }
        ) {
            withContext(ioDispatcher) {
                val vault = vaultRepository.get(vaultId) ?: return@withContext
                vault.coins
                    .filter { coin ->
                        coin.chain == Chain.Solana &&
                            coin.contractAddress in KaminoVaultRegistry.SHARES_MINTS
                    }
                    .forEach { coin -> vaultRepository.deleteTokenFromVault(vaultId, coin) }
            }
        }
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
     * without leaving the screen, and whenever the display currency changes, because every figure
     * here is priced — leaving them be after a switch would relabel them without re-pricing.
     */
    private fun observeSelectionAndCurrency() {
        selectionJob?.cancel()
        selectionJob =
            viewModelScope.safeLaunch {
                combine(
                        selectionRepository.getSelectedVaults(vaultId),
                        appCurrencyRepository.currency,
                        ::Pair,
                    )
                    .distinctUntilChanged()
                    .collect { (selected, currency) ->
                        _state.update { it.copy(hasEnabledVaults = selected.isNotEmpty()) }
                        load(selected, currency)
                    }
            }
    }

    private fun load(selected: Set<String>? = null, selectedCurrency: AppCurrency? = null) {
        loadJob?.cancel()
        loadJob =
            viewModelScope.safeLaunch(
                onError = { throwable ->
                    Timber.e(throwable, "Failed to load Kamino Earn positions")
                    // Said on screen, not only in the log: a silent stop leaves the tab looking as
                    // though the vaults simply hold nothing.
                    _state.update { it.copy(isLoading = false, loadFailed = true) }
                }
            ) {
                val enabled = selected ?: selectionRepository.getSelectedVaults(vaultId).first()
                val vaults = KaminoVaultRegistry.ALLOW_LIST.filter { it.address in enabled }
                val currency = selectedCurrency ?: appCurrencyRepository.currency.first()

                // Drop a stored total that no longer describes this selection or this currency
                // before anything else, so neither this load's fallback nor a failure part-way
                // through can leave it on screen as though it still answered.
                if (totalCoverage != enabled || _state.value.totalValue?.currency != currency) {
                    totalCoverage = emptySet()
                    _state.update { it.copy(totalFiat = null, totalValue = null) }
                }

                if (vaults.isEmpty()) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            hasEnabledVaults = false,
                            rows = emptyList(),
                            totalFiat = null,
                            // Zero, not null: no vault enabled is a read that succeeded and found
                            // nothing, so the chain header can still add it up.
                            totalValue = KaminoEarnTotal(BigDecimal.ZERO, currency),
                        )
                    }
                    return@safeLaunch
                }

                _state.update {
                    it.copy(isLoading = true, hasEnabledVaults = true, loadFailed = false)
                }

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
                                            currency = currency,
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
                // Only a sum covering every enabled vault is this wallet's Kamino total. A vault
                // whose row failed to build, or whose price never landed, would otherwise be folded
                // in as a zero and quietly shrink both this card and the chain header.
                val resolvedTotal =
                    if (resolved.size == vaults.size) {
                        totalFiatValue(resolved)?.let { KaminoEarnTotal(it, currency) }
                    } else {
                        null
                    }
                _state.update { current ->
                    // The previous total is only kept when this load produced none; it has already
                    // been dropped above unless it still covers this selection and currency.
                    val total = resolvedTotal ?: current.totalValue
                    current.copy(
                        isLoading = false,
                        // Keep the previous rows if every vault failed, rather than blanking
                        // positions the user holds because one refresh did not land — but only for
                        // vaults still enabled, or disabling one would leave its card on screen.
                        rows =
                            resolved.ifEmpty {
                                current.rows.filter { row -> row.vaultAddress in enabled }
                            },
                        totalFiat = total?.let { currencyFormat.format(it.value) },
                        totalValue = total,
                    )
                }
                if (resolvedTotal != null) totalCoverage = enabled
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
        currency: AppCurrency,
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
                tokenAmount?.let { it.stripTrailingZeros().formatTokenAmount(coin?.ticker).trim() }
                    ?: FIAT_VALUE_UNAVAILABLE,
            depositedFiat =
                tokenAmount?.let { amount -> coin?.let { fiatOrNull(amount, it, currency) } },
            apyDisplay = apy?.formatPercent(),
            pnlDisplay =
                pnlToken?.let {
                    // Unsigned: the row's label already says whether this was earned or lost, so a
                    // minus sign in front of "Lost" would state it twice.
                    val amount = it.abs().setScale(vault.tokenDecimals, RoundingMode.DOWN)
                    amount.stripTrailingZeros().formatTokenAmount(coin?.ticker).trim()
                },
            pnlDirection =
                when {
                    pnlToken == null -> KaminoEarnRow.PnlDirection.FLAT
                    pnlToken.signum() > 0 -> KaminoEarnRow.PnlDirection.UP
                    pnlToken.signum() < 0 -> KaminoEarnRow.PnlDirection.DOWN
                    else -> KaminoEarnRow.PnlDirection.FLAT
                },
            // Null, not zero, when the position or its price could not be read: the totals that add
            // these up treat an unread vault as unknown rather than as holding nothing.
            fiatValue =
                tokenAmount?.let { amount -> coin?.let { fiatValueOrNull(amount, it, currency) } },
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
    private suspend fun fiatValueOrNull(
        amount: BigDecimal,
        coin: Coin,
        currency: AppCurrency,
    ): BigDecimal? {
        if (amount.signum() == 0) return BigDecimal.ZERO
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

    private suspend fun fiatOrNull(amount: BigDecimal, coin: Coin, currency: AppCurrency): String? {
        val value = fiatValueOrNull(amount, coin, currency) ?: return null
        return runCatching { appCurrencyRepository.getCurrencyFormat().format(value) }.getOrNull()
    }

    /**
     * Summed per row, because the vaults do not share an underlying token — dollars and SOL cannot
     * be added before each has been priced. Null as soon as one row is unpriced: that vault added
     * in as a zero would report a total the user does not hold.
     */
    private fun totalFiatValue(rows: List<KaminoEarnRow>): BigDecimal? {
        var total = BigDecimal.ZERO
        for (row in rows) {
            total = total.add(row.fiatValue ?: return null)
        }
        return total
    }

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
