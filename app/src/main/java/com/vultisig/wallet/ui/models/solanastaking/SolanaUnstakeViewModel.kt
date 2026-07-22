package com.vultisig.wallet.ui.models.solanastaking

import androidx.compose.runtime.Immutable
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
 * View-model for the Solana "Unstake SOL" confirmation screen. Deactivating a stake account is
 * irreversible-until-cooldown, so the user confirms on a read-only screen (source account +
 * cooldown notice) before the deactivate transaction is built. Continue builds the deactivate tx
 * and routes to verify. Mirrors Windows `SolanaUnstakeSpecific`.
 */
@HiltViewModel
internal class SolanaUnstakeViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val buildKeysignPayload: BuildSolanaStakingKeysignPayloadUseCase,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Route.SolanaUnstake>()

    private val _state =
        MutableStateFlow(SolanaUnstakeUiState(stakePubkey = shortAddress(route.stakePubkey)))
    val state: StateFlow<SolanaUnstakeUiState> = _state.asStateFlow()

    fun onContinue() {
        if (_state.value.isSubmitting) return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to build Solana unstake tx")
                _state.update {
                    it.copy(isSubmitting = false, error = (e.message ?: "").asUiText())
                }
            }
        ) {
            val vault = vaultRepository.get(route.vaultId) ?: error("Vault not found")
            val solCoin =
                vault.coins.firstOrNull { it.chain == Chain.Solana && it.isNativeToken }
                    ?: error("SOL not in this vault")

            val payload = SolanaStakingPayload.unstake(stakeAccount = route.stakePubkey)
            val gasFee = TokenValue(value = SolanaHelper.DefaultFeeInLamports, token = solCoin)
            val specific =
                blockChainSpecificRepository.getSpecific(
                    chain = Chain.Solana,
                    address = solCoin.address,
                    token = solCoin,
                    gasFee = gasFee,
                    isSwap = false,
                    isMaxAmountEnabled = false,
                    isDeposit = true,
                )
            val keysignPayload =
                buildKeysignPayload(
                    coin = solCoin,
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
                    srcToken = solCoin,
                    srcAddress = solCoin.address,
                    // Deactivate carries no transfer — the whole stake account cools down, nothing
                    // leaves the wallet — so the verify screen must show 0, not the delegated stake
                    // (which read as "You're sending 1 SOL"). Matches iOS
                    // UnstakeTransactionBuilder.
                    srcTokenValue = TokenValue(value = BigInteger.ZERO, token = solCoin),
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
        }
    }

    fun back() {
        viewModelScope.safeLaunch(onError = { Timber.w(it, "back failed") }) { navigator.back() }
    }

    private fun shortAddress(address: String): String =
        if (address.length > 12) "${address.take(6)}…${address.takeLast(4)}" else address
}

@Immutable
internal data class SolanaUnstakeUiState(
    val stakePubkey: String = "",
    val isSubmitting: Boolean = false,
    val error: UiText? = null,
)
