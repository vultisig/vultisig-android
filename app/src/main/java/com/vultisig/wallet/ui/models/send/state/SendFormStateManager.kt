package com.vultisig.wallet.ui.models.send.state

import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.ui.models.send.GasSettings
import com.vultisig.wallet.ui.models.send.SendFormUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import wallet.core.jni.proto.Bitcoin

/**
 * Manages all [MutableStateFlow] state for [com.vultisig.wallet.ui.models.send.SendFormViewModel].
 */
internal class SendFormStateManager(
    appCurrencyRepository: AppCurrencyRepository,
    scope: CoroutineScope,
) {
    /** Current UI state of the send form. */
    val uiState = MutableStateFlow(SendFormUiModel())

    /** The currently selected token for the transaction. */
    val selectedToken = MutableStateFlow<Coin?>(null)

    /** Accounts available for the selected vault. */
    val accounts = MutableStateFlow(emptyList<Account>())

    /** Bitcoin transaction plan fee in satoshis. */
    val planFee = MutableStateFlow<Long?>(null)

    /** Bitcoin transaction plan. */
    val planBtc = MutableStateFlow<Bitcoin.TransactionPlan?>(null)

    /** Estimated gas fee for the transaction. */
    val gasFee = MutableStateFlow<TokenValue?>(null)

    /** Resolved destination address after name resolution. */
    val resolvedDstAddress = MutableStateFlow<String?>(null)

    /** Human-readable label for the destination address (e.g. ENS name). */
    val dstAddressLabel = MutableStateFlow<String?>(null)

    /** Advanced gas settings override. */
    val gasSettings = MutableStateFlow<GasSettings?>(null)

    /** Chain-specific payload data. */
    val specific = MutableStateFlow<BlockChainSpecificAndUtxo?>(null)

    /** Whether the user has selected the max available amount. */
    val isMaxAmount = MutableStateFlow(false)

    /** Whether accounts are currently being switched. */
    val isSwitchingAccounts = MutableStateFlow(false)

    /** Active app currency, updated reactively from [AppCurrencyRepository]. */
    val appCurrency =
        appCurrencyRepository.currency.stateIn(
            scope,
            SharingStarted.WhileSubscribed(),
            appCurrencyRepository.defaultCurrency,
        )
}
