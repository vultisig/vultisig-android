package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.data.blockchain.tron.GetTronFrozenBalancesUseCase
import com.vultisig.wallet.data.blockchain.tron.TronFrozenBalanceState
import com.vultisig.wallet.data.blockchain.tron.TronResourceType
import com.vultisig.wallet.data.blockchain.tron.TronStakingOperation
import com.vultisig.wallet.data.blockchain.tron.tronStakingMemo
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import java.math.BigDecimal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

internal class TronStakingService(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<SendFormUiModel>,
    private val tronFrozenBalances: MutableStateFlow<TronFrozenBalanceState>,
    private val tokenAmountFieldState: TextFieldState,
    private val fiatAmountFieldState: TextFieldState,
    private val memoFieldState: TextFieldState,
    private val defiTypeProvider: () -> DeFiNavActions?,
    private val vaultProvider: () -> Vault?,
    private val vaultIdProvider: () -> VaultId?,
    private val vaultRepository: VaultRepository,
    private val getTronFrozenBalances: GetTronFrozenBalancesUseCase,
) {
    private var loadTronFrozenBalancesJob: Job? = null

    fun isStakingType(): Boolean {
        val defi = defiTypeProvider()
        return defi == DeFiNavActions.FREEZE_TRX || defi == DeFiNavActions.UNFREEZE_TRX
    }

    fun initIfStakingType() {
        if (!isStakingType()) return
        applyStakingMemo(TronResourceType.BANDWIDTH)
        if (defiTypeProvider() == DeFiNavActions.UNFREEZE_TRX) {
            loadFrozenBalances()
        }
    }

    fun setResourceType(type: TronResourceType) {
        // Ignore toggles while a send is in flight so the captured memo/resource type
        // used by send() can't diverge from the one visible in the tab.
        if (uiState.value.isLoading) return
        if (uiState.value.tronResourceType == type) return
        uiState.update {
            it.copy(
                tronResourceType = type,
                tronBalanceAvailableOverride = null,
                selectedAmountFraction = null,
            )
        }
        applyStakingMemo(type)
        tokenAmountFieldState.clearText()
        fiatAmountFieldState.clearText()
        if (defiTypeProvider() == DeFiNavActions.UNFREEZE_TRX) {
            updateFrozenBalanceDisplay(type)
        }
    }

    fun currentFrozenBalance(): BigDecimal? {
        val type = uiState.value.tronResourceType ?: return null
        val balances =
            (tronFrozenBalances.value as? TronFrozenBalanceState.Loaded)?.balances ?: return null
        return balances.forResource(type)
    }

    private fun applyStakingMemo(type: TronResourceType) {
        val op =
            when (defiTypeProvider()) {
                DeFiNavActions.FREEZE_TRX -> TronStakingOperation.FREEZE
                DeFiNavActions.UNFREEZE_TRX -> TronStakingOperation.UNFREEZE
                else -> error("applyStakingMemo called outside Tron staking")
            }
        memoFieldState.setTextAndPlaceCursorAtEnd(tronStakingMemo(op, type))
    }

    private fun loadFrozenBalances() {
        loadTronFrozenBalancesJob?.cancel()
        loadTronFrozenBalancesJob =
            scope.safeLaunch(
                onError = { e ->
                    Timber.e(e, "Failed to load Tron frozen balances")
                    setFrozenBalanceState(TronFrozenBalanceState.Error)
                }
            ) {
                uiState.update { it.copy(isTronFrozenBalancesLoading = true) }
                try {
                    val vault =
                        vaultProvider()
                            ?: vaultRepository.get(vaultIdProvider() ?: return@safeLaunch)
                    val trxCoin =
                        vault?.coins?.firstOrNull { it.chain == Chain.Tron && it.isNativeToken }
                    if (trxCoin == null) {
                        setFrozenBalanceState(TronFrozenBalanceState.Error)
                        return@safeLaunch
                    }
                    val balances = getTronFrozenBalances(trxCoin.address)
                    setFrozenBalanceState(TronFrozenBalanceState.Loaded(balances))
                    updateFrozenBalanceDisplay(
                        uiState.value.tronResourceType ?: TronResourceType.BANDWIDTH
                    )
                } finally {
                    uiState.update { it.copy(isTronFrozenBalancesLoading = false) }
                }
            }
    }

    private fun setFrozenBalanceState(state: TronFrozenBalanceState) {
        tronFrozenBalances.value = state
        uiState.update {
            it.copy(hasTronFrozenBalancesError = state is TronFrozenBalanceState.Error)
        }
    }

    private fun updateFrozenBalanceDisplay(type: TronResourceType) {
        val balances =
            (tronFrozenBalances.value as? TronFrozenBalanceState.Loaded)?.balances ?: return
        uiState.update {
            it.copy(
                tronBalanceAvailableOverride =
                    balances.forResource(type).stripTrailingZeros().toPlainString()
            )
        }
    }
}
