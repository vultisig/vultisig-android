package com.vultisig.wallet.ui.models.referral

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

internal data class CreateReferralUiState(
    val searchStatus: SearchStatusType = SearchStatusType.DEFAULT,
    val yearExpiration: Int = 1,
    val formattedYearExpiration: String = "",
)

internal enum class SearchStatusType {
    DEFAULT,
    IS_SEARCHING,
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
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
) : ViewModel() {
    val searchReferralTexFieldState = TextFieldState()
    val state = MutableStateFlow(CreateReferralUiState())

    init {
        loadYearExpiration()
        observeReferralTextField()
    }

    private fun loadYearExpiration() {
        val formattedDate = getFormattedDateByAdding(1)

        state.update {
            it.copy(
                formattedYearExpiration = formattedDate,
            )
        }
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
                it.copy(searchStatus = SearchStatusType.IS_SEARCHING)
            }

            try {
                val exists = thorChainApi.existsReferralCode(referralCode)
                val status = if (exists) {
                    SearchStatusType.ERROR
                } else {
                    SearchStatusType.SUCCESS
                }
                state.update {
                    it.copy(searchStatus = status)
                }
            } catch (t: Throwable) {
                state.update {
                    it.copy(searchStatus = SearchStatusType.DEFAULT)
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
        viewModelScope.launch {
            searchReferralTexFieldState.clearText()
            state.update {
                it.copy(searchStatus = SearchStatusType.DEFAULT)
            }
        }
    }

    fun onCreateReferralCode() {

    }

    fun onAddExpirationYear() {
        viewModelScope.launch {
            val totalToAdd = state.value.yearExpiration + 1
            updateFormattedDate(totalToAdd)
        }
    }

    fun onSubtractExpirationYear() {
        viewModelScope.launch {
            val totalToAdd = state.value.yearExpiration - 1
            updateFormattedDate(totalToAdd)
        }
    }

    private fun updateFormattedDate(toAdd: Int) {
        val formattedDate = getFormattedDateByAdding(toAdd.toLong())
        state.update {
            it.copy(
                yearExpiration = toAdd,
                formattedYearExpiration = formattedDate,
            )
        }
    }

    private fun getFormattedDateByAdding(add: Long): String {
        val currentDate = LocalDate.now()
        val nextYearDate = currentDate.plusYears(add)
        val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
        return nextYearDate.format(formatter)
    }
}