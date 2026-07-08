package com.vultisig.wallet.ui.models.solanastaking

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.blockchain.solana.staking.BuildSolanaStakingKeysignPayloadUseCase
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakeAccount
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakeState
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingPayload
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingService
import com.vultisig.wallet.data.chains.helpers.SolanaHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

/**
 * The current step of the guided move-stake sequence, derived from the source account's on-chain
 * state so the flow is resumable across epochs.
 */
internal enum class SolanaMoveStakeStep {
    /** Source is still active/activating — deactivate it to begin the move. */
    Deactivate,
    /** Deactivation requested — waiting out the ~1-epoch cooldown. */
    Waiting,
    /** Fully cooled down — withdraw to the wallet, then stake to the new validator. */
    Withdraw,
    /** No live delegation — nothing to move. */
    Done,
}

@Immutable
internal data class SolanaMoveStakeUiState(
    val step: SolanaMoveStakeStep = SolanaMoveStakeStep.Deactivate,
    val validatorName: String = "",
    val unlocksAtEpoch: Long? = null,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: UiText? = null,
)

/**
 * Guided move-stake flow. Solana has no native mainnet redelegate, so moving a stake account to a
 * new validator is a cross-epoch sequence — Deactivate → wait for cooldown → Withdraw → stake to
 * the new validator. The step is resolved from the source account's live on-chain state each time
 * the screen opens, so a user who left mid-sequence resumes at the right step. Mirrors the iOS
 * guided move flow (vultisig-ios #4663). Partial moves (Split) are not offered — wallet-core
 * exposes no split primitive, so the whole account is moved.
 */
@HiltViewModel
internal class SolanaMoveStakeViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val solanaStakingService: SolanaStakingService,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val buildKeysignPayload: BuildSolanaStakingKeysignPayloadUseCase,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Route.SolanaMoveStake>()

    private val _state = MutableStateFlow(SolanaMoveStakeUiState())
    val state: StateFlow<SolanaMoveStakeUiState> = _state.asStateFlow()

    private var coin: Coin? = null
    private var account: SolanaStakeAccount? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to load Solana move-stake state")
                _state.update {
                    it.copy(
                        isLoading = false,
                        error =
                            com.vultisig.wallet.R.string.error_view_default_description.asUiText(),
                    )
                }
            }
        ) {
            val vault = vaultRepository.get(route.vaultId) ?: error("Vault not found")
            val solCoin =
                vault.coins.firstOrNull { it.chain == Chain.Solana && it.isNativeToken }
                    ?: error("SOL not in this vault")
            coin = solCoin

            val currentEpoch = solanaStakingService.fetchEpochInfo()?.epoch
            val acct =
                solanaStakingService.fetchStakeAccounts(solCoin.address).firstOrNull {
                    it.stakePubkey == route.stakePubkey
                }
            account = acct

            val step =
                when (acct?.state) {
                    SolanaStakeState.Active,
                    SolanaStakeState.Activating -> SolanaMoveStakeStep.Deactivate
                    SolanaStakeState.Deactivating -> SolanaMoveStakeStep.Waiting
                    SolanaStakeState.Inactive -> SolanaMoveStakeStep.Withdraw
                    else -> SolanaMoveStakeStep.Done
                }
            val unlocksAtEpoch =
                acct
                    ?.deactivationEpoch
                    ?.let { it + 1 }
                    .takeIf { step == SolanaMoveStakeStep.Waiting }
            _state.update {
                it.copy(
                    step = step,
                    validatorName = acct?.voter?.let(::shortAddress).orEmpty(),
                    unlocksAtEpoch = unlocksAtEpoch,
                    isLoading = false,
                )
            }
        }
    }

    /** Step 1: deactivate the source account, beginning the cooldown. */
    fun onDeactivate() {
        val stakePubkey = route.stakePubkey
        val amount = account?.delegatedStake ?: BigInteger.ZERO
        buildAndRoute(SolanaStakingPayload.unstake(stakePubkey), amount)
    }

    /** Step 3: withdraw the cooled-down lamports to the wallet (then the user stakes to B). */
    fun onWithdraw() {
        val acct = account?.takeIf { it.state == SolanaStakeState.Inactive } ?: return
        buildAndRoute(
            SolanaStakingPayload.withdraw(route.stakePubkey, acct.lamports),
            acct.lamports,
        )
    }

    /** After withdraw, open the delegate flow to stake to the new validator (validator B). */
    fun onStakeToNewValidator() {
        viewModelScope.safeLaunch(onError = { Timber.w(it, "open delegate failed") }) {
            navigator.route(Route.SolanaDelegate(vaultId = route.vaultId))
        }
    }

    fun back() {
        viewModelScope.safeLaunch(onError = { Timber.w(it, "back failed") }) { navigator.back() }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun buildAndRoute(payload: SolanaStakingPayload, amount: BigInteger) {
        val srcCoin = coin ?: return
        if (_state.value.isSubmitting) return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Solana move-stake tx build failed")
                _state.update {
                    it.copy(isSubmitting = false, error = (e.message ?: "").asUiText())
                }
            }
        ) {
            val vault = vaultRepository.get(route.vaultId) ?: error("Vault not found")
            val gasFee = TokenValue(value = SolanaHelper.DefaultFeeInLamports, token = srcCoin)
            val specific =
                blockChainSpecificRepository.getSpecific(
                    chain = Chain.Solana,
                    address = srcCoin.address,
                    token = srcCoin,
                    gasFee = gasFee,
                    isSwap = false,
                    isMaxAmountEnabled = false,
                    isDeposit = true,
                )
            val keysignPayload =
                buildKeysignPayload(
                    coin = srcCoin,
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
                    vaultId = route.vaultId,
                    srcToken = srcCoin,
                    srcAddress = srcCoin.address,
                    srcTokenValue = TokenValue(value = amount, token = srcCoin),
                    memo = "",
                    dstAddress = route.stakePubkey,
                    estimatedFees = gasFee,
                    estimateFeesFiat = "",
                    blockChainSpecific = specific.blockChainSpecific,
                    solanaStakingPayload = payload,
                    signSolana = keysignPayload.signSolana,
                )
            depositTransactionRepository.addTransaction(depositTx)
            _state.update { it.copy(isSubmitting = false) }
            navigator.route(
                Route.VerifyDeposit(vaultId = route.vaultId, transactionId = depositTx.id)
            )
        }
    }

    private fun shortAddress(address: String): String =
        if (address.length > 12) "${address.take(6)}…${address.takeLast(4)}" else address
}
