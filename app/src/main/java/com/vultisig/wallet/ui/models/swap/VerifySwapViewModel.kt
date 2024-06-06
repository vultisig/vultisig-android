package com.vultisig.wallet.ui.models.swap

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject


data class VerifySwapUiModel(
    val srcTokenValue: String = "",
    val dstTokenValue: String = "",
    val estimatedFees: String = "",
    val estimatedTime: String = "",
    val consentAmount: Boolean = false,
    val consentReceiveAmount: Boolean = false,
)

@HiltViewModel
internal class VerifySwapViewModel @Inject constructor() : ViewModel() {

    val state = MutableStateFlow(VerifySwapUiModel())

    fun consentReceiveAmount(consent: Boolean) {
        state.update { it.copy(consentReceiveAmount = consent) }
    }

    fun consentAmount(consent: Boolean) {
        state.update { it.copy(consentAmount = consent) }
    }

    fun confirm() {
        // TODO start or join keysign

    }

}