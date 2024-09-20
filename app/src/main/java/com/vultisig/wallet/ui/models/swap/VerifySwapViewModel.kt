package com.vultisig.wallet.ui.models.swap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.navigation.SendDst.Companion.ARG_TRANSACTION_ID
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
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
    val consentAmount: Boolean = false,
    val consentReceiveAmount: Boolean = false,
    val hasConsentAllowance: Boolean = false,
    val consentAllowance: Boolean = false,
    val errorText: UiText? = null,
    val hasFastSign: Boolean = false,
)

@HiltViewModel
internal class VerifySwapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sendNavigator: Navigator<SendDst>,
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase,

    private val swapTransactionRepository: SwapTransactionRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val vaultRepository: VaultRepository,
    private val vultiSignerRepository: VultiSignerRepository,
) : ViewModel() {

    val state = MutableStateFlow(VerifySwapUiModel())

    private val transactionId: String = requireNotNull(savedStateHandle[ARG_TRANSACTION_ID])
    private val vaultId: String? = savedStateHandle["vault_id"]

    init {
        viewModelScope.launch {
            val currency = appCurrencyRepository.currency.first()
            val transaction = swapTransactionRepository.getTransaction(transactionId)

            val fiatFees = convertTokenValueToFiat( //FiatValue(value=14.80373801065170, currency=USD)
                transaction.dstToken,//Coin(chain=Ethereum, ticker=THOR, logo=https://tokens.1inch.io/0xa5f2211b9b8170f694421f2046281775e8468044.png, address=0xC8c8CA8902f58f9b751C3a5Dfa677D724416a886, decimal=18, hexPublicKey=03591449887e576bbaeabf5248b8a7de3d264f792a0d64c49fe0973b1a479c1e06, priceProviderID=, contractAddress=0xa5f2211b9b8170f694421f2046281775e8468044, isNativeToken=false)
                transaction.estimatedFees, currency //TokenValue(value=50643274050000000000, unit=THOR, decimals=18)
            )

            val providerText = when (transaction.payload) {
                is SwapPayload.OneInch -> R.string.swap_for_provider_1inch.asUiText()
                is SwapPayload.ThorChain -> R.string.swap_form_provider_thorchain.asUiText()
                is SwapPayload.MayaChain -> R.string.swap_form_provider_mayachain.asUiText()
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
                )
            }
        }
        loadFastSign()
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

    fun fastSign() {
        keysign(true)
    }

    fun confirm() {
        keysign(false)
    }

    private fun keysign(
        hasFastSign: Boolean,
    ) {
        val hasAllConsents = state.value.let {
            it.consentReceiveAmount && it.consentAmount && it.consentAllowance
        }

        if (hasAllConsents) {
            viewModelScope.launch {
                if (hasFastSign) {
                    sendNavigator.navigate(
                        SendDst.Password(
                            transactionId = transactionId,
                        )
                    )
                } else {
                    sendNavigator.navigate(
                        SendDst.Keysign(
                            transactionId = transactionId,
                            password = null,
                        )
                    )
                }
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

    private fun loadFastSign() {
        viewModelScope.launch {
            if (vaultId == null) return@launch
            val vault = requireNotNull(vaultRepository.get(vaultId))
            val hasFastSign = vultiSignerRepository.hasFastSign(vault.pubKeyECDSA)
            state.update {
                it.copy(
                    hasFastSign = hasFastSign
                )
            }
        }
    }

}