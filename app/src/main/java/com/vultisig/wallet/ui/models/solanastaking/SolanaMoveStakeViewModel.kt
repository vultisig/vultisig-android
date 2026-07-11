package com.vultisig.wallet.ui.models.solanastaking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.blockchain.solana.staking.BuildSolanaStakingKeysignPayloadUseCase
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingPayload
import com.vultisig.wallet.data.chains.helpers.SolanaHelper
import com.vultisig.wallet.data.models.Chain
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
 * View-model for the Solana move-stake step 1 ("Move SOL") screen. Solana has no native redelegate,
 * so moving a stake account to another validator is a guided cross-epoch flow: this step
 * deactivates the source account (starting the ~1-epoch cooldown), after which the DeFi tab
 * surfaces the finish-move re-delegation. The whole account moves (wallet-core has no split
 * instruction), so the screen is read-only and Continue simply builds the deactivate transaction.
 * Mirrors Windows `SolanaMoveStakeSpecific`.
 */
@HiltViewModel
internal class SolanaMoveStakeViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val buildKeysignPayload: BuildSolanaStakingKeysignPayloadUseCase,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Route.SolanaMoveStake>()

    val stakePubkey: String = route.stakePubkey

    private val _state = MutableStateFlow(SolanaMoveStakeUiState(stakePubkey = route.stakePubkey))
    val state: StateFlow<SolanaMoveStakeUiState> = _state.asStateFlow()

    private var isSubmitting = false

    fun onContinue() {
        if (isSubmitting) return
        isSubmitting = true
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.safeLaunch(
            onError = { e ->
                isSubmitting = false
                Timber.e(e, "Failed to build Solana move-stake tx")
                _state.update { it.copy(isSubmitting = false, error = e.message) }
            }
        ) {
            val vault = vaultRepository.get(route.vaultId) ?: error("Vault not found")
            val coin =
                vault.coins.firstOrNull { it.chain == Chain.Solana && it.isNativeToken }
                    ?: error("SOL not in this vault")

            val payload = SolanaStakingPayload.unstake(stakeAccount = route.stakePubkey)
            val delegatedStake = route.delegatedStake.toBigIntegerOrNull() ?: BigInteger.ZERO
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
                    vaultId = route.vaultId,
                    srcToken = coin,
                    srcAddress = coin.address,
                    srcTokenValue = TokenValue(value = delegatedStake, token = coin),
                    memo = "",
                    dstAddress = route.stakePubkey,
                    estimatedFees = gasFee,
                    estimateFeesFiat = "",
                    blockChainSpecific = specific.blockChainSpecific,
                    solanaStakingPayload = payload,
                    signSolana = keysignPayload.signSolana,
                )
            depositTransactionRepository.addTransaction(depositTx)
            navigator.route(
                Route.VerifyDeposit(vaultId = route.vaultId, transactionId = depositTx.id)
            )
            isSubmitting = false
        }
    }

    fun back() {
        viewModelScope.safeLaunch(onError = { Timber.w(it, "back failed") }) { navigator.back() }
    }
}

internal data class SolanaMoveStakeUiState(
    val stakePubkey: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
)
