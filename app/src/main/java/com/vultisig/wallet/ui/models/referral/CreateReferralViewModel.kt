package com.vultisig.wallet.ui.models.referral

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class CreateReferralUiState(
    val isLoading: Boolean = false,
    val searchStatus: SearchStatusType = SearchStatusType.DEFAULT,
)

internal enum class SearchStatusType {
    DEFAULT,
    VALIDATION_ERROR,
    SUCCESS,
    ERROR,
}

internal fun SearchStatusType.isError(): Boolean {
    return this == SearchStatusType.VALIDATION_ERROR || this == SearchStatusType.ERROR
}

@HiltViewModel
internal class CreateReferralViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val thorChainApi: ThorChainApi,
) : ViewModel() {
    val searchReferralTexFieldState = TextFieldState()
    val state = MutableStateFlow(CreateReferralUiState())

    init {
        observeReferralTextField()
    }

    fun onSearchReferralCode() {
        viewModelScope.launch {
            val referralCode = searchReferralTexFieldState.text.toString().trim()
            validateReferralCode(referralCode)?.let { _ ->
                state.update {
                    it.copy(searchStatus = SearchStatusType.VALIDATION_ERROR)
                }
                return@launch
            }

            state.update {
                it.copy(isLoading = true)
            }

            try {
                val exists = thorChainApi.existsReferralCode(referralCode)
                val status = if (exists) {
                    SearchStatusType.ERROR
                } else {
                    SearchStatusType.SUCCESS
                }
                state.update {
                    it.copy(searchStatus = status, isLoading = false)
                }
            } catch (t: Throwable) {
                state.update {
                    it.copy(isLoading = false)
                }
            }
        }
    }

    private fun observeReferralTextField() {
        viewModelScope.launch {
            searchReferralTexFieldState
                .textAsFlow()
                .onEach {
                    if (state.value.searchStatus != SearchStatusType.DEFAULT) {
                        state.update {
                            it.copy(searchStatus = SearchStatusType.DEFAULT)
                        }
                    }
                }
                .collect {}
        }
    }

    fun onCleanReferralClick() {
        searchReferralTexFieldState.clearText()
        state.update {
            it.copy(searchStatus = SearchStatusType.DEFAULT)
        }
    }

    fun onCreateReferralCode() {

    }

    fun onAddExpirationYear() {

    }

    fun onSubtractExpirationYear() {

    }
}