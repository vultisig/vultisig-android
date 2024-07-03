package com.vultisig.wallet.ui.models.swap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.common.asUiText
import com.vultisig.wallet.data.models.SwapPayload
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.ui.models.mappers.DurationToUiStringMapper
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.navigation.SendDst.Companion.ARG_TRANSACTION_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


internal data class VerifySwapUiModel(
    val provider: UiText = UiText.Empty,
    val srcTokenValue: String = "",
    val dstTokenValue: String = "",
    val estimatedFees: String = "",
    val estimatedTime: UiText = UiText.Empty,
    val consentAmount: Boolean = false,
    val consentReceiveAmount: Boolean = false,
    val hasConsentAllowance: Boolean = false,
    val consentAllowance: Boolean = false,
    val errorText: UiText? = null,
)

@HiltViewModel
internal class VerifySwapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sendNavigator: Navigator<SendDst>,
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val mapDurationToUiString: DurationToUiStringMapper,

    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase,
    private val swapTransactionRepository: SwapTransactionRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
) : ViewModel() {

    val state = MutableStateFlow(VerifySwapUiModel())

    private val transactionId: String = requireNotNull(savedStateHandle[ARG_TRANSACTION_ID])

    init {
        viewModelScope.launch {
            val currency = appCurrencyRepository.currency.first()
            val transaction = swapTransactionRepository.getTransaction(transactionId)

            val fiatFees = convertTokenValueToFiat(
                transaction.dstToken,
                transaction.estimatedFees, currency
            )

            val estimatedTime = transaction.estimatedTime?.let {
                UiText.DynamicString(mapDurationToUiString(it))
            } ?: R.string.swap_screen_estimated_time_instant.asUiText()

            val providerText = when (transaction.payload) {
                is SwapPayload.OneInch -> R.string.swap_for_provider_1inch.asUiText()
                is SwapPayload.ThorChain -> R.string.swap_form_provider_thorchain.asUiText()
            }

            val consentAllowance = !transaction.isApprovalRequired

            state.update {
                it.copy(
                    provider = providerText,
                    srcTokenValue = mapTokenValueToStringWithUnit(transaction.srcTokenValue),
                    dstTokenValue = mapTokenValueToStringWithUnit(transaction.expectedDstTokenValue),
                    hasConsentAllowance = transaction.isApprovalRequired,
                    consentAllowance = consentAllowance,
                    estimatedFees = fiatValueToStringMapper.map(fiatFees),
                    estimatedTime = estimatedTime,
                )
            }
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
        val hasAllConsents = state.value.let {
            it.consentReceiveAmount && it.consentAmount && it.consentAllowance
        }

        if (hasAllConsents) {
            viewModelScope.launch {
                sendNavigator.navigate(
                    SendDst.Keysign(
                        transactionId = transactionId,
                    )
                )
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

}