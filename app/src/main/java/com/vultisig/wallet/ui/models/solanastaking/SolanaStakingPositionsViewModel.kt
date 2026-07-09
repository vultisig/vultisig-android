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
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
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
    val validatorAddressDisplay: String,
    val validatorLogoUrl: String?,
    val votePubkey: String?,
    val stakedDisplay: String,
    val stakedFiatDisplay: String,
    val rentReserveDisplay: String,
    val stateLabel: UiText,
    val apyDisplay: String?,
    val canDeactivate: Boolean,
    val canWithdraw: Boolean,
)

/**
 * Single-state model for the Solana staking positions screen so the shared DeFi scaffold renders
 * the balance banner + Total-Staked summary card with skeleton loaders (no bespoke full-screen
 * loading).
 */
@Immutable
internal data class SolanaStakingPositionsUiState(
    val isLoading: Boolean = true,
    val isBalanceVisible: Boolean = true,
    val totalStakedFiatDisplay: String = "",
    val totalStakedSolDisplay: String = "",
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
    private var solCoin: Coin? = null
    private var accountsByPubkey: Map<String, SolanaStakeAccount> = emptyMap()

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

    /** Open the guided move-stake flow for a delegated stake account. */
    fun onMove(stakePubkey: String) {
        if (vaultId.isEmpty()) return
        viewModelScope.safeLaunch(onError = { Timber.w(it, "open Solana move-stake failed") }) {
            navigator.route(Route.SolanaMoveStake(vaultId = vaultId, stakePubkey = stakePubkey))
        }
    }

    /** Deactivate (unstake) a stake account — begins the ~1-epoch cooldown; carries no amount. */
    fun onDeactivate(stakePubkey: String) {
        val account = accountsByPubkey[stakePubkey] ?: return
        buildStakingTxAndRoute(
            payload = SolanaStakingPayload.unstake(stakeAccount = stakePubkey),
            amount = account.delegatedStake,
            dstAddress = stakePubkey,
        )
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
        viewModelScope.safeLaunch(
            onError = { e ->
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
        }
    }

    private fun loadData() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.safeLaunch(
                onError = { e ->
                    Timber.e(e, "Failed to load Solana staking positions")
                    _state.update {
                        it.copy(
                            isLoading = false,
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
                            error = R.string.solana_staking_error_sol_not_in_vault.asUiText(),
                        )
                    }
                    return@safeLaunch
                }

                val isBalanceVisible = balanceVisibilityRepository.getVisibility(vaultId)
                val currency = appCurrencyRepository.currency.first()
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
                val totalFiat = currencyFormat.format(totalStakedSolAmount.multiply(price))

                _state.update {
                    it.copy(
                        isLoading = false,
                        isBalanceVisible = isBalanceVisible,
                        totalStakedFiatDisplay = totalFiat,
                        totalStakedSolDisplay =
                            "${totalStakedSolAmount.stripTrailingZeros().toPlainString()} $SOL_TICKER",
                        positions = rows,
                        error = null,
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
            stakedDisplay = "${stakedSol.toPlainString()} ${SOL_TICKER}",
            stakedFiatDisplay = stakedFiat,
            rentReserveDisplay = "${rentReserveSol.toPlainString()} $SOL_TICKER",
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
