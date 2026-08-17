package com.vultisig.wallet.ui.models.solanastaking

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.solana.staking.BuildSolanaStakingKeysignPayloadUseCase
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakeAccount
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakeState
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingConfig
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingPayload
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingService
import com.vultisig.wallet.data.blockchain.solana.staking.ValidatorMetadata
import com.vultisig.wallet.data.blockchain.solana.staking.ValidatorMetadataProvider
import com.vultisig.wallet.data.chains.helpers.SolanaHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.DefiFiatTotal
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import com.vultisig.wallet.ui.utils.formatPercent
import com.vultisig.wallet.ui.utils.formatTokenAmount
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import timber.log.Timber

/**
 * One row per Solana stake account. Because a stake account maps to exactly one validator and a
 * wallet may hold N of them, the DeFi tab renders per-stake-account rows (not per-validator).
 *
 * @property stakePubkey base58 stake-account address (row key)
 * @property validatorName display name (metadata) or truncated vote pubkey when unenriched
 * @property validatorLogoUrl absolute logo URL, or null to fall back to a monogram avatar
 * @property stakedDisplay delegated stake formatted as SOL, e.g. `"12.5 SOL"`
 * @property stakedFiatDisplay pre-formatted fiat value of the delegated stake, null while it is not
 *   known in the selected currency — a display-currency change drops it until the reload re-prices
 *   it, rather than leaving a figure that is now a number in one currency wearing another's symbol
 * @property stateLabel localized lifecycle label (Active / Activating / Deactivating / Inactive)
 * @property apyDisplay pre-formatted APY (e.g. `"5.72%"`), or null when unknown
 * @property canManage the account is Active/Activating/Deactivating, so the Move/Stake actions row
 *   is shown. Deactivating is included so those actions stay visible while the account cools down.
 * @property canUnstake the account is Active/Activating (not yet deactivating), so the Unstake
 *   action is offered. Excludes Deactivating — deactivating an already-deactivating stake is a
 *   chain no-op — so Unstake is hidden there while Move/Stake ([canManage]) remain.
 * @property canWithdraw the account is fully Inactive and its lamports can be withdrawn
 */
@Immutable
internal data class SolanaStakePositionRow(
    val stakePubkey: String,
    val validatorName: String,
    val validatorAddressDisplay: String,
    val validatorLogoUrl: String?,
    val votePubkey: String?,
    val stakedDisplay: String,
    val stakedFiatDisplay: String?,
    val rentReserveDisplay: String,
    val state: SolanaStakeState,
    val stateLabel: UiText,
    val apyDisplay: String?,
    val canManage: Boolean,
    val canUnstake: Boolean,
    val canWithdraw: Boolean,
    /** Total account lamports (raw). Re-delegated as-is when the account is moved (Finish Move). */
    val accountLamports: BigInteger,
)

/**
 * Single-state model for the Solana staking positions screen so the shared DeFi scaffold renders
 * the balance banner + Total-Staked summary card with skeleton loaders (no bespoke full-screen
 * loading).
 */
@Immutable
internal data class SolanaStakingPositionsUiState(
    val isLoading: Boolean = true,
    val isReloading: Boolean = false,
    val isBalanceVisible: Boolean = true,
    // Null until priced. The shared DeFi header renders null as the unavailable marker; an empty
    // string would slip past that and draw a blank line where the price belongs.
    val totalStakedFiatDisplay: String? = null,
    val totalStakedSolDisplay: String = "",
    /**
     * The chain's whole DeFi holding — native staking plus Kamino Earn — for the header banner,
     * which is labelled with the chain rather than with the selected tab. Null renders as the
     * unavailable marker, same as [totalStakedFiatDisplay].
     */
    val chainTotalFiatDisplay: String? = null,
    /**
     * The two halves [chainTotalFiatDisplay] is made of, kept raw because they are loaded by
     * separate view-models and either may arrive first. Each keeps the currency it was priced in —
     * the Kamino half exactly as it was handed over, so the screen can also tell "Earn's figure has
     * not reached this view-model yet" from "Earn has no figure".
     */
    val stakedFiat: DefiFiatTotal? = null,
    val kaminoTotal: DefiFiatTotal? = null,
    val positions: List<SolanaStakePositionRow> = emptyList(),
    val error: UiText? = null,
)

/**
 * View-model for the Solana native-staking positions on the DeFi/Earn tab. Reads the wallet's stake
 * accounts fresh (never cached — a stale list would misreport what can be deactivated/withdrawn),
 * joins them against on-chain validator commission and off-chain [ValidatorMetadata], and renders
 * one row per stake account. Degrades gracefully: a metadata outage falls back to a truncated vote
 * pubkey and no logo. Mirrors iOS `SolanaStakeDefiViewModel` (vultisig-ios #4664).
 */
@HiltViewModel
internal class SolanaStakingPositionsViewModel
@Inject
constructor(
    private val vaultRepository: VaultRepository,
    private val solanaStakingService: SolanaStakingService,
    private val validatorMetadataProvider: ValidatorMetadataProvider,
    private val balanceVisibilityRepository: BalanceVisibilityRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val buildKeysignPayload: BuildSolanaStakingKeysignPayloadUseCase,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val _state = MutableStateFlow(SolanaStakingPositionsUiState())
    val state: StateFlow<SolanaStakingPositionsUiState> = _state.asStateFlow()

    private var vaultId: VaultId = ""
    private var loadJob: Job? = null
    private var currencyJob: Job? = null
    private var solCoin: Coin? = null
    private var accountsByPubkey: Map<String, SolanaStakeAccount> = emptyMap()
    private var isBuildingStakingTx: Boolean = false

    /**
     * The currency the figures on screen were priced in, together with its format. Held as one
     * reference so a reader never sees a format from one currency next to the ticker of another,
     * and null until the first load has priced anything.
     */
    @Volatile private var pricing: Pricing? = null

    private data class Pricing(val currency: AppCurrency, val format: NumberFormat)

    fun setData(vaultId: VaultId) {
        this.vaultId = vaultId
        observeCurrency()
    }

    /**
     * Loads on entry and again on every display-currency change: every figure here is priced, so a
     * switch invalidates them rather than merely relabelling them. The collector's first emission
     * performs the initial load.
     */
    private fun observeCurrency() {
        currencyJob?.cancel()
        currencyJob =
            viewModelScope.safeLaunch(
                onError = { Timber.e(it, "Failed to watch the app currency") }
            ) {
                appCurrencyRepository.currency.distinctUntilChanged().collect { currency ->
                    if (pricing?.currency?.equals(currency) == false) {
                        // Drop the old-currency figures instead of leaving them under a new
                        // symbol while the reload runs. The per-account fiat goes with them: those
                        // strings are priced too, and a reload that then fails never rebuilds
                        // them, so leaving them would strand each card on the old currency.
                        pricing = null
                        _state.update {
                            it.copy(
                                isLoading = true,
                                totalStakedFiatDisplay = null,
                                chainTotalFiatDisplay = null,
                                positions =
                                    it.positions.map { row -> row.copy(stakedFiatDisplay = null) },
                            )
                        }
                    }
                    loadData(currency)
                }
            }
    }

    fun refresh() {
        if (vaultId.isEmpty()) return
        _state.update { it.copy(isReloading = true) }
        loadData()
    }

    /**
     * The Kamino Earn total for this vault, handed over by the screen. Earn is loaded by its own
     * view-model, so the header banner — which is labelled with the chain, not with the selected
     * tab — can only be their sum once both sides have reported.
     *
     * Null is "not resolved", which is not zero: reporting the staking half alone would present a
     * number that is short by a real position as the chain's whole DeFi balance.
     *
     * Applied synchronously on purpose. Formatting asynchronously here let two handovers finish out
     * of order, so an earlier total could land last and sit on the banner as the current one.
     */
    fun onKaminoTotalChanged(total: DefiFiatTotal?) {
        _state.update { it.withChainTotal(staked = it.stakedFiat, kamino = total) }
    }

    fun onStake() {
        if (vaultId.isEmpty()) return
        viewModelScope.safeLaunch(onError = { Timber.w(it, "open Solana delegate failed") }) {
            navigator.route(Route.SolanaDelegate(vaultId = vaultId))
        }
    }

    /**
     * Open the move-stake step 1 ("Move SOL") screen for a stake account. Solana has no native
     * redelegate, so moving A → B is a guided cross-epoch flow that starts by deactivating the
     * source account; the screen carries the account's delegated stake for the verify summary.
     */
    fun onMove(stakePubkey: String) {
        if (vaultId.isEmpty()) return
        val account = accountsByPubkey[stakePubkey] ?: return
        viewModelScope.safeLaunch(onError = { Timber.w(it, "open Solana move-stake failed") }) {
            navigator.route(
                Route.SolanaMoveStake(
                    vaultId = vaultId,
                    stakePubkey = stakePubkey,
                    delegatedStake = account.delegatedStake.toString(),
                )
            )
        }
    }

    /**
     * Open move-stake step 2 ("Finish Move") for a cooled-down (Inactive) account: re-delegate it
     * to a new validator. Gated on the row being Inactive (same as Withdraw), so no state re-check
     * here.
     */
    fun onFinishMove(stakePubkey: String) {
        if (vaultId.isEmpty()) return
        val account = accountsByPubkey[stakePubkey] ?: return
        viewModelScope.safeLaunch(onError = { Timber.w(it, "open Solana finish-move failed") }) {
            navigator.route(
                Route.SolanaFinishMove(
                    vaultId = vaultId,
                    stakePubkey = stakePubkey,
                    lamports = account.lamports.toString(),
                )
            )
        }
    }

    /**
     * Open the "Unstake SOL" confirmation screen for a stake account. Deactivating begins the
     * ~1-epoch cooldown, so the user confirms on a dedicated screen before the tx is built.
     */
    fun onDeactivate(stakePubkey: String) {
        if (vaultId.isEmpty()) return
        val account = accountsByPubkey[stakePubkey] ?: return
        viewModelScope.safeLaunch(onError = { Timber.w(it, "open Solana unstake failed") }) {
            navigator.route(
                Route.SolanaUnstake(
                    vaultId = vaultId,
                    stakePubkey = stakePubkey,
                    delegatedStake = account.delegatedStake.toString(),
                )
            )
        }
    }

    /**
     * Withdraw a fully-inactive stake account's lamports back to the wallet. Gated on the account
     * being [SolanaStakeState.Inactive] (the row only surfaces Withdraw once cooled down), so no
     * cooldown re-check is needed here.
     */
    fun onWithdraw(stakePubkey: String) {
        val account =
            accountsByPubkey[stakePubkey]?.takeIf { it.state == SolanaStakeState.Inactive }
        if (account == null) return
        buildStakingTxAndRoute(
            payload =
                SolanaStakingPayload.withdraw(
                    stakeAccount = stakePubkey,
                    lamports = account.lamports,
                ),
            amount = account.lamports,
            dstAddress = stakePubkey,
        )
    }

    private fun buildStakingTxAndRoute(
        payload: SolanaStakingPayload,
        amount: BigInteger,
        dstAddress: String,
    ) {
        val coin = solCoin ?: return
        if (isBuildingStakingTx) return
        isBuildingStakingTx = true
        viewModelScope.safeLaunch(
            onError = { e ->
                isBuildingStakingTx = false
                Timber.e(e, "Failed to build Solana staking tx")
                _state.update { it.copy(error = (e.message ?: "").asUiText()) }
            }
        ) {
            val vault = vaultRepository.get(vaultId) ?: error("Vault not found")
            val gasFee = TokenValue(value = SolanaHelper.DefaultFeeInLamports, token = coin)
            val specific =
                blockChainSpecificRepository.getSpecific(
                    chain = Chain.Solana,
                    address = coin.address,
                    token = coin,
                    gasFee = gasFee,
                    isSwap = false,
                    isMaxAmountEnabled = false,
                    isDeposit = true,
                )
            val keysignPayload =
                buildKeysignPayload(
                    coin = coin,
                    payload = payload,
                    blockChainSpecific = specific.blockChainSpecific,
                    balanceLamports = BigInteger.ZERO,
                    vaultPublicKeyECDSA = vault.pubKeyECDSA,
                    vaultLocalPartyID = vault.localPartyID,
                    libType = vault.libType,
                )
            val depositTx =
                DepositTransaction(
                    id = UUID.randomUUID().toString(),
                    vaultId = vaultId,
                    srcToken = coin,
                    srcAddress = coin.address,
                    srcTokenValue = TokenValue(value = amount, token = coin),
                    memo = "",
                    dstAddress = dstAddress,
                    estimatedFees = gasFee,
                    estimateFeesFiat = "",
                    blockChainSpecific = specific.blockChainSpecific,
                    solanaStakingPayload = payload,
                    signSolana = keysignPayload.signSolana,
                )
            depositTransactionRepository.addTransaction(depositTx)
            navigator.route(Route.VerifyDeposit(vaultId = vaultId, transactionId = depositTx.id))
            isBuildingStakingTx = false
        }
    }

    private fun loadData(selectedCurrency: AppCurrency? = null) {
        loadJob?.cancel()
        loadJob =
            viewModelScope.safeLaunch(
                onError = { e ->
                    Timber.e(e, "Failed to load Solana staking positions")
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isReloading = false,
                            error = R.string.error_view_default_description.asUiText(),
                        )
                    }
                }
            ) {
                val solCoin = findSolCoin(vaultId)
                if (solCoin == null) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isReloading = false,
                            error = R.string.solana_staking_error_sol_not_in_vault.asUiText(),
                        )
                    }
                    return@safeLaunch
                }

                val isBalanceVisible = balanceVisibilityRepository.getVisibility(vaultId)
                val currency = selectedCurrency ?: appCurrencyRepository.currency.first()
                val currencyFormat = appCurrencyRepository.getCurrencyFormat()
                val price = cachedPrice(solCoin.id, currency)

                this@SolanaStakingPositionsViewModel.solCoin = solCoin
                val accounts = solanaStakingService.fetchStakeAccounts(solCoin.address)
                accountsByPubkey = accounts.associateBy { it.stakePubkey }
                val votePubkeys = accounts.mapNotNull { it.voter }.distinct()
                val metadata =
                    if (votePubkeys.isEmpty()) emptyMap()
                    else validatorMetadataProvider.metadata(votePubkeys)

                val rows = accounts.map { buildRow(it, metadata[it.voter], price, currencyFormat) }
                val totalStakedSolAmount =
                    accounts
                        .fold(BigDecimal.ZERO) { acc, account ->
                            acc + account.delegatedStake.toBigDecimal()
                        }
                        .movePointLeft(solCoin.decimal)
                val stakedFiatValue = totalStakedSolAmount.multiply(price)
                val totalFiat = currencyFormat.format(stakedFiatValue)

                // Published with the figures it priced, not ahead of them: everything the banner
                // adds up is checked against this currency, so a load still in flight must not
                // already claim its own.
                pricing = Pricing(currency = currency, format = currencyFormat)
                _state.update {
                    it.copy(
                            isLoading = false,
                            isReloading = false,
                            isBalanceVisible = isBalanceVisible,
                            totalStakedFiatDisplay = totalFiat,
                            totalStakedSolDisplay =
                                totalStakedSolAmount
                                    .stripTrailingZeros()
                                    .formatTokenAmount(SOL_TICKER),
                            positions = rows,
                            error = null,
                        )
                        .withChainTotal(
                            staked = DefiFiatTotal(stakedFiatValue, currency),
                            kamino = it.kaminoTotal,
                        )
                }
            }
    }

    private fun buildRow(
        account: SolanaStakeAccount,
        metadata: ValidatorMetadata?,
        price: BigDecimal,
        currencyFormat: NumberFormat,
    ): SolanaStakePositionRow {
        val stakedSol =
            account.delegatedStake.toBigDecimal().movePointLeft(SOL_DECIMALS).stripTrailingZeros()
        val stakedFiat = currencyFormat.format(stakedSol.multiply(price))
        val rentReserveSol =
            account.rentExemptReserve
                .toBigDecimal()
                .movePointLeft(SOL_DECIMALS)
                .stripTrailingZeros()
        val name =
            metadata?.name?.takeIf { it.isNotBlank() }
                ?: account.voter?.let { shortAddress(it) }
                ?: shortAddress(account.stakePubkey)
        return SolanaStakePositionRow(
            stakePubkey = account.stakePubkey,
            validatorName = name,
            // iOS shows the truncated stake-account pubkey under the validator name (a wallet can
            // hold multiple accounts on the same validator, so the account address disambiguates).
            validatorAddressDisplay = shortAddress(account.stakePubkey),
            validatorLogoUrl = metadata?.logoUrl,
            votePubkey = account.voter,
            stakedDisplay = stakedSol.formatTokenAmount(SOL_TICKER),
            stakedFiatDisplay = stakedFiat,
            rentReserveDisplay = rentReserveSol.formatTokenAmount(SOL_TICKER),
            state = account.state,
            stateLabel = stateLabel(account.state),
            // metadata.apyEstimate is a fraction (0.0572); render as a percentage.
            apyDisplay =
                metadata?.apyEstimate?.let {
                    it.multiply(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP).formatPercent()
                },
            canManage =
                account.state == SolanaStakeState.Active ||
                    account.state == SolanaStakeState.Activating ||
                    account.state == SolanaStakeState.Deactivating,
            canUnstake =
                account.state == SolanaStakeState.Active ||
                    account.state == SolanaStakeState.Activating,
            canWithdraw = account.state == SolanaStakeState.Inactive,
            accountLamports = account.lamports,
        )
    }

    private fun stateLabel(state: SolanaStakeState): UiText =
        when (state) {
            SolanaStakeState.Activating -> R.string.solana_staking_state_activating.asUiText()
            SolanaStakeState.Active -> R.string.solana_staking_state_active.asUiText()
            SolanaStakeState.Deactivating -> R.string.solana_staking_state_deactivating.asUiText()
            SolanaStakeState.Inactive -> R.string.solana_staking_state_inactive.asUiText()
            SolanaStakeState.NotDelegated -> R.string.solana_staking_state_not_delegated.asUiText()
        }

    private fun shortAddress(address: String): String =
        if (address.length > 12) "${address.take(6)}…${address.takeLast(4)}" else address

    /**
     * Folds the two halves of the chain's DeFi holding into the banner value.
     *
     * Unknown wins over known: while either half is unresolved the banner says so rather than
     * printing the other half, which would read as the chain total while being short by whatever
     * the unread side holds. A user with no Kamino vault enabled has a resolved zero there, so the
     * common staking-only case still shows a figure.
     *
     * A half priced in some other currency counts as unresolved too, and both are held to that —
     * the staking one is no safer than Kamino's. The two view-models read the selected currency
     * independently, so a mid-session switch reaches one before the other, and Kamino's zero-vault
     * answer needs no network call at all: it can arrive re-priced while this side is still holding
     * the figure it read before the switch. The banner waits that window out rather than summing
     * across currencies or restamping an old total with a new symbol.
     */
    private fun SolanaStakingPositionsUiState.withChainTotal(
        staked: DefiFiatTotal?,
        kamino: DefiFiatTotal?,
    ): SolanaStakingPositionsUiState {
        val pricing = pricing
        val stakedValue = staked?.takeIf { it.currency == pricing?.currency }?.value
        val kaminoValue = kamino?.takeIf { it.currency == pricing?.currency }?.value
        return copy(
            stakedFiat = staked,
            kaminoTotal = kamino,
            chainTotalFiatDisplay =
                if (stakedValue == null || kaminoValue == null || pricing == null) null
                else pricing.format.format(stakedValue.add(kaminoValue)),
        )
    }

    private suspend fun cachedPrice(tokenId: String, currency: AppCurrency): BigDecimal =
        tokenPriceRepository.getCachedPrice(tokenId = tokenId, appCurrency = currency)
            ?: BigDecimal.ZERO

    private suspend fun findSolCoin(vaultId: VaultId): Coin? =
        vaultRepository.get(vaultId)?.coins?.find { it.chain == Chain.Solana && it.isNativeToken }

    private companion object {
        const val SOL_TICKER = "SOL"
        val SOL_DECIMALS = SolanaStakingConfig.LAMPORTS_PER_SOL.toString().length - 1
    }
}
