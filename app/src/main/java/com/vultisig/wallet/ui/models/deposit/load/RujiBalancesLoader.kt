package com.vultisig.wallet.ui.models.deposit.load

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.MergeAccount
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.utils.toValue
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.deposit.TokenMergeInfo
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import wallet.core.jni.CoinType

/**
 * Owns RUJI un-merge balance loading extracted from `DepositFormViewModel`: fetches the user's
 * merge-pool shares for the current address and populates the un-merge "shares" amount field /
 * [DepositFormUiModel.sharesBalance] for the selected merge token.
 *
 * The API is Hilt-injected here; the ViewModel keeps `viewModelScope` ownership and supplies it
 * (assisted) along with the form-owned [state] / [address] flows, the shared [rujiMergeBalances]
 * cache and the [tokenAmountFieldState] so this loader never owns its own scope or VM state.
 */
internal class RujiBalancesLoader
@AssistedInject
constructor(
    private val thorChainApi: ThorChainApi,
    @Assisted private val scope: CoroutineScope,
    @Assisted private val state: MutableStateFlow<DepositFormUiModel>,
    @Assisted private val address: StateFlow<Address?>,
    @Assisted private val rujiMergeBalances: MutableStateFlow<List<MergeAccount>?>,
    @Assisted private val tokenAmountFieldState: TextFieldState,
) {

    /** @see RujiBalancesLoader */
    @AssistedFactory
    interface Factory {
        /** Creates a [RujiBalancesLoader] bound to the given scope, state flows and field state. */
        fun create(
            scope: CoroutineScope,
            state: MutableStateFlow<DepositFormUiModel>,
            address: StateFlow<Address?>,
            rujiMergeBalances: MutableStateFlow<List<MergeAccount>?>,
            tokenAmountFieldState: TextFieldState,
        ): RujiBalancesLoader
    }

    private var rujiMergeBalancesJob: Job? = null

    /**
     * Loads the RUJI merge-pool balances for the current address into [rujiMergeBalances] and fills
     * the un-merge shares field for the currently-selected un-merge token. Clears
     * [DepositFormUiModel.sharesBalance] on failure so a stale value can't survive a failed load.
     */
    fun onLoadRujiMergeBalances() {
        rujiMergeBalancesJob?.cancel()
        rujiMergeBalancesJob =
            scope.launch {
                try {
                    val selectedToken = state.value.selectedUnMergeCoin
                    val addressString =
                        address.value?.address
                            ?: throw RuntimeException("Invalid address: cannot fetch balance")

                    withContext(Dispatchers.IO) {
                        val newBalances = thorChainApi.getRujiMergeBalances(addressString)
                        rujiMergeBalances.update { newBalances }
                    }

                    setUnMergeTokenSharesField(selectedToken)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    state.update { it.copy(sharesBalance = UiText.Empty) }
                    Timber.e("Can't load Ruji Balances ${t.message}")
                } finally {
                    state.update { it.copy(isLoading = false) }
                }
            }
    }

    /**
     * Populates [tokenAmountFieldState] and [DepositFormUiModel.sharesBalance] with the available
     * shares for [selectedToken] from the already-loaded [rujiMergeBalances]; no-op if the token
     * has no matching merge account.
     */
    fun setUnMergeTokenSharesField(selectedToken: TokenMergeInfo) {
        val selectedSymbol = selectedToken.ticker
        val selectedMergeAccount =
            rujiMergeBalances.value?.firstOrNull {
                it.pool?.mergeAsset?.metadata?.symbol.equals(selectedSymbol, true)
            } ?: return

        val amountText =
            selectedMergeAccount.shares?.toBigInteger()?.let {
                CoinType.THORCHAIN.toValue(it).toString()
            } ?: "0"

        state.update { it.copy(sharesBalance = amountText.asUiText()) }

        tokenAmountFieldState.setTextAndPlaceCursorAtEnd(amountText)
    }
}
