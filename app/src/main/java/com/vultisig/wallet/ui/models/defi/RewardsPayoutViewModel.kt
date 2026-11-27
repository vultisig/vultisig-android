package com.vultisig.wallet.ui.models.defi

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.payoutBps
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.deposit.DepositGasFeeHelper
import com.vultisig.wallet.ui.models.deposit.submit.buildBondYieldTransaction
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val BPS_PER_PERCENT = 100

@Immutable
internal data class RewardsPayoutUiState(
    val percentage: Int = 0,
    /** The provider's on-chain Bond Yield share in basis points; null until read, and after a failed read. */
    val currentBps: Int? = null,
    val loadFailed: Boolean = false,
    val submitFailed: Boolean = false,
    val isSubmitting: Boolean = false,
) {
    val canContinue: Boolean
        get() = currentBps != null && !isSubmitting && percentage * BPS_PER_PERCENT != currentBps
}

/**
 * Rewards Payout sheet for one bonded THORChain node: reads the vault's current Bond Yield share
 * live, then builds the BONDYIELD deposit and routes to the existing verify screen.
 */
@HiltViewModel
internal class RewardsPayoutViewModel
@Inject
constructor(
    private val vaultRepository: VaultRepository,
    private val thorChainApi: ThorChainApi,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val depositGasFeeHelper: DepositGasFeeHelper,
    private val transactionRepository: DepositTransactionRepository,
    private val navigator: Navigator<Destination>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val state: StateFlow<RewardsPayoutUiState>
        field = MutableStateFlow(RewardsPayoutUiState())

    private var vaultId: String? = null
    private var nodeAddress: String? = null
    private var rune: Coin? = null
    private var loadJob: Job? = null
    private var submitJob: Job? = null

    fun load(vaultId: String, nodeAddress: String) {
        this.vaultId = vaultId
        this.nodeAddress = nodeAddress
        rune = null
        loadJob?.cancel()
        state.value = RewardsPayoutUiState()
        loadJob =
            viewModelScope.safeLaunch(
                onError = { e ->
                    Timber.e(e, "Failed to read the Bond Yield share")
                    state.update { it.copy(loadFailed = true) }
                }
            ) {
                val vault =
                    withContext(ioDispatcher) { vaultRepository.get(vaultId) }
                        ?: error("Vault not found")
                val nativeRune =
                    vault.coins.first { it.chain == Chain.ThorChain && it.isNativeToken }
                val details = withContext(ioDispatcher) { thorChainApi.getNodeDetails(nodeAddress) }
                val bps =
                    details.payoutBps(nativeRune.address) ?: error("Vault is not a bond provider")
                rune = nativeRune
                state.update { it.copy(currentBps = bps, percentage = bps / BPS_PER_PERCENT) }
            }
    }

    fun retry() {
        load(vaultId ?: return, nodeAddress ?: return)
    }

    fun setPercentage(percentage: Int) {
        state.update { it.copy(percentage = percentage.coerceIn(0, 100), submitFailed = false) }
    }

    fun submit(onSubmitted: () -> Unit) {
        val vaultId = vaultId ?: return
        val nodeAddress = nodeAddress ?: return
        val rune = rune ?: return
        if (!state.value.canContinue) return
        state.update { it.copy(isSubmitting = true, submitFailed = false) }

        submitJob =
            viewModelScope.safeLaunch(
                onError = { e ->
                    Timber.e(e, "Failed to build the Rewards Payout transaction")
                    state.update { it.copy(isSubmitting = false, submitFailed = true) }
                }
            ) {
                val transaction =
                    buildBondYieldTransaction(
                        vaultId = vaultId,
                        rune = rune,
                        nodeAddress = nodeAddress,
                        payoutPercentage = state.value.percentage,
                        blockChainSpecificRepository = blockChainSpecificRepository,
                        depositGasFeeHelper = depositGasFeeHelper,
                    )
                transactionRepository.addTransaction(transaction)
                state.update { it.copy(isSubmitting = false) }
                navigator.route(
                    Route.VerifyDeposit(vaultId = vaultId, transactionId = transaction.id)
                )
                onSubmitted()
            }
    }

    /** Stops in-flight work and clears state, so a closed sheet never opens verify or reopens stale. */
    fun cancel() {
        loadJob?.cancel()
        submitJob?.cancel()
        state.value = RewardsPayoutUiState()
    }
}
