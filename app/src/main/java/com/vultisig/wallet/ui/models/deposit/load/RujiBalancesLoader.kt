package com.vultisig.wallet.ui.models.deposit.load

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.MergeAccount
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.data.utils.toValue
import com.vultisig.wallet.ui.models.deposit.TokenMergeInfo
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber
import wallet.core.jni.CoinType

/**
 * Owns RUJI un-merge balance loading extracted from `DepositFormViewModel`: fetches the user's RUJI
 * merge balances for the selected un-merge token and reports the resolved shares balance back to
 * the ViewModel while toggling its loading flag.
 *
 * The API is Hilt-injected here; the ViewModel keeps `viewModelScope` ownership and supplies it
 * (assisted) along with the form-owned [tokenAmountFieldState], the [addressProvider] /
 * [selectedUnMergeCoinProvider] accessors and the [onSharesBalance] / [setLoading] callbacks so
 * this loader never owns its own scope or VM state.
 */
internal class RujiBalancesLoader
@AssistedInject
constructor(
    private val thorChainApi: ThorChainApi,
    @Assisted private val scope: CoroutineScope,
    @Assisted private val tokenAmountFieldState: TextFieldState,
    @Assisted private val addressProvider: () -> String?,
    @Assisted private val selectedUnMergeCoinProvider: () -> TokenMergeInfo,
    @Assisted private val onSharesBalance: (UiText) -> Unit,
    @Assisted private val setLoading: (Boolean) -> Unit,
) {

    /** @see RujiBalancesLoader */
    @AssistedFactory
    interface Factory {
        /**
         * Creates a [RujiBalancesLoader] bound to the given scope, field state, accessors and
         * callbacks.
         */
        fun create(
            scope: CoroutineScope,
            tokenAmountFieldState: TextFieldState,
            addressProvider: () -> String?,
            selectedUnMergeCoinProvider: () -> TokenMergeInfo,
            onSharesBalance: (UiText) -> Unit,
            setLoading: (Boolean) -> Unit,
        ): RujiBalancesLoader
    }

    private val rujiMergeBalances = MutableStateFlow<List<MergeAccount>?>(null)

    /** The latest fetched RUJI merge balances, or `null` if they have not been loaded yet. */
    val balances: List<MergeAccount>?
        get() = rujiMergeBalances.value

    /**
     * Fetches the RUJI merge balances for the current address and updates the shares balance field.
     */
    fun loadRujiMergeBalances() {
        setLoading(true)
        scope.safeLaunch(
            onError = { t ->
                onSharesBalance(UiText.Empty)
                Timber.e(t, "Can't load Ruji Balances")
            }
        ) {
            try {
                val selectedToken = selectedUnMergeCoinProvider()
                val addressString =
                    requireNotNull(addressProvider()) { "Invalid address: cannot fetch balance" }

                withContext(Dispatchers.IO) {
                    val newBalances = thorChainApi.getRujiMergeBalances(addressString)
                    rujiMergeBalances.update { newBalances }
                }

                setUnMergeTokenSharesField(selectedToken)
            } finally {
                setLoading(false)
            }
        }
    }

    /**
     * Populates the shares balance and amount field for the given [selectedToken] from cached
     * balances.
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

        onSharesBalance(amountText.asUiText())

        tokenAmountFieldState.setTextAndPlaceCursorAtEnd(amountText)
    }
}
