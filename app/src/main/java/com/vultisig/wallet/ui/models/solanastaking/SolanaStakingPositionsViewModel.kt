package com.vultisig.wallet.ui.models.solanastaking

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakeAccount
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakeState
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingConfig
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingService
import com.vultisig.wallet.data.blockchain.solana.staking.ValidatorMetadata
import com.vultisig.wallet.data.blockchain.solana.staking.ValidatorMetadataProvider
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * @property stakedFiatDisplay pre-formatted fiat value of the delegated stake
 * @property stateLabel localized lifecycle label (Active / Activating / Deactivating / Inactive)
 * @property apyDisplay pre-formatted APY (e.g. `"5.72%"`), or null when unknown
 * @property canDeactivate the account is Active/Activating and can be deactivated (unstaked)
 * @property canWithdraw the account is fully Inactive and its lamports can be withdrawn
 */
@Immutable
internal data class SolanaStakePositionRow(
    val stakePubkey: String,
    val validatorName: String,
    val validatorLogoUrl: String?,
    val votePubkey: String?,
    val stakedDisplay: String,
    val stakedFiatDisplay: String,
    val stateLabel: UiText,
    val apyDisplay: String?,
    val canDeactivate: Boolean,
    val canWithdraw: Boolean,
)

@Immutable
internal sealed interface SolanaStakingPositionsUiState {
    data object Loading : SolanaStakingPositionsUiState

    @Immutable data class Error(val error: UiText) : SolanaStakingPositionsUiState

    @Immutable
    data class Success(
        val totalStakedFiatDisplay: String,
        val positions: List<SolanaStakePositionRow>,
        val isBalanceVisible: Boolean = true,
        val isReloading: Boolean = false,
    ) : SolanaStakingPositionsUiState
}

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
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val _state =
        MutableStateFlow<SolanaStakingPositionsUiState>(SolanaStakingPositionsUiState.Loading)
    val state: StateFlow<SolanaStakingPositionsUiState> = _state.asStateFlow()

    private var vaultId: VaultId = ""
    private var loadJob: Job? = null

    fun setData(vaultId: VaultId) {
        this.vaultId = vaultId
        loadData()
    }

    fun refresh() {
        if (vaultId.isNotEmpty()) loadData()
    }

    fun onStake() {
        if (vaultId.isEmpty()) return
        viewModelScope.safeLaunch(onError = { Timber.w(it, "open Solana delegate failed") }) {
            navigator.route(Route.SolanaDelegate(vaultId = vaultId))
        }
    }

    private fun loadData() {
        loadJob?.cancel()
        _state.update { current ->
            if (current is SolanaStakingPositionsUiState.Success) current.copy(isReloading = true)
            else current
        }
        loadJob =
            viewModelScope.safeLaunch(
                onError = { e ->
                    Timber.e(e, "Failed to load Solana staking positions")
                    if (_state.value !is SolanaStakingPositionsUiState.Success) {
                        _state.value =
                            SolanaStakingPositionsUiState.Error(
                                R.string.error_view_default_description.asUiText()
                            )
                    } else {
                        _state.update { current ->
                            if (current is SolanaStakingPositionsUiState.Success)
                                current.copy(isReloading = false)
                            else current
                        }
                    }
                }
            ) {
                val solCoin = findSolCoin(vaultId)
                if (solCoin == null) {
                    _state.value =
                        SolanaStakingPositionsUiState.Error(
                            R.string.solana_staking_error_sol_not_in_vault.asUiText()
                        )
                    return@safeLaunch
                }

                val isBalanceVisible = balanceVisibilityRepository.getVisibility(vaultId)
                val currency = appCurrencyRepository.currency.first()
                val currencyFormat = appCurrencyRepository.getCurrencyFormat()
                val price = cachedPrice(solCoin.id, currency)

                val accounts = solanaStakingService.fetchStakeAccounts(solCoin.address)
                val votePubkeys = accounts.mapNotNull { it.voter }.distinct()
                val metadata =
                    if (votePubkeys.isEmpty()) emptyMap()
                    else validatorMetadataProvider.metadata(votePubkeys)

                val rows = accounts.map { buildRow(it, metadata[it.voter], price, currencyFormat) }
                val totalStakedSol =
                    accounts
                        .fold(BigDecimal.ZERO) { acc, account ->
                            acc + account.delegatedStake.toBigDecimal()
                        }
                        .movePointLeft(solCoin.decimal)
                val totalFiat = currencyFormat.format(totalStakedSol.multiply(price))

                _state.value =
                    SolanaStakingPositionsUiState.Success(
                        totalStakedFiatDisplay = totalFiat,
                        positions = rows,
                        isBalanceVisible = isBalanceVisible,
                    )
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
        val name =
            metadata?.name?.takeIf { it.isNotBlank() }
                ?: account.voter?.let { shortAddress(it) }
                ?: shortAddress(account.stakePubkey)
        return SolanaStakePositionRow(
            stakePubkey = account.stakePubkey,
            validatorName = name,
            validatorLogoUrl = metadata?.logoUrl,
            votePubkey = account.voter,
            stakedDisplay = "${stakedSol.toPlainString()} ${SOL_TICKER}",
            stakedFiatDisplay = stakedFiat,
            stateLabel = stateLabel(account.state),
            // metadata.apyEstimate is a fraction (0.0572); render as a percentage.
            apyDisplay =
                metadata?.apyEstimate?.let {
                    it.multiply(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP).toPlainString() +
                        "%"
                },
            canDeactivate =
                account.state == SolanaStakeState.Active ||
                    account.state == SolanaStakeState.Activating,
            canWithdraw = account.state == SolanaStakeState.Inactive,
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
