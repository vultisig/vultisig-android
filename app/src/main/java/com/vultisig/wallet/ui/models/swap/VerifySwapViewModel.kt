package com.vultisig.wallet.ui.models.swap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.ui.models.keysign.KeysignInitType
import com.vultisig.wallet.ui.models.mappers.SwapTransactionToUiModelMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.util.LaunchKeysignUseCase
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class SwapTransactionUiModel(
    val srcTokenValue: String = "",
    val srcToken: Coin = Coins.wewe,
    val dstTokenValue: String = "",
    val dstToken: Coin = Coins.wewe,
    val totalFee: String = "",
    val hasConsentAllowance: Boolean = false,
)

internal data class VerifySwapUiModel(
    val swapTransactionUiModel: SwapTransactionUiModel = SwapTransactionUiModel(),
    val provider: UiText = UiText.Empty,
    val consentAmount: Boolean = false,
    val consentReceiveAmount: Boolean = false,
    val consentAllowance: Boolean = false,
    val errorText: UiText? = null,
    val hasFastSign: Boolean = false,
) {
    val hasAllConsents: Boolean
        get() = consentAmount && consentReceiveAmount && (consentAllowance || !swapTransactionUiModel.hasConsentAllowance)
}

@HiltViewModel
internal class VerifySwapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val mapTransactionToUiModel: SwapTransactionToUiModelMapper,

    private val swapTransactionRepository: SwapTransactionRepository,
    private val vaultPasswordRepository: VaultPasswordRepository,
    private val launchKeysign: LaunchKeysignUseCase,
    private val isVaultHasFastSignById: IsVaultHasFastSignByIdUseCase,
) : ViewModel() {

    val state = MutableStateFlow(VerifySwapUiModel())
    private val password = MutableStateFlow<String?>(null)

    private val args = savedStateHandle.toRoute<Route.VerifySwap>()

    private val vaultId: VaultId = args.vaultId
    private val transactionId: String = args.transactionId

    init {
        viewModelScope.launch {
            val transaction = swapTransactionRepository.getTransaction(transactionId)
            val providerText = when (transaction.payload) {
                is SwapPayload.OneInch -> R.string.swap_for_provider_1inch.asUiText()
                is SwapPayload.ThorChain -> R.string.swap_form_provider_thorchain.asUiText()
                is SwapPayload.MayaChain -> R.string.swap_form_provider_mayachain.asUiText()
            }
            val consentAllowance = !transaction.isApprovalRequired
            state.update {
                it.copy(
                    provider = providerText,
                    consentAllowance = consentAllowance,
                    swapTransactionUiModel = mapTransactionToUiModel(transaction)
                )
            }
        }
        loadFastSign()
        loadPassword()
    }

    fun back() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }

    fun consentReceiveAmount(consent: Boolean) {
        state.update { it.copy(consentReceiveAmount = consent) }
    }

    fun consentAmount(consent: Boolean) {
        state.update { it.copy(consentAmount = consent) }
    }

    fun consentAllowance(consent: Boolean) {
        state.update { it.copy(consentAllowance = consent) }
    }

    fun dismissError() {
        state.update { it.copy(errorText = null) }
    }

    fun confirm() {
        keysign(KeysignInitType.QR_CODE)
    }

    fun authFastSign() {
        keysign(KeysignInitType.BIOMETRY)
    }

    fun tryToFastSignWithPassword(): Boolean {
        if (password.value != null) {
            return false
        } else {
            keysign(KeysignInitType.PASSWORD)
            return true
        }
    }

    private fun keysign(
        keysignInitType: KeysignInitType,
    ) {
        val hasAllConsents = state.value.let {
            it.consentReceiveAmount && it.consentAmount && it.consentAllowance
        }

        if (hasAllConsents) {
            viewModelScope.launch {
                launchKeysign(keysignInitType, transactionId, password.value,
                    Route.Keysign.Keysign.TxType.Swap, vaultId)
            }
        } else {
            state.update {
                it.copy(
                    errorText = UiText.StringResource(
                        R.string.verify_transaction_error_not_enough_consent
                    )
                )
            }
        }
    }

    private fun loadPassword() {
        viewModelScope.launch {
            password.value = vaultPasswordRepository.getPassword(vaultId)
        }
    }

    private fun loadFastSign() {
        viewModelScope.launch {
            val hasFastSign = isVaultHasFastSignById(vaultId)
            state.update {
                it.copy(
                    hasFastSign = hasFastSign
                )
            }
        }
    }
}